package com.ktc.chungnam3.remembrall.save.confirm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktc.chungnam3.remembrall.common.exception.ApiException;
import com.ktc.chungnam3.remembrall.common.exception.ErrorCode;
import com.ktc.chungnam3.remembrall.content.dto.AnalysisOutcome;
import com.ktc.chungnam3.remembrall.content.service.ContentPersistenceService;
import com.ktc.chungnam3.remembrall.domain.confirm.ConfirmCandidate;
import com.ktc.chungnam3.remembrall.domain.confirm.ConfirmCandidateStatus;
import com.ktc.chungnam3.remembrall.repository.ConfirmCandidateRepository;
import com.ktc.chungnam3.remembrall.repository.PersonalSaveRepository;
import com.ktc.chungnam3.remembrall.save.dto.ConfirmRequestDto;
import com.ktc.chungnam3.remembrall.save.dto.ResolvedPlaceDto;
import com.ktc.chungnam3.remembrall.save.place.KakaoPlaceLookup;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

/**
 * 되묻기 후보에 대한 사용자 답변을 반영한다 - 고른 옵션을 {@link SavePipelineService}의
 * {@code verifyWithKakao}와 같은 방식으로 카카오 검증한 뒤, {@link ContentPersistenceService
 * #addConfirmedPlace}로 Place/ContentPlace/Trigger를 생성한다(기존 저장 로직 재사용, 새로 안 만듦).
 * <p>
 * {@code personalSaveId}가 호출한 회원({@code memberId}) 소유인지 {@link PersonalSaveRepository
 * #findOwnedContentId}로 먼저 확인한다(PR 리뷰로 발견된 버그 수정, 2026-10-29) - 예전엔 contentId만
 * 받아서 로그인한 아무나 남이 저장한 콘텐츠에 답변을 제출할 수 있었다. {@code
 * ContentPersistenceService#deletePersonalSave}가 쓰는 것과 같은 소유권 확인 패턴.
 */
@Component
public class ConfirmAnswerService {

    private final ConfirmCandidateRepository confirmCandidateRepository;
    private final ContentPersistenceService contentPersistenceService;
    private final PersonalSaveRepository personalSaveRepository;
    private final KakaoPlaceLookup kakaoPlaceLookup;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ConfirmAnswerService(
            ConfirmCandidateRepository confirmCandidateRepository,
            ContentPersistenceService contentPersistenceService,
            PersonalSaveRepository personalSaveRepository,
            KakaoPlaceLookup kakaoPlaceLookup,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.confirmCandidateRepository = confirmCandidateRepository;
        this.contentPersistenceService = contentPersistenceService;
        this.personalSaveRepository = personalSaveRepository;
        this.kakaoPlaceLookup = kakaoPlaceLookup;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public void answer(UUID memberId, UUID personalSaveId, String candidateId, String selectedOptionId) {
        UUID contentId = personalSaveRepository.findOwnedContentId(personalSaveId, memberId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));

        ConfirmCandidate candidate = confirmCandidateRepository.findByContent_IdAndCandidateId(contentId, candidateId)
                .filter(c -> c.getStatus() == ConfirmCandidateStatus.PENDING)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));

        if (selectedOptionId == null) {
            confirmCandidateRepository.markAnswered(candidate.getId(), clock.instant());
            return;
        }

        ConfirmRequestDto confirmRequest = readConfirmRequest(candidate.getConfirmRequest());
        ResolvedPlaceDto selected = confirmRequest.candidates().stream()
                .filter(option -> selectedOptionId.equals(option.candidateId()))
                .findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_REQUEST));

        Optional<String> verificationPlaceId;
        try {
            verificationPlaceId = kakaoPlaceLookup.findPlaceId(
                    selected.name(), selected.branchName(), selected.lat(), selected.lng());
        } catch (RuntimeException e) {
            throw new ApiException(ErrorCode.KAKAO_API_ERROR, e);
        }
        if (verificationPlaceId.isEmpty()) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }

        AnalysisOutcome.PlaceResult placeResult = new AnalysisOutcome.PlaceResult(
                candidate.getDescription(), selected.name(), selected.address(), selected.lat(), selected.lng(),
                selected.geocodingProvider(), selected.geocodingPlaceId(), verificationPlaceId.get());

        if (!contentPersistenceService.addConfirmedPlace(contentId, placeResult)) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        confirmCandidateRepository.markAnswered(candidate.getId(), clock.instant());
    }

    private ConfirmRequestDto readConfirmRequest(String json) {
        try {
            return objectMapper.readValue(json, ConfirmRequestDto.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("되묻기 후보 역직렬화 실패", e);
        }
    }
}
