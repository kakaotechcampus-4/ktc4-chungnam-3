package com.ktc.chungnam3.remembrall.recall.service;

import com.ktc.chungnam3.remembrall.domain.recallexecution.ProposalType;
import com.ktc.chungnam3.remembrall.domain.content.ContentAnalysisStatus;
import com.ktc.chungnam3.remembrall.domain.content.ContentSourceStatus;
import com.ktc.chungnam3.remembrall.domain.place.Place;
import com.ktc.chungnam3.remembrall.recall.agent.ProposalSource;
import com.ktc.chungnam3.remembrall.recall.agent.RecallAgentResult;
import com.ktc.chungnam3.remembrall.recall.config.RecallProperties;
import com.ktc.chungnam3.remembrall.repository.ContentPlaceRepository;
import com.ktc.chungnam3.remembrall.repository.ContentRepository;
import com.ktc.chungnam3.remembrall.repository.PersonalSaveRepository;
import com.ktc.chungnam3.remembrall.repository.PlaceRepository;
import com.ktc.chungnam3.remembrall.repository.PublicCandidateRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Component
public class ProposalValidator {
    private final ContentRepository contents;
    private final PlaceRepository places;
    private final ContentPlaceRepository links;
    private final PersonalSaveRepository saves;
    private final PublicCandidateRepository publicCandidates;
    private final WalkingTimeCalculator walking;
    private final RecallProperties properties;

    public ProposalValidator(ContentRepository contents, PlaceRepository places,
                             ContentPlaceRepository links, PersonalSaveRepository saves,
                             PublicCandidateRepository publicCandidates, WalkingTimeCalculator walking,
                             RecallProperties properties) {
        this.contents = contents;
        this.places = places;
        this.links = links;
        this.saves = saves;
        this.publicCandidates = publicCandidates;
        this.walking = walking;
        this.properties = properties;
    }

    public boolean valid(UUID memberId, UUID triggerPlaceId, RecallAgentResult result) {
        if (result.proposalType() == null || result.proposalPayload() == null
                || result.proposalPayload().items() == null || result.decisionSummary() == null
                || result.title() == null || result.body() == null) {
            return false;
        }
        var items = result.proposalPayload().items();
        if (result.proposalType() == ProposalType.COURSE ? items.size() < 2 : items.size() != 1) {
            return false;
        }
        var coursePlaceIds = new HashSet<UUID>();
        var coursePlaces = new ArrayList<Place>();
        for (var item : items) {
            if (item == null || item.contentId() == null || item.source() == null) {
                return false;
            }
            var content = contents.findById(item.contentId()).orElse(null);
            if (content == null || content.getAnalysisStatus() == ContentAnalysisStatus.FAILED
                    || content.getSourceStatus() == ContentSourceStatus.UNAVAILABLE) {
                return false;
            }
            boolean personallySaved = saves.findByMemberIdAndContentId(memberId, item.contentId()).isPresent();
            if (item.source() == ProposalSource.PERSONAL ? !personallySaved
                    : personallySaved || !publicCandidates.isEligibleContent(item.contentId())) {
                return false;
            }
            if (result.proposalType() == ProposalType.CONTENT) {
                if (item.placeId() != null) {
                    return false;
                }
            } else {
                if (item.placeId() == null) {
                    return false;
                }
                var place = places.findById(item.placeId()).orElse(null);
                if (place == null || links.findByContent_IdAndPlace_Id(item.contentId(), item.placeId()).isEmpty()) {
                    return false;
                }
                if (result.proposalType() == ProposalType.COURSE) {
                    coursePlaces.add(place);
                }
            }
            if (result.proposalType() == ProposalType.COURSE && !coursePlaceIds.add(item.placeId())) {
                return false;
            }
        }
        return result.proposalType() != ProposalType.COURSE || withinWalkingLimit(triggerPlaceId, coursePlaces);
    }

    private boolean withinWalkingLimit(UUID triggerPlaceId, List<Place> coursePlaces) {
        var previous = triggerPlaceId == null ? null : places.findById(triggerPlaceId).orElse(null);
        if (previous == null) {
            return false;
        }
        double totalMinutes = 0;
        for (var place : coursePlaces) {
            double meters = walking.straightLineMeters(previous.getLatitude(), previous.getLongitude(),
                    place.getLatitude(), place.getLongitude());
            totalMinutes += walking.estimatedMinutes(meters);
            if (!Double.isFinite(totalMinutes) || totalMinutes > properties.maxCourseWalkingMinutes()) {
                return false;
            }
            previous = place;
        }
        return true;
    }
}
