package com.ktc.chungnam3.remembrall.personalsave.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktc.chungnam3.remembrall.common.exception.ApiException;
import com.ktc.chungnam3.remembrall.common.exception.ErrorCode;
import com.ktc.chungnam3.remembrall.content.dto.ContentSaveClaim;
import com.ktc.chungnam3.remembrall.content.service.ContentPersistenceService;
import com.ktc.chungnam3.remembrall.domain.confirm.ConfirmCandidate;
import com.ktc.chungnam3.remembrall.personalsave.config.PersonalSaveProperties;
import com.ktc.chungnam3.remembrall.personalsave.dto.PersonalSaveResponse;
import com.ktc.chungnam3.remembrall.personalsave.repository.PersonalSaveReadRepository;
import com.ktc.chungnam3.remembrall.repository.ConfirmCandidateRepository;
import com.ktc.chungnam3.remembrall.save.dto.ConfirmRequestDto;
import com.ktc.chungnam3.remembrall.save.youtube.YoutubeUrlParser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.UUID;

@Service
public class PersonalSaveService {
    private final ContentPersistenceService persistence;
    private final PersonalSaveAnalysisService analysis;
    private final PersonalSaveReadRepository reads;
    private final PersonalSaveProperties properties;
    private final ConfirmCandidateRepository confirmCandidates;
    private final ObjectMapper objectMapper;

    public PersonalSaveService(ContentPersistenceService persistence, PersonalSaveAnalysisService analysis,
                               PersonalSaveReadRepository reads, PersonalSaveProperties properties,
                               ConfirmCandidateRepository confirmCandidates, ObjectMapper objectMapper) {
        this.persistence = persistence;
        this.analysis = analysis;
        this.reads = reads;
        this.properties = properties;
        this.confirmCandidates = confirmCandidates;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ContentSaveClaim save(UUID memberId, String url) {
        String videoId = extractVideoId(url);
        ContentSaveClaim claim = persistence.saveAndClaim(memberId, videoId);
        if (claim.analysisClaimed()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    analysis.submit(claim.contentId(), videoId);
                }
            });
        }
        return claim;
    }

    @Transactional(readOnly = true)
    public PersonalSaveResponse.Page list(UUID memberId, Integer requestedSize, String cursor) {
        int size = requestedSize == null ? properties.defaultPageSize() : requestedSize;
        if (size < 1 || size > properties.maxPageSize()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }
        List<PersonalSaveResponse.Item> rows = reads.findPage(
                memberId, PersonalSaveCursor.decode(cursor), size + 1, properties.maxSummaryLength());
        boolean hasNext = rows.size() > size;
        List<PersonalSaveResponse.Item> items = List.copyOf(hasNext ? rows.subList(0, size) : rows);
        String nextCursor = null;
        if (hasNext) {
            var last = items.getLast();
            nextCursor = new PersonalSaveCursor(last.savedAt(), last.personalSaveId()).encode();
        }
        return new PersonalSaveResponse.Page(items, nextCursor);
    }

    @Transactional(readOnly = true)
    public PersonalSaveResponse.Detail detail(UUID memberId, UUID personalSaveId) {
        var detail = reads.findOwnedDetail(memberId, personalSaveId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        return new PersonalSaveResponse.Detail(detail.personalSaveId(), detail.contentId(), detail.videoId(),
                detail.sourceUrl(), detail.title(), detail.summary(), detail.category(), detail.analysisStatus(),
                detail.analysisFailureCode(), detail.savedAt(), reads.findPlaces(detail.contentId()),
                pendingConfirmCandidates(detail.contentId()));
    }

    private List<PersonalSaveResponse.ConfirmCandidate> pendingConfirmCandidates(UUID contentId) {
        return confirmCandidates.findPendingByContentId(contentId).stream()
                .map(this::toConfirmCandidate)
                .toList();
    }

    private PersonalSaveResponse.ConfirmCandidate toConfirmCandidate(ConfirmCandidate candidate) {
        ConfirmRequestDto request;
        try {
            request = objectMapper.readValue(candidate.getConfirmRequest(), ConfirmRequestDto.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        return new PersonalSaveResponse.ConfirmCandidate(candidate.getCandidateId(), request.question(), request.candidates());
    }

    public void delete(UUID memberId, UUID personalSaveId) {
        if (!persistence.deletePersonalSave(memberId, personalSaveId)) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
    }

    private String extractVideoId(String url) {
        if (url == null || url.isBlank()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }
        try {
            return YoutubeUrlParser.extractVideoId(url)
                    .filter(id -> id.matches("[A-Za-z0-9_-]{1,32}"))
                    .orElseThrow(() -> new ApiException(ErrorCode.INVALID_REQUEST));
        } catch (IllegalArgumentException | IndexOutOfBoundsException exception) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }
    }
}
