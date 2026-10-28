package com.ktc.chungnam3.remembrall.recall.service;

import com.ktc.chungnam3.remembrall.domain.content.Content;
import com.ktc.chungnam3.remembrall.domain.content.ContentAnalysisStatus;
import com.ktc.chungnam3.remembrall.domain.content.ContentSourceStatus;
import com.ktc.chungnam3.remembrall.domain.contentplace.ContentPlace;
import com.ktc.chungnam3.remembrall.domain.personalsave.PersonalSave;
import com.ktc.chungnam3.remembrall.domain.place.GeocodingProvider;
import com.ktc.chungnam3.remembrall.domain.place.Place;
import com.ktc.chungnam3.remembrall.domain.place.VerificationProvider;
import com.ktc.chungnam3.remembrall.domain.recallexecution.ProposalType;
import com.ktc.chungnam3.remembrall.recall.agent.ProposalPayload;
import com.ktc.chungnam3.remembrall.recall.agent.ProposalSource;
import com.ktc.chungnam3.remembrall.recall.agent.RecallAgentResult;
import com.ktc.chungnam3.remembrall.recall.config.RecallProperties;
import com.ktc.chungnam3.remembrall.recall.config.RecallToolProperties;
import com.ktc.chungnam3.remembrall.repository.ContentPlaceRepository;
import com.ktc.chungnam3.remembrall.repository.ContentRepository;
import com.ktc.chungnam3.remembrall.repository.PersonalSaveRepository;
import com.ktc.chungnam3.remembrall.repository.PlaceRepository;
import com.ktc.chungnam3.remembrall.repository.PublicCandidateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProposalValidatorTest {
    private final UUID memberId = UUID.randomUUID();
    private final UUID contentId = UUID.randomUUID();
    private final ContentRepository contents = mock(ContentRepository.class);
    private final PlaceRepository places = mock(PlaceRepository.class);
    private final ContentPlaceRepository links = mock(ContentPlaceRepository.class);
    private final PersonalSaveRepository saves = mock(PersonalSaveRepository.class);
    private final PublicCandidateRepository publicCandidates = mock(PublicCandidateRepository.class);
    private final WalkingTimeCalculator walking = new WalkingTimeCalculator(
            new RecallToolProperties(5000, 20, 20, 3, 2000, java.time.Duration.ofDays(7), 1.5, 100));
    private final Place origin = place(37.0);
    private final Place first = place(37.001);
    private final Place last = place(37.002);

    @BeforeEach
    void setUp() {
        var content = mock(Content.class);
        when(content.getAnalysisStatus()).thenReturn(ContentAnalysisStatus.SUCCESS);
        when(content.getSourceStatus()).thenReturn(ContentSourceStatus.AVAILABLE);
        when(contents.findById(contentId)).thenReturn(Optional.of(content));
        when(saves.findByMemberIdAndContentId(memberId, contentId))
                .thenReturn(Optional.of(mock(PersonalSave.class)));
        when(links.findByContent_IdAndPlace_Id(any(), any()))
                .thenReturn(Optional.of(mock(ContentPlace.class)));
        for (Place place : List.of(origin, first, last)) {
            when(places.findById(place.getId())).thenReturn(Optional.of(place));
        }
    }

    @Test
    void acceptsExactLimitAndRejectsTotalJustOverLimitUsingSharedWalkingSettings() {
        double total = minutes(origin, first) + minutes(first, last);
        var result = course();

        assertThat(validator(total).valid(memberId, origin.getId(), result)).isTrue();
        assertThat(validator(total - 0.000001).valid(memberId, origin.getId(), result)).isFalse();
    }

    @Test
    void missingTriggerPlaceFailsCourseValidation() {
        UUID missing = UUID.randomUUID();
        when(places.findById(missing)).thenReturn(Optional.empty());

        assertThat(validator(30).valid(memberId, missing, course())).isFalse();
        assertThat(validator(30).valid(memberId, null, course())).isFalse();
    }

    private ProposalValidator validator(double maximum) {
        var properties = new RecallProperties(Duration.ofMinutes(30), Duration.ofHours(2), maximum);
        return new ProposalValidator(contents, places, links, saves, publicCandidates, walking, properties);
    }

    private RecallAgentResult course() {
        return RecallAgentResult.propose(ProposalType.COURSE, new ProposalPayload(List.of(
                new ProposalPayload.Item(contentId, first.getId(), ProposalSource.PERSONAL),
                new ProposalPayload.Item(contentId, last.getId(), ProposalSource.PERSONAL))),
                "Decision", "Title", "Body");
    }

    private double minutes(Place from, Place to) {
        return walking.estimatedMinutes(walking.straightLineMeters(from.getLatitude(), from.getLongitude(),
                to.getLatitude(), to.getLongitude()));
    }

    private static Place place(double latitude) {
        return Place.create("Place", "Address", latitude, 127, GeocodingProvider.LOCATIONIQ,
                UUID.randomUUID().toString(), VerificationProvider.KAKAO, UUID.randomUUID().toString(),
                Instant.EPOCH, Instant.EPOCH);
    }
}
