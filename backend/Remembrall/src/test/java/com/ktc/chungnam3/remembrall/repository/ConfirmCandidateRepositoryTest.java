package com.ktc.chungnam3.remembrall.repository;

import com.ktc.chungnam3.remembrall.domain.confirm.ConfirmCandidate;
import com.ktc.chungnam3.remembrall.domain.confirm.ConfirmCandidateStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
class ConfirmCandidateRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-10-27T00:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres")
    );

    @Autowired
    private ConfirmCandidateRepository repository;

    @Autowired
    private ContentRepository contentRepository;

    private UUID contentId;

    @BeforeEach
    void setUp() {
        String videoId = "v-" + UUID.randomUUID().toString().substring(0, 20);
        contentRepository.insertIfAbsent(UUID.randomUUID(), videoId, NOW);
        contentId = contentRepository.findIdByVideoId(videoId).orElseThrow();
    }

    @Test
    void 저장한_후보를_PENDING으로_조회한다() {
        repository.upsert(UUID.randomUUID(), contentId, "p1", "성심당 설명", "{\"question\":\"q\"}", NOW);

        var pending = repository.findPendingByContentId(contentId);

        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).getCandidateId()).isEqualTo("p1");
        assertThat(pending.get(0).getConfirmRequest()).isEqualTo("{\"question\":\"q\"}");
        assertThat(pending.get(0).getStatus()).isEqualTo(ConfirmCandidateStatus.PENDING);
    }

    @Test
    void 같은_content_candidate로_다시_upsert하면_내용만_갱신되고_PENDING으로_되돌아간다() {
        UUID id = UUID.randomUUID();
        repository.upsert(id, contentId, "p1", "옛 설명", "{\"question\":\"old\"}", NOW);
        repository.markAnswered(id, NOW);

        repository.upsert(UUID.randomUUID(), contentId, "p1", "새 설명", "{\"question\":\"new\"}", NOW.plusSeconds(60));

        var candidate = repository.findByContent_IdAndCandidateId(contentId, "p1").orElseThrow();
        assertThat(candidate.getId()).isEqualTo(id);
        assertThat(candidate.getDescription()).isEqualTo("새 설명");
        assertThat(candidate.getConfirmRequest()).isEqualTo("{\"question\":\"new\"}");
        assertThat(candidate.getStatus()).isEqualTo(ConfirmCandidateStatus.PENDING);
        assertThat(candidate.getAnsweredAt()).isNull();
    }

    @Test
    void markAnswered하면_상태가_바뀌고_findPendingByContentId에서_빠진다() {
        UUID id = UUID.randomUUID();
        repository.upsert(id, contentId, "p1", "설명", "{\"question\":\"q\"}", NOW);

        int updated = repository.markAnswered(id, NOW.plusSeconds(10));

        assertThat(updated).isEqualTo(1);
        assertThat(repository.findPendingByContentId(contentId)).isEmpty();
        ConfirmCandidate answered = repository.findById(id).orElseThrow();
        assertThat(answered.getStatus()).isEqualTo(ConfirmCandidateStatus.ANSWERED);
        assertThat(answered.getAnsweredAt()).isEqualTo(NOW.plusSeconds(10));
    }

    @Test
    void 이미_ANSWERED인_후보를_다시_markAnswered하면_0건_갱신된다() {
        UUID id = UUID.randomUUID();
        repository.upsert(id, contentId, "p1", "설명", "{\"question\":\"q\"}", NOW);
        repository.markAnswered(id, NOW);

        int updated = repository.markAnswered(id, NOW.plusSeconds(10));

        assertThat(updated).isEqualTo(0);
    }
}
