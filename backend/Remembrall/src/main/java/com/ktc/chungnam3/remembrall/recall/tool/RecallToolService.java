package com.ktc.chungnam3.remembrall.recall.tool;

import com.ktc.chungnam3.remembrall.content.embedding.EmbeddingClient;
import com.ktc.chungnam3.remembrall.recall.config.RecallCandidateProperties;
import com.ktc.chungnam3.remembrall.recall.config.RecallToolProperties;
import com.ktc.chungnam3.remembrall.recall.service.WalkingTimeCalculator;
import com.ktc.chungnam3.remembrall.recall.tool.RecallToolRepository.ContentRow;
import com.ktc.chungnam3.remembrall.recall.tool.RecallToolRepository.PlaceRow;
import com.ktc.chungnam3.remembrall.recall.tool.RecallToolRepository.SearchPlaceRow;
import com.ktc.chungnam3.remembrall.recall.tool.RecallToolResults.ContentCandidate;
import com.ktc.chungnam3.remembrall.recall.tool.RecallToolResults.ContentDetail;
import com.ktc.chungnam3.remembrall.recall.tool.RecallToolResults.ContentPlace;
import com.ktc.chungnam3.remembrall.recall.tool.RecallToolResults.DetailsResult;
import com.ktc.chungnam3.remembrall.recall.tool.RecallToolResults.DistancePair;
import com.ktc.chungnam3.remembrall.recall.tool.RecallToolResults.DistancesResult;
import com.ktc.chungnam3.remembrall.recall.tool.RecallToolResults.PlaceCandidate;
import com.ktc.chungnam3.remembrall.recall.tool.RecallToolResults.SearchResult;
import com.ktc.chungnam3.remembrall.recall.tool.RecallToolResults.TargetError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
    private final WalkingTimeCalculator walking;
    private final TransactionTemplate reads;

    public RecallToolService(EmbeddingClient embeddings, RecallToolRepository repository,
                             RecallCandidateProperties candidates, RecallToolProperties properties,
                             PlatformTransactionManager transactionManager, WalkingTimeCalculator walking) {
        this.embeddings = embeddings;
        this.repository = repository;
        this.candidates = candidates;
        this.properties = properties;
        this.walking = walking;
        this.reads = new TransactionTemplate(transactionManager);
        this.reads.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.reads.setReadOnly(true);
    }

    public SearchResult searchCandidates(RecallToolScope scope, String condition, Double radiusMeters) {
        if (!validScope(scope)) {
            return new SearchResult(List.of(), List.of(), "실행 범위를 확인할 수 없습니다.");
        }
        if (condition == null || condition.isBlank()) {
            return new SearchResult(List.of(), List.of(), "검색 조건이 필요합니다.");
        }
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
                var rows = repository.searchPlaces(scope, vector, radius, properties.searchPlaceLimit());
                var placesById = new LinkedHashMap<UUID, SearchPlaceRow>();
                var contentsByPlace = new LinkedHashMap<UUID, List<ContentCandidate>>();
                rows.forEach(row -> {
                    placesById.putIfAbsent(row.placeId(), row);
                    contentsByPlace.computeIfAbsent(row.placeId(), key -> new ArrayList<>()).add(candidate(row.content()));
                });
                var places = placesById.values().stream().map(row -> new PlaceCandidate(row.placeId(), row.name(),
                        row.distanceMeters(), List.copyOf(contentsByPlace.get(row.placeId())))).toList();
                var contents = repository.searchContents(scope.memberId(), vector, properties.searchContentLimit())
                        .stream().map(this::candidate).toList();
                return new SearchResult(places, contents, null);
            });
        } catch (DataAccessException exception) {
            log.warn("Recall candidate query failed", exception);
            return new SearchResult(List.of(), List.of(), "후보를 조회할 수 없습니다.");
        }
    }

    public DetailsResult getContentDetails(RecallToolScope scope, List<UUID> contentIds) {
        String error = validateIds(scope, contentIds, properties.maxContentDetails());
        if (error != null) {
            return new DetailsResult(List.of(), List.of(), error);
        }
        if (contentIds.isEmpty()) {
            return new DetailsResult(List.of(), List.of(), null);
        }
        var ids = List.copyOf(new LinkedHashSet<>(contentIds));
        try {
            return reads.execute(status -> {
                var contentsById = new LinkedHashMap<UUID, ContentRow>();
                var placesByContent = new LinkedHashMap<UUID, List<ContentPlace>>();
                repository.findContentDetails(scope.memberId(), ids).forEach(row -> {
                    contentsById.putIfAbsent(row.content().contentId(), row.content());
                    var places = placesByContent.computeIfAbsent(row.content().contentId(), key -> new ArrayList<>());
                    if (row.placeId() != null) {
                        places.add(new ContentPlace(row.placeId(), row.name(), row.description(),
                                walking.straightLineMeters(scope.latitude(), scope.longitude(), row.latitude(), row.longitude())));
                    }
                });
                var contents = new ArrayList<ContentDetail>();
                var errors = new ArrayList<TargetError>();
                for (UUID id : ids) {
                    var row = contentsById.get(id);
                    if (row == null) {
                        errors.add(new TargetError(id, NOT_FOUND));
                    } else {
                        contents.add(new ContentDetail(id, row.source(), row.title(),
                                summarize(row.summary(), properties.detailsSummaryLength()), row.category(),
                                List.copyOf(placesByContent.get(id)), row.savedAt()));
                    }
                }
                return new DetailsResult(List.copyOf(contents), List.copyOf(errors), null);
            });
        } catch (DataAccessException exception) {
            log.warn("Recall content details query failed", exception);
            return new DetailsResult(List.of(), List.of(), "상세를 조회할 수 없습니다.");
        }
    }

    public DistancesResult getPlaceDistances(RecallToolScope scope, List<UUID> placeIds) {
        String error = validateIds(scope, placeIds, properties.maxPlaceDistances());
        if (error != null) {
            return new DistancesResult(List.of(), List.of(), error);
        }
        var ids = new LinkedHashSet<>(placeIds);
        ids.remove(scope.triggerPlaceId());
        try {
            return reads.execute(status -> {
                Map<UUID, PlaceRow> accessible = new LinkedHashMap<>();
                if (!ids.isEmpty()) {
                    repository.findAccessiblePlaces(scope.memberId(), List.copyOf(ids))
                            .forEach(row -> accessible.put(row.placeId(), row));
                }
                var places = new ArrayList<PlaceRow>();
                places.add(new PlaceRow(scope.triggerPlaceId(), scope.latitude(), scope.longitude()));
                var errors = new ArrayList<TargetError>();
                for (UUID id : ids) {
                    var place = accessible.get(id);
                    if (place == null) {
                        errors.add(new TargetError(id, NOT_FOUND));
                    } else {
                        places.add(place);
                    }
                }
                var pairs = new ArrayList<DistancePair>();
                for (int from = 0; from < places.size(); from++) {
                    for (int to = from + 1; to < places.size(); to++) {
                        var first = places.get(from);
                        var second = places.get(to);
                        double meters = walking.straightLineMeters(first.latitude(), first.longitude(),
                                second.latitude(), second.longitude());
                        pairs.add(new DistancePair(first.placeId(), second.placeId(), meters,
                                walking.estimatedMinutes(meters), true));
                    }
                }
                return new DistancesResult(List.copyOf(pairs), List.copyOf(errors), null);
            });
        } catch (DataAccessException exception) {
            log.warn("Recall place distances query failed", exception);
            return new DistancesResult(List.of(), List.of(), "장소를 조회할 수 없습니다.");
        }
    }

    private String validateIds(RecallToolScope scope, List<UUID> ids, int maximum) {
        if (!validScope(scope)) {
            return "실행 범위를 확인할 수 없습니다.";
        }
        if (ids == null || ids.stream().anyMatch(java.util.Objects::isNull)) {
            return "유효한 ID 목록이 필요합니다.";
        }
        return ids.size() > maximum ? "요청 개수가 최대 " + maximum + "개를 초과했습니다." : null;
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
            if (!Float.isFinite(value)) {
                throw new IllegalStateException("Invalid embedding value");
            }
            if (vector.length() > 1) {
                vector.append(',');
            }
            vector.append(value);
            squaredLength += (double) value * value;
        }
        if (squaredLength == 0) {
            throw new IllegalStateException("Empty embedding vector");
        }
        return vector.append(']').toString();
    }
}
