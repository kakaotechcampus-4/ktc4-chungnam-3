package com.ktc.chungnam3.remembrall.save;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktc.chungnam3.remembrall.content.dto.ContentSaveClaim;
import com.ktc.chungnam3.remembrall.content.service.ContentAnalysisResultService;
import com.ktc.chungnam3.remembrall.content.service.ContentPersistenceService;
import com.ktc.chungnam3.remembrall.domain.confirm.ConfirmCandidate;
import com.ktc.chungnam3.remembrall.domain.member.AuthProvider;
import com.ktc.chungnam3.remembrall.domain.member.Member;
import com.ktc.chungnam3.remembrall.extraction.dto.AnalysisMetadataDto;
import com.ktc.chungnam3.remembrall.extraction.dto.PlaceCandidateDto;
import com.ktc.chungnam3.remembrall.extraction.dto.YouTubeContentExtractionResultDto;
import com.ktc.chungnam3.remembrall.extraction.type.ExtractionStatus;
import com.ktc.chungnam3.remembrall.repository.ConfirmCandidateRepository;
import com.ktc.chungnam3.remembrall.repository.ContentPlaceRepository;
import com.ktc.chungnam3.remembrall.repository.ContentRepository;
import com.ktc.chungnam3.remembrall.repository.MemberRepository;
import com.ktc.chungnam3.remembrall.repository.PersonalSaveRepository;
import com.ktc.chungnam3.remembrall.repository.PlaceRepository;
import com.ktc.chungnam3.remembrall.repository.TriggerRepository;
import com.ktc.chungnam3.remembrall.save.confirm.ConfirmPolicy;
import com.ktc.chungnam3.remembrall.save.dto.ConfirmRequestDto;
import com.ktc.chungnam3.remembrall.save.dto.SavePipelineRequestDto;
import com.ktc.chungnam3.remembrall.save.place.PlaceLookup;
import com.ktc.chungnam3.remembrall.save.place.PlaceResolver;
import com.ktc.chungnam3.remembrall.save.place.PlaceSearchClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE;

/**
 * {@code SavePipelineService.saveAndPersist}가 NEEDS_CONFIRMATION 후보를 confirm_candidate에
 * 영속화하는지 실제 DB로 검증한다 - RESOLVED 후보는 기존대로 content_place에 저장되는 것도 같이 확인해
 * 두 경로가 서로 간섭하지 않는지 본다.
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
class SavePipelineConfirmPersistenceIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-10-27T00:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres")
    );

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private ContentRepository contentRepository;
    @Autowired
    private PersonalSaveRepository personalSaveRepository;
    @Autowired
    private PlaceRepository placeRepository;
    @Autowired
    private ContentPlaceRepository contentPlaceRepository;
    @Autowired
    private TriggerRepository triggerRepository;
    @Autowired
    private ConfirmCandidateRepository confirmCandidateRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void RESOLVED는_content_place에_NEEDS_CONFIRMATION은_confirm_candidate에_저장된다() throws Exception {
        ContentPersistenceService contentPersistenceService = new ContentPersistenceService(
                contentRepository, personalSaveRepository, placeRepository, contentPlaceRepository,
                memberRepository, triggerRepository, Clock.systemUTC(), transactionManager
        );
        ContentAnalysisResultService contentAnalysisResultService = new ContentAnalysisResultService(
                contentPersistenceService, (text, taskType) -> { throw new RuntimeException("임베딩 생략"); });

        var resolvedCandidate = new PlaceCandidateDto("p1", "성심당", "본점", null, "대전 성심당", null);
        var confirmCandidate = new PlaceCandidateDto("p2", "경복궁", null, null, "서울 경복궁", null);
        PlaceSearchClient.PlaceSearchResult resolvedResult =
                new PlaceSearchClient.PlaceSearchResult("성심당 본점, 대전", 36.32, 127.42, null, null, "loc-1");
        PlaceSearchClient.PlaceSearchResult confirmResult1 =
                new PlaceSearchClient.PlaceSearchResult("경복궁, 서울", 37.57, 126.97, null, null, "loc-2");
        PlaceSearchClient.PlaceSearchResult confirmResult2 =
                new PlaceSearchClient.PlaceSearchResult("경복궁역, 서울", 37.58, 126.98, null, null, "loc-3");

        VideoAnalyzer videoAnalyzer = url -> new YouTubeContentExtractionResultDto(
                ExtractionStatus.SUCCESS, new AnalysisMetadataDto("v1", "model", "v1"),
                null, "요약", null, List.of(resolvedCandidate, confirmCandidate), List.of(), null);
        PlaceLookup placeLookup = (name, branch, region) -> switch (name) {
            case "성심당" -> List.of(resolvedResult);
            case "경복궁" -> List.of(confirmResult1, confirmResult2);
            default -> List.of();
        };
        PlaceResolver placeResolver = new PlaceResolver(
                (lon, lat, radiusMeters) -> List.of(), (name, branch, region) -> List.of(),
                query -> Optional.empty(), name -> List.of(), 1000);

        SavePipelineService savePipelineService = new SavePipelineService(
                videoAnalyzer, placeLookup, placeResolver, new ConfirmPolicy(),
                (name, branch, lat, lon) -> Optional.of("kakao-place-id"),
                contentAnalysisResultService, confirmCandidateRepository,
                new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC));

        UUID memberId = memberRepository.save(Member.create(
                AuthProvider.KAKAO, UUID.randomUUID().toString(), Instant.now())).getId();
        ContentSaveClaim claim = contentPersistenceService.saveAndClaim(memberId, "video-confirm-persist");

        savePipelineService.saveAndPersist(claim.contentId(), new SavePipelineRequestDto("https://youtu.be/x", false));

        assertThat(contentPlaceRepository.findAllByContent_Id(claim.contentId())).hasSize(1);

        List<ConfirmCandidate> pending = confirmCandidateRepository.findPendingByContentId(claim.contentId());
        assertThat(pending).hasSize(1);
        ConfirmCandidate candidate = pending.get(0);
        assertThat(candidate.getCandidateId()).isEqualTo("p2");
        assertThat(candidate.getDescription()).isEqualTo("서울 경복궁");

        ConfirmRequestDto confirmRequest = new ObjectMapper().readValue(
                candidate.getConfirmRequest(), ConfirmRequestDto.class);
        assertThat(confirmRequest.candidates()).hasSize(2);
        assertThat(confirmRequest.candidates()).extracting(c -> c.address())
                .containsExactlyInAnyOrder("경복궁, 서울", "경복궁역, 서울");
    }
}
