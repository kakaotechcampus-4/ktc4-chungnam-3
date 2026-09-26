package com.ktc.chungnam3.remembrall.content.service;

import com.ktc.chungnam3.remembrall.content.dto.AnalysisOutcome;
import com.ktc.chungnam3.remembrall.content.dto.ContentSaveClaim;
import com.ktc.chungnam3.remembrall.domain.content.Content;
import com.ktc.chungnam3.remembrall.domain.personalsave.PersonalSave;
import com.ktc.chungnam3.remembrall.repository.ContentRepository;
import com.ktc.chungnam3.remembrall.repository.PersonalSaveRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class ContentPersistenceService {

    private final ContentRepository contentRepository;
    private final PersonalSaveRepository personalSaveRepository;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public ContentPersistenceService(
            ContentRepository contentRepository,
            PersonalSaveRepository personalSaveRepository,
            Clock clock,
            PlatformTransactionManager transactionManager
    ) {
        this.contentRepository = contentRepository;
        this.personalSaveRepository = personalSaveRepository;
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

            int inserted = personalSaveRepository.insertIfAbsent(UUID.randomUUID(), memberId, contentId, now);
            PersonalSave personalSave = personalSaveRepository.findByMemberIdAndContentId(memberId, contentId)
                    .orElseThrow(() -> new IllegalStateException("PersonalSave insert or lookup failed"));

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
            Instant now = clock.instant();
            return contentRepository.applyOutcome(
                    contentId,
                    outcome.status().name(),
                    outcome.sourceStatus().name(),
                    outcome.title(),
                    outcome.summary(),
                    outcome.category(),
                    outcome.analysisVersion(),
                    outcome.errorCode(),
                    outcome.metadataFetchedAt(),
                    now,
                    now
            ) == 1;
        }));
    }
}
