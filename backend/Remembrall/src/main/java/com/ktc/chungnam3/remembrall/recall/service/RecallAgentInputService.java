package com.ktc.chungnam3.remembrall.recall.service;

import com.ktc.chungnam3.remembrall.recall.agent.ProposalSource;
import com.ktc.chungnam3.remembrall.recall.agent.RecallAgentInput;
import com.ktc.chungnam3.remembrall.recall.agent.RecallAgentInput.Origin;
import com.ktc.chungnam3.remembrall.recall.agent.RecallAgentInput.PersonalContent;
import com.ktc.chungnam3.remembrall.recall.agent.RecallAgentInput.PersonalPlace;
import com.ktc.chungnam3.remembrall.recall.agent.RecallAgentInput.PublicContent;
import com.ktc.chungnam3.remembrall.recall.agent.RecallAgentInput.PublicPlace;
import com.ktc.chungnam3.remembrall.recall.agent.RecallAgentInput.SemanticContent;
import com.ktc.chungnam3.remembrall.recall.agent.RecallAgentInput.Situation;
import com.ktc.chungnam3.remembrall.recall.config.RecallCandidateProperties;
import com.ktc.chungnam3.remembrall.repository.RecallCandidateRepository;
import com.ktc.chungnam3.remembrall.repository.RecallCandidateRepository.PersonalContentRow;
import com.ktc.chungnam3.remembrall.repository.RecallCandidateRepository.PersonalPlaceRow;
import com.ktc.chungnam3.remembrall.repository.RecallCandidateRepository.PublicPlaceRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

@Service
public class RecallAgentInputService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final RecallCandidateRepository candidates;
    private final RecallCandidateProperties properties;

    public RecallAgentInputService(RecallCandidateRepository candidates, RecallCandidateProperties properties) {
        this.candidates = candidates;
        this.properties = properties;
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public RecallAgentInput prepare(UUID executionId) {
        var rows = candidates.findOrigin(executionId);
        if (rows.isEmpty()) {
            throw new IllegalStateException("Recall execution origin no longer exists");
        }
        var origin = rows.getFirst();
        var contents = rows.stream().filter(row -> row.content() != null)
                .map(row -> personalContent(row.content())).toList();
        var personalRows = candidates.findNearbyPersonal(origin.memberId(), origin.placeId(),
                origin.latitude(), origin.longitude(), properties.radiusMeters(), properties.nearbyPersonalLimit());
        var personalContents = new LinkedHashMap<UUID, List<PersonalContent>>();
        var personalPlacesById = new LinkedHashMap<UUID, PersonalPlaceRow>();
        personalRows.forEach(row -> {
            personalPlacesById.putIfAbsent(row.placeId(), row);
            personalContents.computeIfAbsent(row.placeId(), key -> new ArrayList<>()).add(personalContent(row.content()));
        });
        var personalPlaces = personalPlacesById.values().stream().map(row -> new PersonalPlace(row.placeId(), row.name(),
                row.distanceMeters(), List.copyOf(personalContents.get(row.placeId())))).toList();

        var publicRows = candidates.findNearbyPublic(origin.memberId(),
                origin.latitude(), origin.longitude(), properties.radiusMeters(), properties.nearbyPublicLimit());
        var publicContents = new LinkedHashMap<UUID, List<PublicContent>>();
        var publicPlacesById = new LinkedHashMap<UUID, PublicPlaceRow>();
        publicRows.forEach(row -> {
            publicPlacesById.putIfAbsent(row.placeId(), row);
            publicContents.computeIfAbsent(row.placeId(), key -> new ArrayList<>())
                    .add(new PublicContent(row.contentId(), row.title(), summarize(row.summary()),
                            row.category(), row.description()));
        });
        var publicPlaces = publicPlacesById.values().stream().map(row -> new PublicPlace(row.placeId(), row.name(),
                row.distanceMeters(), List.copyOf(publicContents.get(row.placeId())))).toList();

        var semantic = candidates.findSemantic(origin.memberId(), origin.placeId(), properties.semanticLimit())
                .stream().map(row -> new SemanticContent(row.contentId(),
                        row.personalSaveId() == null ? ProposalSource.PUBLIC : ProposalSource.PERSONAL,
                        row.personalSaveId(), row.title(), summarize(row.summary()), row.category(), row.savedAt()))
                .toList();
        var localTime = origin.eventOccurredAt().atZone(SEOUL);
        return new RecallAgentInput(new Origin(origin.placeId(), origin.name(), origin.latitude(),
                origin.longitude(), contents), personalPlaces, publicPlaces, semantic,
                new Situation(localTime.toLocalDateTime(), localTime.getDayOfWeek()));
    }

    private PersonalContent personalContent(PersonalContentRow row) {
        return new PersonalContent(row.contentId(), row.personalSaveId(), row.title(), summarize(row.summary()),
                row.category(), row.savedAt(), row.description());
    }

    private String summarize(String summary) {
        if (summary == null || summary.codePointCount(0, summary.length()) <= properties.maxSummaryLength()) {
            return summary;
        }
        return summary.substring(0, summary.offsetByCodePoints(0, properties.maxSummaryLength()));
    }
}
