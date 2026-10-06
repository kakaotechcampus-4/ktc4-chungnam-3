package com.ktc.chungnam3.remembrall.save;

import com.ktc.chungnam3.remembrall.extraction.dto.PlaceCandidateDto;
import com.ktc.chungnam3.remembrall.extraction.dto.YouTubeContentExtractionResultDto;
import com.ktc.chungnam3.remembrall.extraction.type.ExtractionStatus;
import com.ktc.chungnam3.remembrall.save.analysis.VideoContentAnalyzer;
import com.ktc.chungnam3.remembrall.save.confirm.ConfirmPolicy;
import com.ktc.chungnam3.remembrall.save.dto.PlaceOutcomeDto;
import com.ktc.chungnam3.remembrall.save.dto.ResolvedPlaceDto;
import com.ktc.chungnam3.remembrall.save.dto.SavePipelineRequestDto;
import com.ktc.chungnam3.remembrall.save.dto.SavePipelineResultDto;
import com.ktc.chungnam3.remembrall.save.place.PlaceLookup;
import com.ktc.chungnam3.remembrall.save.place.PlaceResolver;
import com.ktc.chungnam3.remembrall.save.place.PlaceSearchClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 저장 파이프라인 3단계(전체 조립) - 유튜브 URL 하나를 받아 영상 분석(Module A) → 장소 검색·확정
 * (Module B) → 되묻기 판정까지 묶어서 {@link SavePipelineResultDto}를 만든다.
 * <p>
 * <b>이번 구현 범위</b>: 이 클래스는 순수 로직까지만 담당한다. REST 계층(SavePipelineController)과
 * {@code ContentPersistenceService.applyOutcome}/{@code AnalysisOutcome} 영속화 연동은 별도 작업으로
 * 분리했다 - {@code AnalysisOutcome.PlaceResult}가 geocodingPlaceId/verificationPlaceId를 필수로
 * 요구하고 저장 로직이 VerificationProvider.KAKAO로 고정돼 있는데, 이 파이프라인(LocationIQ+공공
 * 상가정보+VWorld)은 그런 필드도 카카오도 안 쓴다 - Place.md 스키마 충돌로 3자 합의 대기 중인 사안이라
 * 조용히 우회하지 않는다(2026-10-06 계획 확정).
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

    /** Spring이 실제로 쓰는 생성자 - VideoContentAnalyzer(Module A 소유)는 안 건드리고 메서드 참조로
     * {@link VideoAnalyzer}에 연결한다. */
    @Autowired
    public SavePipelineService(VideoContentAnalyzer videoContentAnalyzer, PlaceLookup placeLookup,
                                PlaceResolver placeResolver, ConfirmPolicy confirmPolicy) {
        this(videoContentAnalyzer::analyze, placeLookup, placeResolver, confirmPolicy);
    }

    /** 테스트용 생성자 - 전부 람다로 fake를 넣는다(PlaceResolverTest와 같은 팀 관행). */
    SavePipelineService(VideoAnalyzer videoAnalyzer, PlaceLookup placeLookup,
                         PlaceResolver placeResolver, ConfirmPolicy confirmPolicy) {
        this.videoAnalyzer = videoAnalyzer;
        this.placeLookup = placeLookup;
        this.placeResolver = placeResolver;
        this.confirmPolicy = confirmPolicy;
    }

    public SavePipelineResultDto save(SavePipelineRequestDto request) {
        YouTubeContentExtractionResultDto extraction;
        try {
            extraction = videoAnalyzer.analyze(request.youtubeUrl());
        } catch (RuntimeException e) {
            log.warn("영상 분석 실패: {}", e.getMessage());
            return new SavePipelineResultDto(ExtractionStatus.FAILED, null, List.of(), e.getMessage());
        }

        // VideoContentAnalyzer는 지금 항상 SUCCESS만 반환하지만, 계약상 PARTIAL/FAILED도 가능하므로
        // 무시하지 않는다.
        if (extraction.status() != ExtractionStatus.SUCCESS) {
            String message = extraction.failure() != null ? extraction.failure().message() : "영상 분석 실패";
            return new SavePipelineResultDto(ExtractionStatus.FAILED, null, List.of(), message);
        }

        List<PlaceOutcomeDto> outcomes = extraction.placeCandidates().stream()
                .map(candidate -> resolveCandidate(candidate, request.alreadyAsked()))
                .toList();

        ExtractionStatus status = outcomes.stream().anyMatch(SavePipelineService::isLookupFailure)
                ? ExtractionStatus.PARTIAL
                : ExtractionStatus.SUCCESS;

        return new SavePipelineResultDto(status, extraction.summary(), outcomes, null);
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
            case RESOLVED -> new PlaceOutcomeDto(
                    candidate.candidateId(), PlaceOutcomeDto.Decision.RESOLVED, result.resolvedPlace(), null);
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
        // ConfirmPolicy 클래스 javadoc의 TODO: 이미 물어봤는데도 못 좁혔으면 다시 묻지 않고 "상위 지역"
        // 좌표로 저장한다.
        return resolveBroaderRegion(candidate)
                .map(place -> new PlaceOutcomeDto(candidate.candidateId(), PlaceOutcomeDto.Decision.RESOLVED, place, null))
                .orElseGet(() -> new PlaceOutcomeDto(candidate.candidateId(), PlaceOutcomeDto.Decision.NO_PLACE, null, null));
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
                        r.displayName(), r.lat(), r.lon()));
    }
}
