package com.ktc.chungnam3.remembrall.save;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktc.chungnam3.remembrall.content.dto.AnalysisOutcome;
import com.ktc.chungnam3.remembrall.content.service.ContentAnalysisResultService;
import com.ktc.chungnam3.remembrall.domain.content.ContentAnalysisFailureCode;
import com.ktc.chungnam3.remembrall.domain.content.ContentAnalysisStatus;
import com.ktc.chungnam3.remembrall.domain.content.ContentSourceStatus;
import com.ktc.chungnam3.remembrall.extraction.dto.PlaceCandidateDto;
import com.ktc.chungnam3.remembrall.extraction.dto.YouTubeContentExtractionResultDto;
import com.ktc.chungnam3.remembrall.extraction.type.ExtractionFailureCode;
import com.ktc.chungnam3.remembrall.extraction.type.ExtractionStatus;
import com.ktc.chungnam3.remembrall.repository.ConfirmCandidateRepository;
import com.ktc.chungnam3.remembrall.save.analysis.VideoContentAnalyzer;
import com.ktc.chungnam3.remembrall.save.dto.PlaceOutcomeDto;
import com.ktc.chungnam3.remembrall.save.dto.ResolvedPlaceDto;
import com.ktc.chungnam3.remembrall.save.dto.SavePipelineRequestDto;
import com.ktc.chungnam3.remembrall.save.dto.SavePipelineResultDto;
import com.ktc.chungnam3.remembrall.save.place.KakaoPlaceLookup;
import com.ktc.chungnam3.remembrall.save.place.PlaceLookup;
import com.ktc.chungnam3.remembrall.save.place.PlaceResolver;
import com.ktc.chungnam3.remembrall.save.place.PlaceSearchClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 저장 파이프라인 3단계(전체 조립) - 유튜브 URL 하나를 받아 영상 분석(Module A) → 장소 검색·확정
 * (Module B) → 카카오 장소 검증 → 되묻기 판정까지 묶어서 {@link SavePipelineResultDto}를 만든다.
 * <p>
 * <b>영속화 연동</b>: {@code AnalysisOutcome.PlaceResult}가 요구하는 geocodingPlaceId/verificationPlaceId를
 * 이 파이프라인이 채울 방법이 없어 막혀 있었는데(Place.md 스키마 충돌), 2026-10-10 Place.md 담당자
 * 합의로 geocoding_provider에 DATAPORTAL이 추가되고 카카오 장소 검증까지 이 클래스에 연결되면서
 * 해소됐다 - {@link #saveAndPersist}로 {@code ContentAnalysisResultService.applyOutcome}까지 실제
 * 호출한다. REST 진입점은 {@code SavePipelineController}.
 * <p>
 * <b>카카오 장소 검증</b>: {@link PlaceResolver}가 확정(RESOLVED)한 좌표라도, Place.md 설계상
 * "같은 장소인지" 판단은 카카오 장소 ID가 기준이라 {@link KakaoPlaceLookup}으로 그 ID를 한 번 더
 * 받아야 한다. 못 찾으면(카카오 미등록·동명이인 매장에 밀림 등) 이 후보는 RESOLVED로 내보내지 않고
 * {@link PlaceOutcomeDto.Decision#PLACE_VERIFICATION_FAILED}로 처리한다 - Place.md 생성 흐름 문서의
 * "결과가 모호하거나 일치하지 않으면 Place를 생성하지 않고 실패 기록"과 같은 원칙이다.
 * <p>
 * <b>장소 후보가 여럿인 경우</b>(예: "여행지 Top 5" 영상): 후보마다 독립적으로 판정하고
 * {@link PlaceOutcomeDto} 리스트로 돌려준다 - 하나가 실패해도 나머지는 정상 판정된다.
 */
@Slf4j
@Component
public class SavePipelineService {

    private final VideoAnalyzer videoAnalyzer;
    private final PlaceLookup placeLookup;
    private final PlaceResolver placeResolver;
    private final KakaoPlaceLookup kakaoPlaceLookup;
    private final ContentAnalysisResultService contentAnalysisResultService;
    private final ConfirmCandidateRepository confirmCandidateRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /** Spring이 실제로 쓰는 생성자 - VideoContentAnalyzer(Module A 소유)는 안 건드리고 메서드 참조로
     * {@link VideoAnalyzer}에 연결한다. */
    @Autowired
    public SavePipelineService(VideoContentAnalyzer videoContentAnalyzer, PlaceLookup placeLookup,
                                PlaceResolver placeResolver,
                                KakaoPlaceLookup kakaoPlaceLookup,
                                ContentAnalysisResultService contentAnalysisResultService,
                                ConfirmCandidateRepository confirmCandidateRepository,
                                ObjectMapper objectMapper, Clock clock) {
        this(videoContentAnalyzer::analyze, placeLookup, placeResolver, kakaoPlaceLookup,
                contentAnalysisResultService, confirmCandidateRepository, objectMapper, clock);
    }

    /**
     * 순수 로직만 검증하는 기존 테스트용 생성자(4-인자, 시그니처 그대로 유지) - {@code saveAndPersist}를
     * 쓰지 않는 테스트는 영속화 관련 의존성이 필요 없어서 이 생성자로 충분하다(team 관행: 람다 fake,
     * 모킹 프레임워크 안 씀).
     */
    SavePipelineService(VideoAnalyzer videoAnalyzer, PlaceLookup placeLookup,
                         PlaceResolver placeResolver,
                         KakaoPlaceLookup kakaoPlaceLookup) {
        this(videoAnalyzer, placeLookup, placeResolver, kakaoPlaceLookup,
                null, null, new ObjectMapper(), Clock.systemUTC());
    }

    /** {@code saveAndPersist}까지 테스트하고 싶을 때 쓰는 전체 생성자. */
    SavePipelineService(VideoAnalyzer videoAnalyzer, PlaceLookup placeLookup,
                         PlaceResolver placeResolver,
                         KakaoPlaceLookup kakaoPlaceLookup,
                         ContentAnalysisResultService contentAnalysisResultService,
                         ConfirmCandidateRepository confirmCandidateRepository,
                         ObjectMapper objectMapper, Clock clock) {
        this.videoAnalyzer = videoAnalyzer;
        this.placeLookup = placeLookup;
        this.placeResolver = placeResolver;
        this.kakaoPlaceLookup = kakaoPlaceLookup;
        this.contentAnalysisResultService = contentAnalysisResultService;
        this.confirmCandidateRepository = confirmCandidateRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * 영상 분석 + 장소 확정(+ 카카오 검증)까지의 결과를 한 번만 계산한다 - {@link #save}(순수 조회용)와
     * {@link #saveAndPersist}(실제 저장용) 둘 다 이 결과를 각자 필요한 모양으로 투영해서 쓴다(로직
     * 중복 없이 공유). {@code extraction}이 null이면 분석 자체가 예외를 던진 것이다.
     */
    /** package-private: {@code toAnalysisOutcome} 매핑 로직을 ContentPersistenceService 없이
     * 단위테스트하려면 테스트 코드가 직접 만들 수 있어야 한다. */
    record Analyzed(
            YouTubeContentExtractionResultDto extraction,
            String analysisFailureMessage,
            List<PlaceOutcomeDto> outcomes
    ) {
    }

    private Analyzed analyze(SavePipelineRequestDto request) {
        YouTubeContentExtractionResultDto extraction;
        try {
            extraction = videoAnalyzer.analyze(request.youtubeUrl());
        } catch (RuntimeException e) {
            log.warn("영상 분석 실패: {}", e.getMessage());
            return new Analyzed(null, e.getMessage(), List.of());
        }

        // VideoContentAnalyzer는 지금 항상 SUCCESS만 반환하지만, 계약상 PARTIAL/FAILED도 가능하므로
        // 무시하지 않는다.
        if (extraction.status() != ExtractionStatus.SUCCESS) {
            String message = extraction.failureCode() != null
                    ? "영상 분석 실패: " + extraction.failureCode()
                    : "영상 분석 실패";
            return new Analyzed(extraction, message, List.of());
        }

        List<PlaceOutcomeDto> outcomes = extraction.placeCandidates().stream()
                .map(this::resolveCandidate)
                .toList();
        return new Analyzed(extraction, null, outcomes);
    }

    public SavePipelineResultDto save(SavePipelineRequestDto request) {
        return toResultDto(analyze(request));
    }

    /**
     * {@link #save}와 같은 분석·확정 과정을 거친 뒤, 실제로 {@code ContentAnalysisResultService.
     * applyOutcome}까지 호출해 DB에 반영한다(Place.md 담당자 합의로 막혀있던 연동, 2026-10-10 해소).
     * {@code ContentPersistenceService}를 직접 부르지 않고 {@code ContentAnalysisResultService}를
     * 거치는 이유: 이 서비스가 임베딩 생성까지 포함한 진짜 저장 진입점인데, 지금까지 프로덕션 어디에서도
     * 호출되지 않고 있었다(멘토 피드백 "모듈끼리 연결이 안 됨"의 실제 사례, 2026-10-11 발견·수정).
     * contentId는 {@code ContentPersistenceService.saveAndClaim}이 먼저 만들어둔 값을 그대로 받는다
     * ({@code SavePipelineController}에서 호출). {@link #save}와 분석을 두 번 돌리지 않도록 같은
     * {@link Analyzed}를 응답 투영({@link #toResultDto})에도 재사용한다.
     */
    public SavePipelineResultDto saveAndPersist(UUID contentId, SavePipelineRequestDto request) {
        Analyzed analyzed = analyze(request);
        AnalysisOutcome outcome = toAnalysisOutcome(analyzed);
        boolean applied = contentAnalysisResultService.applyOutcome(contentId, outcome);
        if (!applied) {
            log.warn("분석 결과 반영 실패(이미 처리됐거나 contentId 없음) contentId={}", contentId);
        }
        if (analyzed.extraction() != null && analyzed.extraction().status() == ExtractionStatus.SUCCESS) {
            saveConfirmCandidates(contentId, analyzed.extraction().placeCandidates(), analyzed.outcomes());
        }
        return toResultDto(analyzed);
    }

    /**
     * 되묻기(NEEDS_CONFIRMATION) 후보를 영속화한다 - {@code AnalysisOutcome}(RESOLVED 전용 계약)에
     * 안 얹고 별도 호출로 두는 이유: content/dto 패키지가 save 패키지를 몰라야 하는 레이어 경계도 있고,
     * AnalysisOutcome을 쓰는 기존 테스트/호출부(ContentPersistenceServiceTest 등)를 전부 건드리지
     * 않아도 되기 때문(ponytail: 최소 diff). {@code confirmRequest}(질문+후보 목록)는 JSON 그대로
     * 저장해뒀다가, 답변 제출({@code ConfirmAnswerService}) 또는 GET /api/personal-saves/{id} 쪽에서
     * 역직렬화해서 쓴다.
     */
    private void saveConfirmCandidates(UUID contentId, List<PlaceCandidateDto> candidates, List<PlaceOutcomeDto> outcomes) {
        Map<String, String> descriptionByCandidateId = candidates.stream()
                .collect(Collectors.toMap(PlaceCandidateDto::candidateId, PlaceCandidateDto::description));
        Instant now = clock.instant();
        outcomes.stream()
                .filter(o -> o.decision() == PlaceOutcomeDto.Decision.NEEDS_CONFIRMATION)
                .forEach(o -> {
                    String confirmRequestJson;
                    try {
                        confirmRequestJson = objectMapper.writeValueAsString(o.confirmRequest());
                    } catch (JsonProcessingException e) {
                        throw new IllegalStateException("되묻기 후보 직렬화 실패 candidateId=" + o.candidateId(), e);
                    }
                    confirmCandidateRepository.upsert(UUID.randomUUID(), contentId, o.candidateId(),
                            descriptionByCandidateId.get(o.candidateId()), confirmRequestJson, now);
                });
    }

    private static SavePipelineResultDto toResultDto(Analyzed analyzed) {
        if (analyzed.extraction() == null || analyzed.extraction().status() != ExtractionStatus.SUCCESS) {
            return new SavePipelineResultDto(ExtractionStatus.FAILED, null, List.of(), analyzed.analysisFailureMessage());
        }

        ExtractionStatus status = analyzed.outcomes().stream().anyMatch(SavePipelineService::isLookupFailure)
                ? ExtractionStatus.PARTIAL
                : ExtractionStatus.SUCCESS;

        return new SavePipelineResultDto(status, analyzed.extraction().summary(), analyzed.outcomes(), null);
    }

    /** package-private: 실제 DB 호출 없이 매핑 로직만 단위테스트하기 위함. */
    AnalysisOutcome toAnalysisOutcome(Analyzed analyzed) {
        if (analyzed.extraction() == null) {
            // videoAnalyzer.analyze() 자체가 예외를 던짐 - YouTube/Gemini 호출 실패로 간주한다.
            return new AnalysisOutcome(ContentAnalysisStatus.FAILED, ContentAnalysisFailureCode.EXTRACTION_API_ERROR,
                    null, null, null, null, null, clock.instant(), List.of());
        }

        YouTubeContentExtractionResultDto extraction = analyzed.extraction();
        String analysisVersion = extraction.analysisMetadata() == null
                ? null : extraction.analysisMetadata().analysisVersion();

        if (extraction.status() != ExtractionStatus.SUCCESS) {
            ExtractionFailureCode extractionFailureCode = extraction.failureCode();
            ContentAnalysisFailureCode failureCode = extractionFailureCode == null
                    ? ContentAnalysisFailureCode.EXTRACTION_RESULT_ERROR
                    : ContentAnalysisFailureCode.valueOf(extractionFailureCode.name());
            ContentSourceStatus sourceStatus = failureCode == ContentAnalysisFailureCode.VIDEO_UNAVAILABLE
                    ? ContentSourceStatus.UNAVAILABLE : null;
            return new AnalysisOutcome(ContentAnalysisStatus.FAILED, failureCode, sourceStatus,
                    extraction.title(), null, null, analysisVersion, clock.instant(), List.of());
        }

        List<AnalysisOutcome.PlaceResult> places = toPlaceResults(extraction.placeCandidates(), analyzed.outcomes());
        boolean anyLookupFailure = analyzed.outcomes().stream().anyMatch(SavePipelineService::isLookupFailure);
        ContentAnalysisStatus status = anyLookupFailure ? ContentAnalysisStatus.PARTIAL : ContentAnalysisStatus.SUCCESS;
        ContentAnalysisFailureCode failureCode = anyLookupFailure ? dominantLookupFailureCode(analyzed.outcomes()) : null;

        return new AnalysisOutcome(status, failureCode, null,
                extraction.title(), extraction.summary(), extraction.category(), analysisVersion, clock.instant(), places);
    }

    /**
     * 장소 검색 실패와 검증 실패가 섞여 나올 수 있는데 콘텐츠 단위 실패 사유는 하나만 골라야 한다 -
     * 검색 실패를 더 근본 원인으로 보고 우선한다(ponytail: 단순 우선순위, 더 정교한 집계가 필요해지면
     * 그때 바꾼다).
     */
    private static ContentAnalysisFailureCode dominantLookupFailureCode(List<PlaceOutcomeDto> outcomes) {
        boolean anySearchFailed = outcomes.stream()
                .anyMatch(o -> o.decision() == PlaceOutcomeDto.Decision.PLACE_SEARCH_FAILED);
        return anySearchFailed
                ? ContentAnalysisFailureCode.PLACE_SEARCH_API_ERROR
                : ContentAnalysisFailureCode.PLACE_VERIFICATION_API_ERROR;
    }

    /**
     * RESOLVED로 확정된 후보만 Place로 변환한다 - 되묻기·실패는 Place.md 생성 흐름 문서("결과가
     * 모호하거나 일치하지 않으면 Place를 생성하지 않고 실패 기록")와 같은 원칙으로 저장하지 않는다.
     * description은 PlaceOutcomeDto/ResolvedPlaceDto엔 없고 원본 PlaceCandidateDto에만 있어서
     * candidateId로 다시 찾아 붙인다.
     */
    private static List<AnalysisOutcome.PlaceResult> toPlaceResults(
            List<PlaceCandidateDto> candidates, List<PlaceOutcomeDto> outcomes) {
        Map<String, String> descriptionByCandidateId = candidates.stream()
                .collect(Collectors.toMap(PlaceCandidateDto::candidateId, PlaceCandidateDto::description));

        return outcomes.stream()
                .filter(o -> o.decision() == PlaceOutcomeDto.Decision.RESOLVED)
                .map(o -> {
                    ResolvedPlaceDto place = o.resolvedPlace();
                    return new AnalysisOutcome.PlaceResult(
                            descriptionByCandidateId.get(o.candidateId()),
                            place.name(), place.address(), place.lat(), place.lng(),
                            place.geocodingProvider(), place.geocodingPlaceId(), place.verificationPlaceId());
                })
                .toList();
    }

    private static boolean isLookupFailure(PlaceOutcomeDto outcome) {
        return outcome.decision() == PlaceOutcomeDto.Decision.PLACE_SEARCH_FAILED
                || outcome.decision() == PlaceOutcomeDto.Decision.PLACE_VERIFICATION_FAILED;
    }

    private PlaceOutcomeDto resolveCandidate(PlaceCandidateDto candidate) {
        List<PlaceSearchClient.PlaceSearchResult> searchResults;
        try {
            searchResults = placeLookup.search(candidate.name(), candidate.branchName(), candidate.regionHint());
        } catch (RuntimeException e) {
            log.warn("장소 검색 실패 candidateId={}: {}", candidate.candidateId(), e.getMessage());
            return new PlaceOutcomeDto(candidate.candidateId(), PlaceOutcomeDto.Decision.PLACE_SEARCH_FAILED, null, null);
        }

        PlaceResolver.Result result;
        try {
            result = placeResolver.resolve(candidate.candidateId(), candidate.name(), candidate.branchName(),
                    candidate.regionHint(), searchResults);
        } catch (RuntimeException e) {
            // 방어적 처리: PlaceResolver는 주입받은 lookup 예외를 내부에서 전부 삼키므로 실제로는
            // 여기 안 걸릴 가능성이 높다 - 그 전제가 깨지는 순간 파이프라인 전체가 죽는 대신 이 후보
            // 하나만 실패로 표시하도록 경계를 둔다.
            log.warn("장소 확정 실패 candidateId={}: {}", candidate.candidateId(), e.getMessage());
            return new PlaceOutcomeDto(candidate.candidateId(), PlaceOutcomeDto.Decision.PLACE_VERIFICATION_FAILED, null, null);
        }

        return switch (result.decision()) {
            case RESOLVED -> verifyWithKakao(candidate.candidateId(), result.resolvedPlace());
            case NO_PLACE -> new PlaceOutcomeDto(
                    candidate.candidateId(), PlaceOutcomeDto.Decision.NO_PLACE, null, null);
            case NEEDS_CONFIRMATION -> new PlaceOutcomeDto(
                    candidate.candidateId(), PlaceOutcomeDto.Decision.NEEDS_CONFIRMATION, null, result.confirmRequest());
        };
    }

    /**
     * PlaceResolver가 확정한 좌표를 카카오 장소 ID로 한 번 더 검증한다(Place.md 담당자 합의 반영,
     * 2026-10-10) - "같은 장소인지" 판단 기준이 카카오 장소 ID라서다. 못 찾으면 이 후보를 RESOLVED로
     * 내보내지 않고 PLACE_VERIFICATION_FAILED로 처리한다(Place.md: "결과가 모호하거나 일치하지 않으면
     * Place를 생성하지 않고 실패 기록"과 같은 원칙).
     */
    private PlaceOutcomeDto verifyWithKakao(String candidateId, ResolvedPlaceDto place) {
        Optional<String> verificationPlaceId;
        try {
            verificationPlaceId = kakaoPlaceLookup.findPlaceId(place.name(), place.branchName(), place.lat(), place.lng());
        } catch (RuntimeException e) {
            log.warn("카카오 장소 검증 실패 candidateId={}: {}", candidateId, e.getMessage());
            return new PlaceOutcomeDto(candidateId, PlaceOutcomeDto.Decision.PLACE_VERIFICATION_FAILED, null, null);
        }
        if (verificationPlaceId.isEmpty()) {
            return new PlaceOutcomeDto(candidateId, PlaceOutcomeDto.Decision.NO_PLACE, null, null);
        }

        ResolvedPlaceDto verified = new ResolvedPlaceDto(
                place.candidateId(), place.name(), place.branchName(), place.address(), place.lat(), place.lng(),
                place.geocodingProvider(), place.geocodingPlaceId(), verificationPlaceId.get());
        return new PlaceOutcomeDto(candidateId, PlaceOutcomeDto.Decision.RESOLVED, verified, null);
    }
}
