package com.ktc.chungnam3.remembrall.content.service;

import com.ktc.chungnam3.remembrall.common.exception.ApiException;
import com.ktc.chungnam3.remembrall.common.exception.ErrorCode;
import com.ktc.chungnam3.remembrall.content.dto.AnalysisOutcome;
import com.ktc.chungnam3.remembrall.content.dto.ContentSaveClaim;
import com.ktc.chungnam3.remembrall.domain.content.Content;
import com.ktc.chungnam3.remembrall.domain.place.Place;
import com.ktc.chungnam3.remembrall.domain.place.VerificationProvider;
import com.ktc.chungnam3.remembrall.domain.personalsave.PersonalSave;
import com.ktc.chungnam3.remembrall.repository.ContentPlaceRepository;
import com.ktc.chungnam3.remembrall.repository.ContentRepository;
import com.ktc.chungnam3.remembrall.repository.MemberRepository;
import com.ktc.chungnam3.remembrall.repository.PersonalSaveRepository;
import com.ktc.chungnam3.remembrall.repository.PlaceRepository;
import com.ktc.chungnam3.remembrall.repository.TriggerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class ContentPersistenceService {

    private final ContentRepository contentRepository;
    private final PersonalSaveRepository personalSaveRepository;
    private final PlaceRepository placeRepository;
    private final ContentPlaceRepository contentPlaceRepository;
    private final MemberRepository memberRepository;
    private final TriggerRepository triggerRepository;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public ContentPersistenceService(
            ContentRepository contentRepository,
            PersonalSaveRepository personalSaveRepository,
            PlaceRepository placeRepository,
            ContentPlaceRepository contentPlaceRepository,
            MemberRepository memberRepository,
            TriggerRepository triggerRepository,
            Clock clock,
            PlatformTransactionManager transactionManager
    ) {
        this.contentRepository = contentRepository;
        this.personalSaveRepository = personalSaveRepository;
        this.placeRepository = placeRepository;
        this.contentPlaceRepository = contentPlaceRepository;
        this.memberRepository = memberRepository;
        this.triggerRepository = triggerRepository;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public ContentSaveClaim saveAndClaim(UUID memberId, String videoId) {
        Objects.requireNonNull(memberId, "memberId");
        if (videoId == null || videoId.isBlank() || videoId.length() > 32) {
            throw new IllegalArgumentException("videoId must contain 1 to 32 characters");
        }

        return Objects.requireNonNull(transactionTemplate.execute(transaction -> {
            Instant now = clock.instant();
            contentRepository.insertIfAbsent(UUID.randomUUID(), videoId, now);
            UUID contentId = contentRepository.findIdByVideoId(videoId)
                    .orElseThrow(() -> new IllegalStateException("Content insert or lookup failed"));

            contentRepository.findIdForUpdate(contentId)
                    .orElseThrow(() -> new IllegalStateException("Content disappeared before save"));
            lockMember(memberId);

            int inserted = personalSaveRepository.insertIfAbsent(UUID.randomUUID(), memberId, contentId, now);
            PersonalSave personalSave = personalSaveRepository.findByMemberIdAndContentId(memberId, contentId)
                    .orElseThrow(() -> new IllegalStateException("PersonalSave insert or lookup failed"));

            createTriggers(memberId, contentPlaceRepository.findPlaceIdsByContentId(contentId), now);

            boolean claimed = contentRepository.claimAnalysis(contentId, now) == 1;
            Content content = contentRepository.findById(contentId)
                    .orElseThrow(() -> new IllegalStateException("Content disappeared after analysis claim"));

            return new ContentSaveClaim(
                    contentId,
                    personalSave.getId(),
                    inserted == 1,
                    content.getAnalysisStatus(),
                    claimed
            );
        }));
    }

    public boolean applyOutcome(UUID contentId, AnalysisOutcome outcome) {
        Objects.requireNonNull(contentId, "contentId");
        Objects.requireNonNull(outcome, "outcome");

        return Boolean.TRUE.equals(transactionTemplate.execute(transaction -> {
            if (contentRepository.findIdForUpdate(contentId).isEmpty()) {
                return false;
            }
            Instant now = clock.instant();
            int updatedRows = contentRepository.applyOutcome(
                    contentId,
                    outcome.status().name(),
                    outcome.sourceStatus() == null ? null : outcome.sourceStatus().name(),
                    outcome.title(),
                    outcome.summary(),
                    outcome.category(),
                    outcome.analysisVersion(),
                    outcome.failureCode() == null ? null : outcome.failureCode().name(),
                    outcome.metadataFetchedAt(),
                    now,
                    now
            );
            if (updatedRows != 1) {
                return false;
            }

            List<UUID> memberIds = personalSaveRepository.findMemberIdsByContentId(contentId)
                    .stream().sorted().toList();
            memberIds.forEach(this::lockMember);
            if (outcome.places().isEmpty()) {
                return true;
            }
            outcome.places().stream()
                    .sorted(Comparator.comparing(AnalysisOutcome.PlaceResult::verificationPlaceId))
                    .forEach(place -> savePlaceAndLink(contentId, place, now));
            List<UUID> placeIds = contentPlaceRepository.findPlaceIdsByContentId(contentId);
            memberIds.forEach(memberId -> createTriggers(memberId, placeIds, now));
            return true;
        }));
    }

    public boolean deletePersonalSave(UUID memberId, UUID personalSaveId) {
        Objects.requireNonNull(memberId, "memberId");
        Objects.requireNonNull(personalSaveId, "personalSaveId");

        return Boolean.TRUE.equals(transactionTemplate.execute(transaction -> {
            UUID contentId = personalSaveRepository.findOwnedContentId(personalSaveId, memberId)
                    .orElse(null);
            if (contentId == null || contentRepository.findIdForUpdate(contentId).isEmpty()) {
                return false;
            }
            lockMember(memberId);
            if (personalSaveRepository.deleteOwnedSave(personalSaveId, memberId) != 1) {
                return false;
            }

            contentPlaceRepository.findPlaceIdsByContentId(contentId)
                    .forEach(placeId -> triggerRepository.deleteIfUnreferenced(memberId, placeId));
            return true;
        }));
    }

    private void lockMember(UUID memberId) {
        memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_SESSION));
    }

    private void createTriggers(UUID memberId, List<UUID> placeIds, Instant now) {
        placeIds.forEach(placeId -> triggerRepository.insertIfAbsent(
                UUID.randomUUID(), memberId, placeId, now));
    }

    private void savePlaceAndLink(
            UUID contentId,
            AnalysisOutcome.PlaceResult placeResult,
            Instant now
    ) {
        placeRepository.insertIfAbsent(
                UUID.randomUUID(),
                placeResult.name(),
                placeResult.address(),
                placeResult.latitude(),
                placeResult.longitude(),
                placeResult.geocodingPlaceId(),
                placeResult.verificationPlaceId(),
                now
        );
        Place place = placeRepository.findByVerificationProviderAndVerificationPlaceId(
                        VerificationProvider.KAKAO,
                        placeResult.verificationPlaceId()
                )
                .orElseThrow(() -> new IllegalStateException("Place insert or lookup failed"));
        contentPlaceRepository.insertIfAbsent(
                UUID.randomUUID(),
                contentId,
                place.getId(),
                placeResult.description(),
                now
        );
    }
}
