package com.ktc.chungnam3.remembrall.save.confirm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktc.chungnam3.remembrall.common.exception.ApiException;
import com.ktc.chungnam3.remembrall.common.exception.ErrorCode;
import com.ktc.chungnam3.remembrall.content.dto.ContentSaveClaim;
import com.ktc.chungnam3.remembrall.content.service.ContentPersistenceService;
import com.ktc.chungnam3.remembrall.domain.confirm.ConfirmCandidateStatus;
import com.ktc.chungnam3.remembrall.domain.member.AuthProvider;
import com.ktc.chungnam3.remembrall.domain.member.Member;
import com.ktc.chungnam3.remembrall.domain.place.GeocodingProvider;
import com.ktc.chungnam3.remembrall.repository.ConfirmCandidateRepository;
import com.ktc.chungnam3.remembrall.repository.ContentPlaceRepository;
import com.ktc.chungnam3.remembrall.repository.ContentRepository;
import com.ktc.chungnam3.remembrall.repository.MemberRepository;
import com.ktc.chungnam3.remembrall.repository.PersonalSaveRepository;
import com.ktc.chungnam3.remembrall.repository.PlaceRepository;
import com.ktc.chungnam3.remembrall.repository.TriggerRepository;
import com.ktc.chungnam3.remembrall.save.dto.ConfirmRequestDto;
import com.ktc.chungnam3.remembrall.save.dto.ResolvedPlaceDto;
import com.ktc.chungnam3.remembrall.save.place.KakaoPlaceLookup;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
class ConfirmAnswerServiceTest {

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

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ContentPersistenceService contentPersistenceService;

    @BeforeEach
    void setUp() {
        contentPersistenceService = new ContentPersistenceService(
                contentRepository, personalSaveRepository, placeRepository, contentPlaceRepository,
                memberRepository, triggerRepository, Clock.systemUTC(), transactionManager
        );
    }

    private ConfirmAnswerService serviceWithKakao(KakaoPlaceLookup kakaoPlaceLookup) {
        return new ConfirmAnswerService(confirmCandidateRepository, contentPersistenceService,
                personalSaveRepository, kakaoPlaceLookup, objectMapper, Clock.fixed(NOW, java.time.ZoneOffset.UTC));
    }

    private record Saved(UUID memberId, UUID contentId, UUID personalSaveId) {
    }

    private Saved createMemberAndContent(String videoId) {
        UUID memberId = memberRepository.save(Member.create(
                AuthProvider.KAKAO, UUID.randomUUID().toString(), Instant.now())).getId();
        ContentSaveClaim claim = contentPersistenceService.saveAndClaim(memberId, videoId);
        return new Saved(memberId, claim.contentId(), claim.personalSaveId());
    }

    private void insertPendingCandidate(UUID contentId, String candidateId) throws Exception {
        ConfirmRequestDto confirmRequest = new ConfirmRequestDto("어느 지점이 맞을까요?", List.of(
                new ResolvedPlaceDto(candidateId + "-1", "성심당", "본점", "대전 중구 은행동", 36.32, 127.42,
                        GeocodingProvider.LOCATIONIQ, "loc-1", null),
                new ResolvedPlaceDto(candidateId + "-2", "성심당", "역전점", "대전 동구 정동", 36.33, 127.43,
                        GeocodingProvider.LOCATIONIQ, "loc-2", null)
        ));
        confirmCandidateRepository.upsert(UUID.randomUUID(), contentId, candidateId, "설명",
                objectMapper.writeValueAsString(confirmRequest), NOW);
    }

    @Test
    void 옵션을_고르면_카카오_검증_후_장소와_트리거가_생기고_후보는_ANSWERED가_된다() throws Exception {
        UUID memberId = memberRepository.save(Member.create(
                AuthProvider.KAKAO, UUID.randomUUID().toString(), Instant.now())).getId();
        ContentSaveClaim claim = contentPersistenceService.saveAndClaim(memberId, "video-1");
        insertPendingCandidate(claim.contentId(), "p1");
        ConfirmAnswerService service = serviceWithKakao(
                (name, branch, lat, lon) -> Optional.of("kakao-place-id"));

        service.answer(memberId, claim.personalSaveId(), "p1", "p1-2");

        assertThat(contentPlaceRepository.findAllByContent_Id(claim.contentId())).hasSize(1);
        var place = placeRepository.findByVerificationProviderAndVerificationPlaceId(
                com.ktc.chungnam3.remembrall.domain.place.VerificationProvider.KAKAO, "kakao-place-id").orElseThrow();
        assertThat(place.getName()).isEqualTo("성심당");
        assertThat(place.getAddress()).isEqualTo("대전 동구 정동");
        assertThat(triggerRepository.findByMemberIdAndPlaceId(memberId, place.getId())).isPresent();
        assertThat(confirmCandidateRepository.findByContent_IdAndCandidateId(claim.contentId(), "p1")
                .orElseThrow().getStatus()).isEqualTo(ConfirmCandidateStatus.ANSWERED);
        assertThat(confirmCandidateRepository.findPendingByContentId(claim.contentId())).isEmpty();
    }

    @Test
    void selectedOptionId가_null이면_장소를_만들지_않고_ANSWERED로만_닫는다() throws Exception {
        Saved saved = createMemberAndContent("video-2");
        insertPendingCandidate(saved.contentId(), "p1");
        ConfirmAnswerService service = serviceWithKakao((name, branch, lat, lon) -> Optional.of("unused"));

        service.answer(saved.memberId(), saved.personalSaveId(), "p1", null);

        assertThat(contentPlaceRepository.findAllByContent_Id(saved.contentId())).isEmpty();
        assertThat(confirmCandidateRepository.findByContent_IdAndCandidateId(saved.contentId(), "p1")
                .orElseThrow().getStatus()).isEqualTo(ConfirmCandidateStatus.ANSWERED);
    }

    @Test
    void 존재하지_않는_candidateId면_NOT_FOUND다() {
        Saved saved = createMemberAndContent("video-3");
        ConfirmAnswerService service = serviceWithKakao((name, branch, lat, lon) -> Optional.of("unused"));

        assertThatThrownBy(() -> service.answer(saved.memberId(), saved.personalSaveId(), "no-such-candidate", null))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void 잘못된_selectedOptionId면_INVALID_REQUEST다() throws Exception {
        Saved saved = createMemberAndContent("video-4");
        insertPendingCandidate(saved.contentId(), "p1");
        ConfirmAnswerService service = serviceWithKakao((name, branch, lat, lon) -> Optional.of("unused"));

        assertThatThrownBy(() -> service.answer(saved.memberId(), saved.personalSaveId(), "p1", "p1-999"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void 카카오가_장소를_못_찾으면_NOT_FOUND다() throws Exception {
        Saved saved = createMemberAndContent("video-5");
        insertPendingCandidate(saved.contentId(), "p1");
        ConfirmAnswerService service = serviceWithKakao((name, branch, lat, lon) -> Optional.empty());

        assertThatThrownBy(() -> service.answer(saved.memberId(), saved.personalSaveId(), "p1", "p1-1"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(confirmCandidateRepository.findPendingByContentId(saved.contentId())).hasSize(1);
    }

    @Test
    void 이미_ANSWERED인_후보를_다시_답하면_NOT_FOUND다() throws Exception {
        Saved saved = createMemberAndContent("video-6");
        insertPendingCandidate(saved.contentId(), "p1");
        ConfirmAnswerService service = serviceWithKakao((name, branch, lat, lon) -> Optional.of("kakao-id"));
        service.answer(saved.memberId(), saved.personalSaveId(), "p1", null);

        assertThatThrownBy(() -> service.answer(saved.memberId(), saved.personalSaveId(), "p1", null))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void 남의_personalSaveId로는_답변할_수_없다() throws Exception {
        Saved owner = createMemberAndContent("video-7");
        insertPendingCandidate(owner.contentId(), "p1");
        UUID stranger = memberRepository.save(Member.create(
                AuthProvider.KAKAO, UUID.randomUUID().toString(), Instant.now())).getId();
        ConfirmAnswerService service = serviceWithKakao((name, branch, lat, lon) -> Optional.of("kakao-id"));

        assertThatThrownBy(() -> service.answer(stranger, owner.personalSaveId(), "p1", "p1-1"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(confirmCandidateRepository.findPendingByContentId(owner.contentId())).hasSize(1);
    }
}
