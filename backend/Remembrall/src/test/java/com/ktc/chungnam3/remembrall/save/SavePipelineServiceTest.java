package com.ktc.chungnam3.remembrall.save;

import com.ktc.chungnam3.remembrall.content.dto.AnalysisOutcome;
import com.ktc.chungnam3.remembrall.domain.content.ContentAnalysisFailureCode;
import com.ktc.chungnam3.remembrall.domain.content.ContentAnalysisStatus;
import com.ktc.chungnam3.remembrall.domain.content.ContentSourceStatus;
import com.ktc.chungnam3.remembrall.domain.place.GeocodingProvider;
import com.ktc.chungnam3.remembrall.extraction.dto.AnalysisMetadataDto;
import com.ktc.chungnam3.remembrall.extraction.dto.PlaceCandidateDto;
import com.ktc.chungnam3.remembrall.extraction.dto.YouTubeContentExtractionResultDto;
import com.ktc.chungnam3.remembrall.extraction.type.ExtractionFailureCode;
import com.ktc.chungnam3.remembrall.extraction.type.ExtractionStatus;
import com.ktc.chungnam3.remembrall.save.confirm.ConfirmPolicy;
import com.ktc.chungnam3.remembrall.save.dto.PlaceOutcomeDto;
import com.ktc.chungnam3.remembrall.save.dto.ResolvedPlaceDto;
import com.ktc.chungnam3.remembrall.save.dto.SavePipelineRequestDto;
import com.ktc.chungnam3.remembrall.save.dto.SavePipelineResultDto;
import com.ktc.chungnam3.remembrall.save.place.DataportalStoreClient;
import com.ktc.chungnam3.remembrall.save.place.KakaoPlaceLookup;
import com.ktc.chungnam3.remembrall.save.place.PlaceLookup;
import com.ktc.chungnam3.remembrall.save.place.PlaceResolver;
import com.ktc.chungnam3.remembrall.save.place.PlaceSearchClient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SavePipelineServiceTest {

    private static PlaceCandidateDto candidate(String candidateId, String name, String branchName, String regionHint) {
        return new PlaceCandidateDto(candidateId, name, branchName, regionHint, "설명", null);
    }

    private static PlaceSearchClient.PlaceSearchResult result(String displayName, double lat, double lon) {
        return new PlaceSearchClient.PlaceSearchResult(displayName, lat, lon, null, null, "");
    }

    private static YouTubeContentExtractionResultDto extraction(List<PlaceCandidateDto> candidates) {
        return new YouTubeContentExtractionResultDto(
                ExtractionStatus.SUCCESS, new AnalysisMetadataDto("v1", "model", "v1"),
                null, "요약", null, candidates, List.of(), null);
    }

    /** name→검색결과를 그대로 매핑하는 PlaceLookup fake. 지정 안 된 이름은 빈 리스트. */
    private static PlaceLookup lookupOf(java.util.Map<String, List<PlaceSearchClient.PlaceSearchResult>> byName) {
        return (name, branch, region) -> byName.getOrDefault(name, List.of());
    }

    private static PlaceResolver realResolver() {
        return new PlaceResolver(
                (lon, lat, radiusMeters) -> List.of(),
                (name, branch, region) -> List.of(),
                query -> Optional.empty(),
                1000);
    }

    /** 대부분의 테스트는 카카오 검증 자체가 관심사가 아니므로, 항상 ID를 찾은 것으로 고정한다. */
    private static KakaoPlaceLookup kakaoAlwaysFound() {
        return (name, branch, lat, lon) -> Optional.of("kakao-test-id");
    }

    @Test
    void 장소후보가_없으면_SUCCESS와_빈_리스트를_반환한다() {
        VideoAnalyzer analyzer = url -> extraction(List.of());
        SavePipelineService service = new SavePipelineService(analyzer, lookupOf(java.util.Map.of()),
                realResolver(), new ConfirmPolicy(), kakaoAlwaysFound());

        SavePipelineResultDto resolved = service.save(new SavePipelineRequestDto("https://youtu.be/x", false));

        assertThat(resolved.status()).isEqualTo(ExtractionStatus.SUCCESS);
        assertThat(resolved.places()).isEmpty();
    }

    @Test
    void 장소후보_하나가_검색결과_한건으로_RESOLVED되면_그대로_반환한다() {
        var place = candidate("p1", "성심당", "본점", null);
        VideoAnalyzer analyzer = url -> extraction(List.of(place));
        PlaceLookup lookup = lookupOf(java.util.Map.of("성심당", List.of(result("성심당 본점, 대전", 36.32, 127.42))));
        SavePipelineService service = new SavePipelineService(analyzer, lookup, realResolver(), new ConfirmPolicy(), kakaoAlwaysFound());

        SavePipelineResultDto resolved = service.save(new SavePipelineRequestDto("https://youtu.be/x", false));

        assertThat(resolved.status()).isEqualTo(ExtractionStatus.SUCCESS);
        assertThat(resolved.places()).hasSize(1);
        assertThat(resolved.places().get(0).decision()).isEqualTo(PlaceOutcomeDto.Decision.RESOLVED);
        assertThat(resolved.places().get(0).resolvedPlace().address()).isEqualTo("성심당 본점, 대전");
    }

    @Test
    void 장소후보_여러_개는_각각_독립적으로_판정된다() {
        var resolvedCandidate = candidate("p1", "성심당", null, null);
        var confirmCandidate = candidate("p2", "경복궁", null, null);
        var noPlaceCandidate = candidate("p3", "존재안하는가게", null, null);
        VideoAnalyzer analyzer = url -> extraction(List.of(resolvedCandidate, confirmCandidate, noPlaceCandidate));
        PlaceLookup lookup = lookupOf(java.util.Map.of(
                "성심당", List.of(result("성심당 본점, 대전", 36.32, 127.42)),
                "경복궁", List.of(result("경복궁, 서울", 37.57, 126.97), result("경복궁역, 서울", 37.58, 126.98)),
                "존재안하는가게", List.of()
        ));
        SavePipelineService service = new SavePipelineService(analyzer, lookup, realResolver(), new ConfirmPolicy(), kakaoAlwaysFound());

        SavePipelineResultDto resolved = service.save(new SavePipelineRequestDto("https://youtu.be/x", false));

        assertThat(resolved.places()).hasSize(3);
        assertThat(resolved.places().get(0).decision()).isEqualTo(PlaceOutcomeDto.Decision.RESOLVED);
        assertThat(resolved.places().get(1).decision()).isEqualTo(PlaceOutcomeDto.Decision.NEEDS_CONFIRMATION);
        assertThat(resolved.places().get(2).decision()).isEqualTo(PlaceOutcomeDto.Decision.NO_PLACE);
    }

    @Test
    void 영상_URL이_잘못되면_FAILED를_반환하고_장소검색은_시도하지_않는다() {
        VideoAnalyzer analyzer = url -> {
            throw new IllegalArgumentException("URL에서 videoId를 추출할 수 없습니다: " + url);
        };
        PlaceLookup lookup = (name, branch, region) -> {
            throw new AssertionError("장소 검색이 호출되면 안 됨");
        };
        SavePipelineService service = new SavePipelineService(analyzer, lookup, realResolver(), new ConfirmPolicy(), kakaoAlwaysFound());

        SavePipelineResultDto resolved = service.save(new SavePipelineRequestDto("not-a-url", false));

        assertThat(resolved.status()).isEqualTo(ExtractionStatus.FAILED);
        assertThat(resolved.places()).isEmpty();
    }

    @Test
    void 영상_분석_자체가_예외를_던지면_FAILED를_반환한다() {
        VideoAnalyzer analyzer = url -> {
            throw new IllegalStateException("Gemini 응답 파싱 실패");
        };
        SavePipelineService service = new SavePipelineService(analyzer, lookupOf(java.util.Map.of()),
                realResolver(), new ConfirmPolicy(), kakaoAlwaysFound());

        SavePipelineResultDto resolved = service.save(new SavePipelineRequestDto("https://youtu.be/x", false));

        assertThat(resolved.status()).isEqualTo(ExtractionStatus.FAILED);
        assertThat(resolved.failureMessage()).contains("Gemini");
    }

    @Test
    void 영상_분석_상태가_SUCCESS가_아니면_FAILED로_처리한다() {
        YouTubeContentExtractionResultDto partial = new YouTubeContentExtractionResultDto(
                ExtractionStatus.FAILED, new AnalysisMetadataDto("v1", "model", "v1"),
                null, null, null, List.of(), List.of(), ExtractionFailureCode.VIDEO_UNAVAILABLE);
        VideoAnalyzer analyzer = url -> partial;
        SavePipelineService service = new SavePipelineService(analyzer, lookupOf(java.util.Map.of()),
                realResolver(), new ConfirmPolicy(), kakaoAlwaysFound());

        SavePipelineResultDto resolved = service.save(new SavePipelineRequestDto("https://youtu.be/x", false));

        assertThat(resolved.status()).isEqualTo(ExtractionStatus.FAILED);
        assertThat(resolved.failureMessage()).contains("VIDEO_UNAVAILABLE");
    }

    @Test
    void 장소검색_API가_실패한_후보는_PLACE_SEARCH_FAILED로_표시되고_나머지는_정상_판정된다() {
        var failingCandidate = candidate("p1", "실패가게", null, null);
        var okCandidate = candidate("p2", "성심당", null, null);
        VideoAnalyzer analyzer = url -> extraction(List.of(failingCandidate, okCandidate));
        PlaceLookup lookup = (name, branch, region) -> {
            if ("실패가게".equals(name)) {
                throw new RuntimeException("LocationIQ 타임아웃");
            }
            return List.of(result("성심당 본점, 대전", 36.32, 127.42));
        };
        SavePipelineService service = new SavePipelineService(analyzer, lookup, realResolver(), new ConfirmPolicy(), kakaoAlwaysFound());

        SavePipelineResultDto resolved = service.save(new SavePipelineRequestDto("https://youtu.be/x", false));

        assertThat(resolved.places().get(0).decision()).isEqualTo(PlaceOutcomeDto.Decision.PLACE_SEARCH_FAILED);
        assertThat(resolved.places().get(1).decision()).isEqualTo(PlaceOutcomeDto.Decision.RESOLVED);
    }

    @Test
    void 후보_하나라도_검색_실패하면_전체_status는_PARTIAL이_된다() {
        var failingCandidate = candidate("p1", "실패가게", null, null);
        VideoAnalyzer analyzer = url -> extraction(List.of(failingCandidate));
        PlaceLookup lookup = (name, branch, region) -> {
            throw new RuntimeException("LocationIQ 타임아웃");
        };
        SavePipelineService service = new SavePipelineService(analyzer, lookup, realResolver(), new ConfirmPolicy(), kakaoAlwaysFound());

        SavePipelineResultDto resolved = service.save(new SavePipelineRequestDto("https://youtu.be/x", false));

        assertThat(resolved.status()).isEqualTo(ExtractionStatus.PARTIAL);
    }

    @Test
    void 아직_안_물어봤으면_NEEDS_CONFIRMATION으로_되묻는다() {
        var place = candidate("p1", "경복궁", null, null);
        VideoAnalyzer analyzer = url -> extraction(List.of(place));
        PlaceLookup lookup = lookupOf(java.util.Map.of(
                "경복궁", List.of(result("경복궁, 서울", 37.57, 126.97), result("경복궁역, 서울", 37.58, 126.98))));
        SavePipelineService service = new SavePipelineService(analyzer, lookup, realResolver(), new ConfirmPolicy(), kakaoAlwaysFound());

        SavePipelineResultDto resolved = service.save(new SavePipelineRequestDto("https://youtu.be/x", false));

        assertThat(resolved.places().get(0).decision()).isEqualTo(PlaceOutcomeDto.Decision.NEEDS_CONFIRMATION);
        assertThat(resolved.places().get(0).confirmRequest()).isNotNull();
    }

    @Test
    void 이미_물어봤고_지역_검색이_성공하면_상위_지역_좌표로_RESOLVED된다() {
        var place = candidate("p1", "경복궁", null, "서울 종로");
        VideoAnalyzer analyzer = url -> extraction(List.of(place));
        PlaceLookup lookup = lookupOf(java.util.Map.of(
                "경복궁", List.of(result("경복궁, 서울", 37.57, 126.97), result("경복궁역, 서울", 37.58, 126.98)),
                "서울 종로", List.of(result("종로구, 서울특별시", 37.57, 126.98))));
        SavePipelineService service = new SavePipelineService(analyzer, lookup, realResolver(), new ConfirmPolicy(), kakaoAlwaysFound());

        SavePipelineResultDto resolved = service.save(new SavePipelineRequestDto("https://youtu.be/x", true));

        assertThat(resolved.places().get(0).decision()).isEqualTo(PlaceOutcomeDto.Decision.RESOLVED);
        assertThat(resolved.places().get(0).resolvedPlace().address()).isEqualTo("종로구, 서울특별시");
    }

    @Test
    void 이미_물어봤는데_regionHint가_없으면_NO_PLACE로_포기한다() {
        var place = candidate("p1", "경복궁", null, null);
        VideoAnalyzer analyzer = url -> extraction(List.of(place));
        PlaceLookup lookup = lookupOf(java.util.Map.of(
                "경복궁", List.of(result("경복궁, 서울", 37.57, 126.97), result("경복궁역, 서울", 37.58, 126.98))));
        SavePipelineService service = new SavePipelineService(analyzer, lookup, realResolver(), new ConfirmPolicy(), kakaoAlwaysFound());

        SavePipelineResultDto resolved = service.save(new SavePipelineRequestDto("https://youtu.be/x", true));

        assertThat(resolved.places().get(0).decision()).isEqualTo(PlaceOutcomeDto.Decision.NO_PLACE);
    }

    @Test
    void 카카오_장소_ID를_못_찾으면_RESOLVED_대신_PLACE_VERIFICATION_FAILED로_처리한다() {
        // Place.md: "결과가 모호하거나 일치하지 않으면 Place를 생성하지 않고 실패 기록" - PlaceResolver가
        // 확정해도 카카오 검증이 안 되면 RESOLVED로 내보내면 안 된다.
        var place = candidate("p1", "성심당", "본점", null);
        VideoAnalyzer analyzer = url -> extraction(List.of(place));
        PlaceLookup lookup = lookupOf(java.util.Map.of("성심당", List.of(result("성심당 본점, 대전", 36.32, 127.42))));
        KakaoPlaceLookup kakaoNotFound = (name, branch, lat, lon) -> Optional.empty();
        SavePipelineService service = new SavePipelineService(analyzer, lookup, realResolver(), new ConfirmPolicy(), kakaoNotFound);

        SavePipelineResultDto resolved = service.save(new SavePipelineRequestDto("https://youtu.be/x", false));

        assertThat(resolved.places().get(0).decision()).isEqualTo(PlaceOutcomeDto.Decision.PLACE_VERIFICATION_FAILED);
        assertThat(resolved.status()).isEqualTo(ExtractionStatus.PARTIAL);
    }

    private static SavePipelineService serviceForMapping() {
        return new SavePipelineService(url -> extraction(List.of()), lookupOf(java.util.Map.of()),
                realResolver(), new ConfirmPolicy(), kakaoAlwaysFound());
    }

    @Test
    void toAnalysisOutcome_분석자체가_실패하면_EXTRACTION_API_ERROR로_FAILED를_만든다() {
        SavePipelineService service = serviceForMapping();
        SavePipelineService.Analyzed analyzed = new SavePipelineService.Analyzed(null, "분석 실패", List.of());

        AnalysisOutcome outcome = service.toAnalysisOutcome(analyzed);

        assertThat(outcome.status()).isEqualTo(ContentAnalysisStatus.FAILED);
        assertThat(outcome.failureCode()).isEqualTo(ContentAnalysisFailureCode.EXTRACTION_API_ERROR);
        assertThat(outcome.places()).isEmpty();
    }

    @Test
    void toAnalysisOutcome_영상이_UNAVAILABLE이면_sourceStatus를_채운다() {
        SavePipelineService service = serviceForMapping();
        YouTubeContentExtractionResultDto extraction = new YouTubeContentExtractionResultDto(
                ExtractionStatus.FAILED, null, null, null, null, List.of(), List.of(),
                ExtractionFailureCode.VIDEO_UNAVAILABLE);
        SavePipelineService.Analyzed analyzed = new SavePipelineService.Analyzed(extraction, null, List.of());

        AnalysisOutcome outcome = service.toAnalysisOutcome(analyzed);

        assertThat(outcome.status()).isEqualTo(ContentAnalysisStatus.FAILED);
        assertThat(outcome.failureCode()).isEqualTo(ContentAnalysisFailureCode.VIDEO_UNAVAILABLE);
        assertThat(outcome.sourceStatus()).isEqualTo(ContentSourceStatus.UNAVAILABLE);
    }

    @Test
    void toAnalysisOutcome_장소검색실패가_섞이면_PARTIAL이고_실패후보는_빠진다() {
        SavePipelineService service = serviceForMapping();
        var candidate = candidate("p1", "성심당", "본점", null);
        YouTubeContentExtractionResultDto extraction = extraction(List.of(candidate));
        PlaceOutcomeDto failed = new PlaceOutcomeDto("p1", PlaceOutcomeDto.Decision.PLACE_SEARCH_FAILED, null, null);
        SavePipelineService.Analyzed analyzed = new SavePipelineService.Analyzed(extraction, null, List.of(failed));

        AnalysisOutcome outcome = service.toAnalysisOutcome(analyzed);

        assertThat(outcome.status()).isEqualTo(ContentAnalysisStatus.PARTIAL);
        assertThat(outcome.failureCode()).isEqualTo(ContentAnalysisFailureCode.PLACE_SEARCH_API_ERROR);
        assertThat(outcome.places()).isEmpty();
    }

    @Test
    void toAnalysisOutcome_RESOLVED된_후보만_geocodingProvider와_함께_PlaceResult로_변환한다() {
        SavePipelineService service = serviceForMapping();
        var candidate = candidate("p1", "경복궁", null, null);
        YouTubeContentExtractionResultDto extraction = extraction(List.of(candidate));
        ResolvedPlaceDto resolvedPlace = new ResolvedPlaceDto(
                "p1", "경복궁", null, "서울 종로구 경복궁", 37.58, 126.97,
                GeocodingProvider.LOCATIONIQ, "227225446", "kakao-id");
        PlaceOutcomeDto resolved = new PlaceOutcomeDto("p1", PlaceOutcomeDto.Decision.RESOLVED, resolvedPlace, null);
        SavePipelineService.Analyzed analyzed = new SavePipelineService.Analyzed(extraction, null, List.of(resolved));

        AnalysisOutcome outcome = service.toAnalysisOutcome(analyzed);

        assertThat(outcome.status()).isEqualTo(ContentAnalysisStatus.SUCCESS);
        assertThat(outcome.failureCode()).isNull();
        assertThat(outcome.places()).hasSize(1);
        AnalysisOutcome.PlaceResult placeResult = outcome.places().get(0);
        assertThat(placeResult.description()).isEqualTo("설명");
        assertThat(placeResult.geocodingProvider()).isEqualTo(GeocodingProvider.LOCATIONIQ);
        assertThat(placeResult.geocodingPlaceId()).isEqualTo("227225446");
        assertThat(placeResult.verificationPlaceId()).isEqualTo("kakao-id");
    }
}
