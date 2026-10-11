package com.ktc.chungnam3.remembrall.save;

import com.ktc.chungnam3.remembrall.extraction.dto.AnalysisMetadataDto;
import com.ktc.chungnam3.remembrall.extraction.dto.PlaceCandidateDto;
import com.ktc.chungnam3.remembrall.extraction.dto.YouTubeContentExtractionResultDto;
import com.ktc.chungnam3.remembrall.extraction.type.ExtractionFailureCode;
import com.ktc.chungnam3.remembrall.extraction.type.ExtractionStatus;
import com.ktc.chungnam3.remembrall.save.confirm.ConfirmPolicy;
import com.ktc.chungnam3.remembrall.save.dto.PlaceOutcomeDto;
import com.ktc.chungnam3.remembrall.save.dto.SavePipelineRequestDto;
import com.ktc.chungnam3.remembrall.save.dto.SavePipelineResultDto;
import com.ktc.chungnam3.remembrall.save.place.DataportalStoreClient;
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
        return new PlaceSearchClient.PlaceSearchResult(displayName, lat, lon, null, null);
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

    @Test
    void 장소후보가_없으면_SUCCESS와_빈_리스트를_반환한다() {
        VideoAnalyzer analyzer = url -> extraction(List.of());
        SavePipelineService service = new SavePipelineService(analyzer, lookupOf(java.util.Map.of()),
                realResolver(), new ConfirmPolicy());

        SavePipelineResultDto resolved = service.save(new SavePipelineRequestDto("https://youtu.be/x", false));

        assertThat(resolved.status()).isEqualTo(ExtractionStatus.SUCCESS);
        assertThat(resolved.places()).isEmpty();
    }

    @Test
    void 장소후보_하나가_검색결과_한건으로_RESOLVED되면_그대로_반환한다() {
        var place = candidate("p1", "성심당", "본점", null);
        VideoAnalyzer analyzer = url -> extraction(List.of(place));
        PlaceLookup lookup = lookupOf(java.util.Map.of("성심당", List.of(result("성심당 본점, 대전", 36.32, 127.42))));
        SavePipelineService service = new SavePipelineService(analyzer, lookup, realResolver(), new ConfirmPolicy());

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
        SavePipelineService service = new SavePipelineService(analyzer, lookup, realResolver(), new ConfirmPolicy());

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
        SavePipelineService service = new SavePipelineService(analyzer, lookup, realResolver(), new ConfirmPolicy());

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
                realResolver(), new ConfirmPolicy());

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
                realResolver(), new ConfirmPolicy());

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
        SavePipelineService service = new SavePipelineService(analyzer, lookup, realResolver(), new ConfirmPolicy());

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
        SavePipelineService service = new SavePipelineService(analyzer, lookup, realResolver(), new ConfirmPolicy());

        SavePipelineResultDto resolved = service.save(new SavePipelineRequestDto("https://youtu.be/x", false));

        assertThat(resolved.status()).isEqualTo(ExtractionStatus.PARTIAL);
    }

    @Test
    void 아직_안_물어봤으면_NEEDS_CONFIRMATION으로_되묻는다() {
        var place = candidate("p1", "경복궁", null, null);
        VideoAnalyzer analyzer = url -> extraction(List.of(place));
        PlaceLookup lookup = lookupOf(java.util.Map.of(
                "경복궁", List.of(result("경복궁, 서울", 37.57, 126.97), result("경복궁역, 서울", 37.58, 126.98))));
        SavePipelineService service = new SavePipelineService(analyzer, lookup, realResolver(), new ConfirmPolicy());

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
        SavePipelineService service = new SavePipelineService(analyzer, lookup, realResolver(), new ConfirmPolicy());

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
        SavePipelineService service = new SavePipelineService(analyzer, lookup, realResolver(), new ConfirmPolicy());

        SavePipelineResultDto resolved = service.save(new SavePipelineRequestDto("https://youtu.be/x", true));

        assertThat(resolved.places().get(0).decision()).isEqualTo(PlaceOutcomeDto.Decision.NO_PLACE);
    }
}
