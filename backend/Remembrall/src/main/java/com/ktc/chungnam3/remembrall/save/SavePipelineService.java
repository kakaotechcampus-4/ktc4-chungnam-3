package com.ktc.chungnam3.remembrall.save;

import com.ktc.chungnam3.remembrall.content.dto.AnalysisOutcome;
import com.ktc.chungnam3.remembrall.content.service.ContentPersistenceService;
import com.ktc.chungnam3.remembrall.domain.content.ContentAnalysisFailureCode;
import com.ktc.chungnam3.remembrall.domain.content.ContentAnalysisStatus;
import com.ktc.chungnam3.remembrall.domain.content.ContentSourceStatus;
import com.ktc.chungnam3.remembrall.domain.place.GeocodingProvider;
import com.ktc.chungnam3.remembrall.extraction.dto.PlaceCandidateDto;
import com.ktc.chungnam3.remembrall.extraction.dto.YouTubeContentExtractionResultDto;
import com.ktc.chungnam3.remembrall.extraction.type.ExtractionFailureCode;
import com.ktc.chungnam3.remembrall.extraction.type.ExtractionStatus;
import com.ktc.chungnam3.remembrall.save.analysis.VideoContentAnalyzer;
import com.ktc.chungnam3.remembrall.save.confirm.ConfirmPolicy;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 저장 파이프라인 3단계(전체 조립) - 유튜브 URL 하나를 받아 영상 분석(Module A) → 장소 검색·확정
 * (Module B) → 카카오 장소 검증 → 되묻기 판정까지 묶어서 {@link SavePipelineResultDto}를 만든다.
 * <p>
 * <b>이번 구현 범위</b>: 이 클래스는 순수 로직까지만 담당한다. REST 계층(SavePipelineController)과
 * {@code ContentPersistenceService.applyOutcome} 영속화 연동은 별도 작업으로 분리했다 - 과거엔
 * {@code AnalysisOutcome.PlaceResult}가 요구하는 geocodingPlaceId/verificationPlaceId를 이 파이프라인이
 * 채울 방법이 없어 막혀 있었는데(Place.md 스키마 충돌), 2026-10-10 Place.md 담당자 합의로
 * geocoding_provider에 DATAPORTAL이 추가되고 카카오 장소 검증까지 이 클래스에 연결되면서 해소됐다 -
 * 남은 건 REST 진입점과 실제 applyOutcome 호출 배선뿐이다.
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
    private final ConfirmPolicy confirmPolicy;
    private final KakaoPlaceLookup kakaoPlaceLookup;
    private final ContentPersistenceService contentPersistenceService;
    private final Clock clock;

    /** Spring이 실제로 쓰는 생성자 - VideoContentAnalyzer(Module A 소유)는 안 건드리고 메서드 참조로
     * {@link VideoAnalyzer}에 연결한다. */
    @Autowired
    public SavePipelineService(VideoContentAnalyzer videoContentAnalyzer, PlaceLookup placeLookup,
                                PlaceResolver placeResolver, ConfirmPolicy confirmPolicy,
                                KakaoPlaceLookup kakaoPlaceLookup,
                                ContentPersistenceService contentPersistenceService, Clock clock) {
        this(videoContentAnalyzer::analyze, placeLookup, placeResolver, confirmPolicy, kakaoPlaceLookup,
                contentPersistenceService, clock);
    }

    /**
     * 순수 로직만 검증하는 기존 테스트용 생성자(5-인자, 시그니처 그대로 유지) - {@code saveAndPersist}를
     * 쓰지 않는 테스트는 {@code contentPersistenceService}/{@code clock}이 필요 없어서 이 생성자로
     * 충분하다(team 관행: 람다 fake, 모킹 프레임워크 안 씀).
     */
    SavePipelineService(VideoAnalyzer videoAnalyzer, PlaceLookup placeLookup,
                         PlaceResolver placeResolver, ConfirmPolicy confirmPolicy,
                         KakaoPlaceLookup kakaoPlaceLookup) {
        this(videoAnalyzer, placeLookup, placeResolver, confirmPolicy, kakaoPlaceLookup, null, Clock.systemUTC());
    }

    /** {@code saveAndPersist}까지 테스트하고 싶을 때 쓰는 전체 생성자. */
    SavePipelineService(VideoAnalyzer videoAnalyzer, PlaceLookup placeLookup,
                         PlaceResolver placeResolver, ConfirmPolicy confirmPolicy,
                         KakaoPlaceLookup kakaoPlaceLookup,
                         ContentPersistenceService contentPersistenceService, Clock clock) {
        this.videoAnalyzer = videoAnalyzer;
        this.placeLookup = placeLookup;
        this.placeResolver = placeResolver;
        this.confirmPolicy = confirmPolicy;
        this.kakaoPlaceLookup = kakaoPlaceLookup;
        this.contentPersistenceService = contentPersistenceService;
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
                .map(candidate -> resolveCandidate(candidate, request.alreadyAsked()))
                .toList();
        return new Analyzed(extraction, null, outcomes);
    }

    public SavePipelineResultDto save(SavePipelineRequestDto request) {
        Analyzed analyzed = analyze(request);
        if (analyzed.extraction() == null || analyzed.extraction().status() != ExtractionStatus.SUCCESS) {
            return new SavePipelineResultDto(ExtractionStatus.FAILED, null, List.of(), analyzed.analysisFailureMessage());
        }

        ExtractionStatus status = analyzed.outcomes().stream().anyMatch(SavePipelineService::isLookupFailure)
                ? ExtractionStatus.PARTIAL
                : ExtractionStatus.SUCCESS;

        return new SavePipelineResultDto(status, analyzed.extraction().summary(), analyzed.outcomes(), null);
    }

    /**
     * {@link #save}와 같은 분석·확정 과정을 거친 뒤, 실제로 {@code ContentPersistenceService.
     * applyOutcome}까지 호출해 DB에 반영한다(Place.md 담당자 합의로 막혀있던 연동, 2026-10-10 해소).
     * REST 진입점(SavePipelineController)이 아직 없어 당장은 직접 호출로만 쓸 수 있다 - contentId는
     * {@code ContentPersistenceService.saveAndClaim}이 먼저 만들어둔 값을 그대로 받는다.
     */
    public boolean saveAndPersist(UUID contentId, SavePipelineRequestDto request) {
        Analyzed analyzed = analyze(request);
        AnalysisOutcome outcome = toAnalysisOutcome(analyzed);
        return contentPersistenceService.applyOutcome(contentId, outcome);
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
                extraction.title(), extraction.summary(), null, analysisVersion, clock.instant(), places);
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

    private PlaceOutcomeDto resolveCandidate(PlaceCandidateDto candidate, boolean alreadyAsked) {
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
            case NEEDS_CONFIRMATION -> resolveConfirmation(candidate, result, alreadyAsked);
        };
    }

    private PlaceOutcomeDto resolveConfirmation(PlaceCandidateDto candidate, PlaceResolver.Result result,
                                                 boolean alreadyAsked) {
        if (confirmPolicy.shouldAsk(alreadyAsked)) {
            return new PlaceOutcomeDto(
                    candidate.candidateId(), PlaceOutcomeDto.Decision.NEEDS_CONFIRMATION, null, result.confirmRequest());
        }
        // 이미 물어봤는데도 못 좁혔으면 다시 묻지 않고 "상위 지역" 좌표로 저장한다
        // (ConfirmPolicy 클래스 javadoc 참고).
        return resolveBroaderRegion(candidate)
                .map(place -> verifyWithKakao(candidate.candidateId(), place))
                .orElseGet(() -> new PlaceOutcomeDto(candidate.candidateId(), PlaceOutcomeDto.Decision.NO_PLACE, null, null));
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
            return new PlaceOutcomeDto(candidateId, PlaceOutcomeDto.Decision.PLACE_VERIFICATION_FAILED, null, null);
        }

        ResolvedPlaceDto verified = new ResolvedPlaceDto(
                place.candidateId(), place.name(), place.branchName(), place.address(), place.lat(), place.lng(),
                place.geocodingProvider(), place.geocodingPlaceId(), verificationPlaceId.get());
        return new PlaceOutcomeDto(candidateId, PlaceOutcomeDto.Decision.RESOLVED, verified, null);
    }

    /**
     * PlaceResolver의 기준점 로직(resolveViaAnchor류)은 regionHint에서 시·도를 "제거"하고 남은 구·동
     * 단어로 더 정밀한 기준점(프랜차이즈 지점 찾기용)을 구하는 방향이라, 여기서 원하는 것(시·도 단위의
     * "상위 지역" 좌표, 오히려 더 거칠어도 됨)과 방향이 반대다 - 억지로 재사용하면 로직을 반전시켜야
     * 해서 오히려 더 얽힌다. 그래서 regionHint 텍스트 그대로 PlaceSearchClient에 물어보고 1순위
     * 결과를 쓴다 - class/지역 필터 없이 거친 최후 수단이다(이미 한 번 되묻기까지 갔는데도 응답이
     * 없었던 경우에만 타는 경로라 PlaceResolver 수준의 정밀도는 필요 없다고 보고 단순하게 짰다. 너무
     * 거칠다고 판명되면 그때 필터를 추가한다).
     */
    private Optional<ResolvedPlaceDto> resolveBroaderRegion(PlaceCandidateDto candidate) {
        if (candidate.regionHint() == null || candidate.regionHint().isBlank()) {
            return Optional.empty();
        }
        List<PlaceSearchClient.PlaceSearchResult> results;
        try {
            results = placeLookup.search(candidate.regionHint(), null, candidate.regionHint());
        } catch (RuntimeException e) {
            return Optional.empty();
        }
        return results.stream().findFirst()
                .map(r -> new ResolvedPlaceDto(
                        candidate.candidateId(), candidate.name(), candidate.branchName(),
                        r.displayName(), r.lat(), r.lon(),
                        GeocodingProvider.LOCATIONIQ, r.placeId(), null));
    }
}
