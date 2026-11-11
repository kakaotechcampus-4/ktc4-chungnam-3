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
                name -> List.of(),
                1000);
    }

    /**
     * 대부분의 테스트는 카카오 검증 자체가 관심사가 아니므로, 항상 ID를 찾은 것으로 고정한다. 좌표마다 다른
     * ID를 준다 - 되묻기 사전검증이 같은 카카오 ID 옵션을 합치므로, 같은 ID를 주면 서로 다른 장소가 합쳐진다.
     */
    private static KakaoPlaceLookup kakaoAlwaysFound() {
        return (name, branch, lat, lon) -> Optional.of("kakao-" + lat + "," + lon);
    }

    @Test
    void 장소후보가_없으면_SUCCESS와_빈_리스트를_반환한다() {
        VideoAnalyzer analyzer = url -> extraction(List.of());
        SavePipelineService service = new SavePipelineService(analyzer, lookupOf(java.util.Map.of()),
                realResolver(), kakaoAlwaysFound());

        SavePipelineResultDto resolved = service.save(new SavePipelineRequestDto("https://youtu.be/x"));

        assertThat(resolved.status()).isEqualTo(ExtractionStatus.SUCCESS);
        assertThat(resolved.places()).isEmpty();
    }

    @Test
    void 장소후보_하나가_검색결과_한건으로_RESOLVED되면_그대로_반환한다() {
        var place = candidate("p1", "성심당", "본점", null);
        VideoAnalyzer analyzer = url -> extraction(List.of(place));
        PlaceLookup lookup = lookupOf(java.util.Map.of("성심당", List.of(result("성심당 본점, 대전", 36.32, 127.42))));
        SavePipelineService service = new SavePipelineService(analyzer, lookup, realResolver(), kakaoAlwaysFound());

        SavePipelineResultDto resolved = service.save(new SavePipelineRequestDto("https://youtu.be/x"));

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
        SavePipelineService service = new SavePipelineService(analyzer, lookup, realResolver(), kakaoAlwaysFound());

        SavePipelineResultDto resolved = service.save(new SavePipelineRequestDto("https://youtu.be/x"));

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
        SavePipelineService service = new SavePipelineService(analyzer, lookup, realResolver(), kakaoAlwaysFound());

        SavePipelineResultDto resolved = service.save(new SavePipelineRequestDto("not-a-url"));

        assertThat(resolved.status()).isEqualTo(ExtractionStatus.FAILED);
        assertThat(resolved.places()).isEmpty();
    }

    @Test
    void 영상_분석_자체가_예외를_던지면_FAILED를_반환한다() {
        VideoAnalyzer analyzer = url -> {
            throw new IllegalStateException("Gemini 응답 파싱 실패");
        };
        SavePipelineService service = new SavePipelineService(analyzer, lookupOf(java.util.Map.of()),
                realResolver(), kakaoAlwaysFound());

        SavePipelineResultDto resolved = service.save(new SavePipelineRequestDto("https://youtu.be/x"));

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
                realResolver(), kakaoAlwaysFound());

        SavePipelineResultDto resolved = service.save(new SavePipelineRequestDto("https://youtu.be/x"));

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
        SavePipelineService service = new SavePipelineService(analyzer, lookup, realResolver(), kakaoAlwaysFound());

        SavePipelineResultDto resolved = service.save(new SavePipelineRequestDto("https://youtu.be/x"));

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
        SavePipelineService service = new SavePipelineService(analyzer, lookup, realResolver(), kakaoAlwaysFound());

        SavePipelineResultDto resolved = service.save(new SavePipelineRequestDto("https://youtu.be/x"));

        assertThat(resolved.status()).isEqualTo(ExtractionStatus.PARTIAL);
    }

    @Test
    void 후보가_모호하면_NEEDS_CONFIRMATION으로_되묻는다() {
        var place = candidate("p1", "경복궁", null, null);
        VideoAnalyzer analyzer = url -> extraction(List.of(place));
        PlaceLookup lookup = lookupOf(java.util.Map.of(
                "경복궁", List.of(result("경복궁, 서울", 37.57, 126.97), result("경복궁역, 서울", 37.58, 126.98))));
        SavePipelineService service = new SavePipelineService(analyzer, lookup, realResolver(), kakaoAlwaysFound());

        SavePipelineResultDto resolved = service.save(new SavePipelineRequestDto("https://youtu.be/x"));

        assertThat(resolved.places().get(0).decision()).isEqualTo(PlaceOutcomeDto.Decision.NEEDS_CONFIRMATION);
        assertThat(resolved.places().get(0).confirmRequest()).isNotNull();
    }

    // --- 되묻기 후보 카카오 사전검증(2026-11-08, 2002횟집 저장 실패 실서버 재현) ---
    // "경복궁" 검색결과 2건(서로 600m+ 떨어져 중복 병합 안 됨) → 기존 판정은 NEEDS_CONFIRMATION 2옵션.
    private static final PlaceLookup TWO_OPTIONS = lookupOf(java.util.Map.of(
            "경복궁", List.of(result("경복궁, 서울", 37.57, 126.97), result("경복궁역, 서울", 37.58, 126.98))));
    private static final VideoAnalyzer ONE_CANDIDATE = url -> extraction(List.of(candidate("p1", "경복궁", null, null)));

    @Test
    void 되묻기_옵션중_카카오가_못찾는_옵션은_빼고_찾은_옵션엔_ID를_붙인다() {
        KakaoPlaceLookup kakao = (name, branch, lat, lon) -> lat == 37.57 ? Optional.of("k-a") : Optional.empty();
        SavePipelineService service = new SavePipelineService(ONE_CANDIDATE, TWO_OPTIONS, realResolver(), kakao);

        PlaceOutcomeDto outcome = service.save(new SavePipelineRequestDto("https://youtu.be/x")).places().get(0);

        // 하나가 빠져서 1개가 남은 것(같은 장소로 합쳐진 게 아님)은 빠진 쪽이 정답이었을 수 있어 확정하지 않는다.
        assertThat(outcome.decision()).isEqualTo(PlaceOutcomeDto.Decision.NEEDS_CONFIRMATION);
        assertThat(outcome.confirmRequest().candidates()).extracting(ResolvedPlaceDto::verificationPlaceId)
                .containsExactly("k-a");
    }

    @Test
    void 확정된_되묻기의_candidateId는_옵션_id가_아니라_원래_후보_id다() {
        KakaoPlaceLookup kakao = (name, branch, lat, lon) -> Optional.of("same-kakao-id");
        SavePipelineService service = new SavePipelineService(ONE_CANDIDATE, TWO_OPTIONS, realResolver(), kakao);

        PlaceOutcomeDto outcome = service.save(new SavePipelineRequestDto("https://youtu.be/x")).places().get(0);

        assertThat(outcome.resolvedPlace().candidateId()).isEqualTo("p1");
    }

    @Test
    void 되묻기_옵션이_모두_같은_카카오_장소면_하나로_합쳐_바로_확정한다() {
        KakaoPlaceLookup kakao = (name, branch, lat, lon) -> Optional.of("same-kakao-id");
        SavePipelineService service = new SavePipelineService(ONE_CANDIDATE, TWO_OPTIONS, realResolver(), kakao);

        PlaceOutcomeDto outcome = service.save(new SavePipelineRequestDto("https://youtu.be/x")).places().get(0);

        assertThat(outcome.decision()).isEqualTo(PlaceOutcomeDto.Decision.RESOLVED);
        assertThat(outcome.resolvedPlace().verificationPlaceId()).isEqualTo("same-kakao-id");
    }

    @Test
    void 되묻기_옵션을_카카오가_하나도_못찾으면_저장할_수_없으니_NO_PLACE다() {
        KakaoPlaceLookup kakao = (name, branch, lat, lon) -> Optional.empty();
        SavePipelineService service = new SavePipelineService(ONE_CANDIDATE, TWO_OPTIONS, realResolver(), kakao);

        PlaceOutcomeDto outcome = service.save(new SavePipelineRequestDto("https://youtu.be/x")).places().get(0);

        assertThat(outcome.decision()).isEqualTo(PlaceOutcomeDto.Decision.NO_PLACE);
    }

    @Test
    void 카카오_호출이_실패한_옵션은_빼지_않고_ID_없이_남겨_되묻는다() {
        KakaoPlaceLookup kakao = (name, branch, lat, lon) -> {
            if (lat == 37.57) {
                throw new IllegalStateException("카카오 일시 장애");
            }
            return Optional.of("k-b");
        };
        SavePipelineService service = new SavePipelineService(ONE_CANDIDATE, TWO_OPTIONS, realResolver(), kakao);

        PlaceOutcomeDto outcome = service.save(new SavePipelineRequestDto("https://youtu.be/x")).places().get(0);

        assertThat(outcome.decision()).isEqualTo(PlaceOutcomeDto.Decision.NEEDS_CONFIRMATION);
        assertThat(outcome.confirmRequest().candidates()).extracting(ResolvedPlaceDto::verificationPlaceId)
                .containsExactly(null, "k-b");
    }

    @Test
    void 원래_1개짜리_되묻기는_카카오가_찾아도_확정하지_않고_ID만_붙여_되묻는다() {
        // 지점명 불일치로 생긴 1옵션 되묻기 - 카카오 검증은 "그 좌표에 그 이름이 있다"일 뿐 영상 속 지점을 보장 안 함.
        VideoAnalyzer analyzer = url -> extraction(List.of(candidate("p1", "성심당", "본점", null)));
        PlaceLookup lookup = lookupOf(java.util.Map.of("성심당", List.of(result("성심당 역전점, 대전", 36.33, 127.43))));
        SavePipelineService service = new SavePipelineService(analyzer, lookup, realResolver(), kakaoAlwaysFound());

        PlaceOutcomeDto outcome = service.save(new SavePipelineRequestDto("https://youtu.be/x")).places().get(0);

        assertThat(outcome.decision()).isEqualTo(PlaceOutcomeDto.Decision.NEEDS_CONFIRMATION);
        assertThat(outcome.confirmRequest().candidates()).hasSize(1);
        assertThat(outcome.confirmRequest().candidates().get(0).verificationPlaceId()).isNotNull();
    }

    @Test
    void Gemini_category가_PlaceResolver까지_전달돼_가게류면_TourAPI_관광지로_확정하지_않는다() {
        PlaceResolver resolverWithPalace = new PlaceResolver(
                (lon, lat, radiusMeters) -> List.of(), (name, branch, region) -> List.of(), query -> Optional.empty(),
                name -> List.of(new com.ktc.chungnam3.remembrall.save.place.TourApiClient.TourApiItem(
                        "126508", "경복궁", "서울특별시 종로구 사직로 161", 37.5796, 126.9770)),
                1000);
        var restaurant = new PlaceCandidateDto("p1", "경복궁", null, null, "고깃집", "맛집");
        var palace = new PlaceCandidateDto("p2", "경복궁", null, null, "궁궐", "관광지");
        SavePipelineService service = new SavePipelineService(url -> extraction(List.of(restaurant, palace)),
                lookupOf(java.util.Map.of()), resolverWithPalace, kakaoAlwaysFound());

        var places = service.save(new SavePipelineRequestDto("https://youtu.be/x")).places();

        assertThat(places.get(0).decision()).isEqualTo(PlaceOutcomeDto.Decision.NO_PLACE);
        assertThat(places.get(1).decision()).isEqualTo(PlaceOutcomeDto.Decision.RESOLVED);
    }

    @Test
    void 카카오에_장소가_없으면_RESOLVED_대신_NO_PLACE를_처리한다() {
        // Place.md: "결과가 모호하거나 일치하지 않으면 Place를 생성하지 않고 실패 기록" - PlaceResolver가
        // 확정해도 카카오 검증이 안 되면 RESOLVED로 내보내면 안 된다. 단, 정상 호출인데 못 찾은
        // 것뿐이라 오류가 아니라 NO_PLACE다(저장담당 요구사항.md "결과 없음은 API 오류와 구분").
        var place = candidate("p1", "성심당", "본점", null);
        VideoAnalyzer analyzer = url -> extraction(List.of(place));
        PlaceLookup lookup = lookupOf(java.util.Map.of("성심당", List.of(result("성심당 본점, 대전", 36.32, 127.42))));
        KakaoPlaceLookup kakaoNotFound = (name, branch, lat, lon) -> Optional.empty();
        SavePipelineService service = new SavePipelineService(analyzer, lookup, realResolver(), kakaoNotFound);

        SavePipelineResultDto resolved = service.save(new SavePipelineRequestDto("https://youtu.be/x"));

        assertThat(resolved.places().get(0).decision()).isEqualTo(PlaceOutcomeDto.Decision.NO_PLACE);
        assertThat(resolved.status()).isEqualTo(ExtractionStatus.SUCCESS);
    }

    @Test
    void 카카오_호출이_실패하면_PLACE_VERIFICATION_FAILED이고_콘텐츠는_PLACE_VERIFICATION_API_ERROR다() {
        var place = candidate("p1", "성심당", "본점", null);
        VideoAnalyzer analyzer = url -> extraction(List.of(place));
        PlaceLookup lookup = lookupOf(java.util.Map.of("성심당", List.of(result("성심당 본점, 대전", 36.32, 127.42))));
        KakaoPlaceLookup kakaoFails = (name, branch, lat, lon) -> {
            throw new RuntimeException("401 Unauthorized");
        };
        SavePipelineService service = new SavePipelineService(analyzer, lookup, realResolver(), kakaoFails);

        SavePipelineResultDto resolved = service.save(new SavePipelineRequestDto("https://youtu.be/x"));
        AnalysisOutcome outcome = service.toAnalysisOutcome(
                new SavePipelineService.Analyzed(extraction(List.of(place)), null, resolved.places()));

        assertThat(resolved.places().get(0).decision()).isEqualTo(PlaceOutcomeDto.Decision.PLACE_VERIFICATION_FAILED);
        assertThat(resolved.status()).isEqualTo(ExtractionStatus.PARTIAL);
        assertThat(outcome.failureCode()).isEqualTo(ContentAnalysisFailureCode.PLACE_VERIFICATION_API_ERROR);
    }

    private static SavePipelineService serviceForMapping() {
        return new SavePipelineService(url -> extraction(List.of()), lookupOf(java.util.Map.of()),
                realResolver(), kakaoAlwaysFound());
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
    void toAnalysisOutcome_콘텐츠_category를_그대로_넘긴다() {
        SavePipelineService service = serviceForMapping();
        YouTubeContentExtractionResultDto extraction = new YouTubeContentExtractionResultDto(
                ExtractionStatus.SUCCESS, new AnalysisMetadataDto("v1", "model", "v1"),
                "대전 맛집 TOP10", "요약", "맛집", List.of(), List.of(), null);

        AnalysisOutcome outcome = service.toAnalysisOutcome(
                new SavePipelineService.Analyzed(extraction, null, List.of()));

        assertThat(outcome.category()).isEqualTo("맛집");
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
