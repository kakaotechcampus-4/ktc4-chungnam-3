package com.ktc.chungnam3.remembrall.recall.tool;

import com.ktc.chungnam3.remembrall.content.embedding.EmbeddingClient;
import com.ktc.chungnam3.remembrall.recall.config.RecallCandidateProperties;
import com.ktc.chungnam3.remembrall.recall.config.RecallToolProperties;
import com.ktc.chungnam3.remembrall.recall.tool.RecallToolRepository.ContentRow;
import com.ktc.chungnam3.remembrall.recall.tool.RecallToolRepository.SearchPlaceRow;
import com.ktc.chungnam3.remembrall.recall.tool.RecallToolResults.*;
import com.ktc.chungnam3.remembrall.recall.weather.WeatherClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class RecallToolService {
    private static final Logger log = LoggerFactory.getLogger(RecallToolService.class);
    private static final String NOT_FOUND = "대상을 찾을 수 없습니다.";
    private final EmbeddingClient embeddings;
    private final RecallToolRepository repository;
    private final RecallCandidateProperties candidates;
    private final RecallToolProperties properties;
    private final WeatherClient weather;
    private final Clock clock;
    private final TransactionTemplate reads;

    public RecallToolService(EmbeddingClient embeddings, RecallToolRepository repository,
                             RecallCandidateProperties candidates, RecallToolProperties properties,
                             PlatformTransactionManager transactionManager, WeatherClient weather, Clock clock) {
        this.embeddings = embeddings;
        this.repository = repository;
        this.candidates = candidates;
        this.properties = properties;
        this.weather = weather;
        this.clock = clock;
        this.reads = new TransactionTemplate(transactionManager);
        this.reads.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.reads.setReadOnly(true);
    }

    public SearchResult searchCandidates(RecallToolScope scope, String condition, Double radiusMeters) {
        return searchCandidates(scope, condition, radiusMeters, SearchSource.ALL, Set.of(), Set.of());
    }

    public SearchResult searchCandidates(RecallToolScope scope, String condition, Double radiusMeters,
                                         SearchSource source, Set<UUID> seenContents, Set<UUID> seenPlaces) {
        if (!validScope(scope)) return new SearchResult(List.of(), List.of(), "실행 범위를 확인할 수 없습니다.");
        if (condition == null || condition.isBlank()) return new SearchResult(List.of(), List.of(), "검색 조건이 필요합니다.");
        if (radiusMeters != null && (!Double.isFinite(radiusMeters) || radiusMeters <= 0)) {
            return new SearchResult(List.of(), List.of(), "반경은 양수여야 합니다.");
        }
        double radius = Math.min(radiusMeters == null ? candidates.radiusMeters() : radiusMeters,
                properties.maxRadiusMeters());
        String vector;
        try {
            vector = vectorLiteral(embeddings.embedQuery(condition.strip()));
        } catch (Exception exception) {
            log.warn("Recall search embedding failed: {}", exception.getClass().getSimpleName());
            return new SearchResult(List.of(), List.of(), "검색 임베딩을 만들 수 없습니다.");
        }
        try {
            return reads.execute(status -> {
                var rows = repository.searchPlaces(scope, vector, radius, properties.searchPlaceLimit(),
                        source == null ? SearchSource.ALL : source, seenContents, seenPlaces);
                var placesById = new LinkedHashMap<UUID, SearchPlaceRow>();
                var contentsByPlace = new LinkedHashMap<UUID, List<ContentCandidate>>();
                rows.forEach(row -> {
                    placesById.putIfAbsent(row.placeId(), row);
                    contentsByPlace.computeIfAbsent(row.placeId(), key -> new ArrayList<>()).add(candidate(row.content()));
                });
                var places = placesById.values().stream().map(row -> new PlaceCandidate(row.placeId(), row.name(),
                        row.distanceMeters(), List.copyOf(contentsByPlace.get(row.placeId())))).toList();
                var contents = repository.searchContents(scope.memberId(), vector, properties.searchContentLimit(),
                        source == null ? SearchSource.ALL : source, seenContents).stream().map(this::candidate).toList();
                return new SearchResult(places, contents, null);
            });
        } catch (DataAccessException exception) {
            log.warn("Recall candidate query failed", exception);
            return new SearchResult(List.of(), List.of(), "후보를 조회할 수 없습니다.");
        }
    }

    public VisitConditionsResult checkVisitConditions(RecallToolScope scope, List<UUID> placeIds) {
        String error = validateIds(scope, placeIds);
        if (error != null) return new VisitConditionsResult(List.of(), null, List.of(), error);
        if (placeIds.isEmpty()) return new VisitConditionsResult(List.of(), null, List.of(), null);
        var ids = List.copyOf(new LinkedHashSet<>(placeIds));
        var now = clock.instant();
        var since = now.minus(properties.recentProposalWindow());
        VisitConditionsResult stored;
        try {
            stored = reads.execute(status -> {
                var conditions = new LinkedHashMap<UUID, List<OperatingCondition>>();
                var names = new LinkedHashMap<UUID, String>();
                repository.findVisitConditions(scope.memberId(), ids).forEach(row -> {
                    names.putIfAbsent(row.placeId(), row.name());
                    conditions.computeIfAbsent(row.placeId(), ignored -> new ArrayList<>()).add(
                            new OperatingCondition(row.content().contentId(), row.content().source(), row.content().title(),
                                    summarize(row.content().summary(), properties.visitSummaryLength()),
                                    summarize(row.description(), properties.visitSummaryLength()), row.referenceAt(),
                                    row.referenceTimeBasis()));
                });
                var recent = new LinkedHashMap<UUID, List<RecentProposal>>();
                repository.findRecentProposals(scope.memberId(), ids, since, now).forEach(row ->
                        recent.computeIfAbsent(row.placeId(), ignored -> new ArrayList<>())
                                .add(new RecentProposal(row.sentAt(), row.openedAt() != null, row.openedAt())));
                var places = new ArrayList<PlaceConditions>();
                var errors = new ArrayList<TargetError>();
                for (UUID id : ids) {
                    if (!conditions.containsKey(id)) {
                        errors.add(new TargetError(id, NOT_FOUND));
                    } else {
                        var notifications = List.copyOf(recent.getOrDefault(id, List.of()));
                        places.add(new PlaceConditions(id, names.get(id), List.copyOf(conditions.get(id)),
                                new RecentProposals("NOTIFICATION_HISTORY", now, since,
                                        !notifications.isEmpty(), notifications)));
                    }
                }
                return new VisitConditionsResult(List.copyOf(places), null, List.copyOf(errors), null);
            });
        } catch (DataAccessException exception) {
            log.warn("Recall visit condition query failed", exception);
            return new VisitConditionsResult(List.of(), null, List.of(), "방문 조건을 조회할 수 없습니다.");
        }
        WeatherClient.Weather forecast;
        try {
            forecast = weather.read(scope.latitude(), scope.longitude(), now);
            if (forecast == null) forecast = WeatherClient.Weather.unavailable(now);
        } catch (Exception exception) {
            log.warn("Recall weather lookup failed: {}", exception.getClass().getSimpleName());
            forecast = WeatherClient.Weather.unavailable(now);
        }
        return new VisitConditionsResult(stored.places(), forecast, stored.errors(), stored.error());
    }

    private String validateIds(RecallToolScope scope, List<UUID> ids) {
        if (!validScope(scope)) return "실행 범위를 확인할 수 없습니다.";
        if (ids == null || ids.stream().anyMatch(java.util.Objects::isNull)) return "유효한 ID 목록이 필요합니다.";
        return ids.size() > properties.maxVisitPlaces() ? "요청 개수가 최대 " + properties.maxVisitPlaces() + "개를 초과했습니다." : null;
    }

    private boolean validScope(RecallToolScope scope) {
        return scope != null && scope.memberId() != null && scope.triggerPlaceId() != null
                && Double.isFinite(scope.latitude()) && Double.isFinite(scope.longitude())
                && Math.abs(scope.latitude()) <= 90 && Math.abs(scope.longitude()) <= 180;
    }

    private ContentCandidate candidate(ContentRow row) {
        return new ContentCandidate(row.contentId(), row.source(), row.title(), row.category(),
                summarize(row.summary(), candidates.maxSummaryLength()), row.savedAt());
    }

    private String summarize(String summary, int maximum) {
        return summary == null || summary.codePointCount(0, summary.length()) <= maximum
                ? summary : summary.substring(0, summary.offsetByCodePoints(0, maximum));
    }

    private String vectorLiteral(EmbeddingClient.Result result) {
        if (result == null || result.vector() == null || result.vector().length != 768) {
            throw new IllegalStateException("Invalid embedding dimensions");
        }
        var vector = new StringBuilder("[");
        double squaredLength = 0;
        for (float value : result.vector()) {
            if (!Float.isFinite(value)) throw new IllegalStateException("Invalid embedding value");
            if (vector.length() > 1) vector.append(',');
            vector.append(value);
            squaredLength += (double) value * value;
        }
        if (squaredLength == 0) throw new IllegalStateException("Empty embedding vector");
        return vector.append(']').toString();
    }
}
