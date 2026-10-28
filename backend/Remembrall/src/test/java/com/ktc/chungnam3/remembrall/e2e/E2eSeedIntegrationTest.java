package com.ktc.chungnam3.remembrall.e2e;

import com.ktc.chungnam3.remembrall.auth.security.ApiAuthenticationEntryPoint;
import com.ktc.chungnam3.remembrall.auth.security.SecurityConfig;
import com.ktc.chungnam3.remembrall.auth.security.SessionAuthenticationFilter;
import com.ktc.chungnam3.remembrall.auth.token.SessionTokenHasher;
import com.ktc.chungnam3.remembrall.common.exception.GlobalExceptionHandler;
import com.ktc.chungnam3.remembrall.consent.config.ConsentConfiguration;
import com.ktc.chungnam3.remembrall.consent.service.MemberConsentService;
import com.ktc.chungnam3.remembrall.content.embedding.EmbeddingClient;
import com.ktc.chungnam3.remembrall.content.service.ContentAnalysisResultService;
import com.ktc.chungnam3.remembrall.content.service.ContentPersistenceService;
import com.ktc.chungnam3.remembrall.domain.device.Device;
import com.ktc.chungnam3.remembrall.domain.member.AuthProvider;
import com.ktc.chungnam3.remembrall.domain.member.Member;
import com.ktc.chungnam3.remembrall.recall.agent.RecallAgent;
import com.ktc.chungnam3.remembrall.recall.agent.StubRecallAgent;
import com.ktc.chungnam3.remembrall.recall.config.RecallCandidateProperties;
import com.ktc.chungnam3.remembrall.recall.service.RecallAgentInputService;
import com.ktc.chungnam3.remembrall.repository.DeviceRepository;
import com.ktc.chungnam3.remembrall.repository.MemberRepository;
import com.ktc.chungnam3.remembrall.repository.RecallCandidateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Testcontainers
@ActiveProfiles("e2e")
@SpringBootTest(classes = E2eSeedIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.ai.google.genai.api-key=test-api-key", "spring.ai.google.genai.embedding.api-key=test-api-key"
})
class E2eSeedIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-10-20T07:00:00Z");
    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired private E2eSeedService seeds;
    @Autowired private ContentPersistenceService persistence;
    @Autowired private MemberRepository members;
    @Autowired private DeviceRepository devices;
    @Autowired private SessionTokenHasher hasher;
    @Autowired private EmbeddingClient embeddings;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private RecallAgentInputService inputService;
    @Autowired private E2eAgentInputRecorder recorder;
    @Autowired private RecallAgent agent;
    @LocalServerPort private int port;
    private UUID memberId;
    private String token;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM member");
        jdbc.update("DELETE FROM content");
        jdbc.update("DELETE FROM place");
        clearInvocations(embeddings);
        memberId = member();
        token = session(memberId);
    }

    @Test
    void seedsThroughRealPersistenceAndDoesNotReanalyzeOnRepeatOrForAnotherMember() {
        var first = seeds.seed(memberId);
        assertThat(first.contents()).hasSize(6).allSatisfy(content -> {
            assertThat(content.analysisStatus()).isEqualTo("SUCCESS");
            assertThat(content.analysisClaimed()).isTrue();
            assertThat(content.outcomeApplied()).isTrue();
            assertThat(content.embeddingFilled()).isTrue();
            assertThat(content.embeddingModel()).isEqualTo("test-embedding");
        });
        assertThat(first.places()).hasSize(5);
        var departure = first.contents().stream().filter(c -> c.videoId().equals("e2e-seed-start-cafe"))
                .findFirst().orElseThrow();
        assertThat(departure.savedAt()).isEqualTo(Instant.parse("2026-07-20T07:00:00Z"));
        assertThat(count("personal_save", memberId)).isEqualTo(5);
        assertThat(count("trigger", memberId)).isEqualTo(4);
        assertThat(count("personal_save", first.publicMemberId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM member_consent WHERE member_id = ? AND consent_type = 'PUBLIC_CANDIDATE_CONTRIBUTION' AND withdrawn_at IS NULL",
                Integer.class, first.publicMemberId())).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT DISTINCT geocoding_provider || '/' || verification_provider FROM place", String.class))
                .containsExactly("LOCATIONIQ/KAKAO");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM place WHERE geocoding_place_id LIKE 'e2e-seed-%' AND verification_place_id LIKE 'e2e-seed-%'", Integer.class))
                .isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM content WHERE vector_dims(embedding) = 768", Integer.class)).isEqualTo(6);
        Instant analyzedAt = NOW.minusSeconds(3600);
        jdbc.update("UPDATE content SET analyzed_at = ?", Timestamp.from(analyzedAt));
        var repeated = seeds.seed(memberId);
        assertThat(repeated.contents()).allSatisfy(content -> {
            assertThat(content.analysisClaimed()).isFalse();
            assertThat(content.outcomeApplied()).isFalse();
        });
        assertThat(repeated.contents().stream().filter(c -> c.contentId().equals(departure.contentId()))
                .findFirst().orElseThrow().savedAt()).isEqualTo(departure.savedAt());
        UUID otherMember = member();
        var other = seeds.seed(otherMember);
        assertThat(other.contents()).extracting(E2eSeedService.ContentView::contentId)
                .containsExactlyElementsOf(first.contents().stream().map(E2eSeedService.ContentView::contentId).toList());
        assertThat(count("personal_save", otherMember)).isEqualTo(5);
        assertThat(count("trigger", otherMember)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM content", Integer.class)).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM place", Integer.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM content WHERE analyzed_at = ?", Integer.class,
                Timestamp.from(analyzedAt))).isEqualTo(6);
        verify(embeddings, times(6)).embedDocument(anyString());
    }

    @Test
    void doesNotApplyAnOutcomeWhenAnotherCallerOwnsTheAnalysisClaim() {
        var claim = persistence.saveAndClaim(memberId, "e2e-seed-cooking-video");
        var seeded = seeds.seed(memberId);
        var cooking = seeded.contents().stream().filter(c -> c.contentId().equals(claim.contentId())).findFirst().orElseThrow();
        assertThat(cooking.analysisStatus()).isEqualTo("ANALYZING");
        assertThat(cooking.analysisClaimed()).isFalse();
        assertThat(cooking.outcomeApplied()).isFalse();
        assertThat(cooking.embeddingFilled()).isFalse();
        verify(embeddings, times(5)).embedDocument(anyString());
    }

    @Test
    void requiresSessionAndCapturesTheActualInputWithoutReplacingTheAgent() throws Exception {
        assertThat(request("POST", "/dev/e2e/seed", null).statusCode()).isEqualTo(401);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM content", Integer.class)).isZero();
        var response = request("POST", "/dev/e2e/seed", token);
        assertThat(response.statusCode()).isEqualTo(200);
        var seeded = mapper.readValue(response.body(), E2eSeedService.SeedResponse.class);
        UUID executionId = UUID.randomUUID();
        UUID triggerId = jdbc.queryForObject("SELECT id FROM trigger WHERE member_id = ? AND place_id = ?",
                UUID.class, memberId, seeded.origin().placeId());
        jdbc.update("INSERT INTO recall_execution (id, trigger_id, trigger_event_id, status, agent_version, event_occurred_at, created_at, updated_at) VALUES (?, ?, ?, 'PENDING', ?, ?, ?, ?)",
                executionId, triggerId, UUID.randomUUID(), agent.version(), Timestamp.from(NOW), Timestamp.from(NOW), Timestamp.from(NOW));
        var input = inputService.prepare(executionId);
        var publicContent = seeded.contents().stream().filter(E2eSeedService.ContentView::publicContribution).findFirst().orElseThrow();
        var cooking = seeded.contents().stream().filter(c -> c.videoId().equals("e2e-seed-cooking-video")).findFirst().orElseThrow();
        assertThat(input.nearbyPersonalCandidates()).hasSize(3);
        assertThat(input.nearbyPublicCandidates()).extracting(p -> p.placeId()).contains(publicContent.places().getFirst().placeId());
        assertThat(input.semanticCandidates()).extracting(c -> c.contentId()).contains(cooking.contentId());
        assertThat(recorder.find(executionId)).isEmpty();
        assertThat(agent.execute(executionId, input)).isNotNull();
        assertThat(recorder.find(executionId)).containsSame(input);
        String path = "/dev/e2e/executions/" + executionId + "/input";
        assertThat(request("GET", path, null).statusCode()).isEqualTo(401);
        assertThat(request("GET", path, session(member())).statusCode()).isEqualTo(404);
        var recorded = request("GET", path, token);
        assertThat(recorded.statusCode()).isEqualTo(200);
        assertThat(mapper.readTree(recorded.body()).get("semanticCandidates").get(0).get("contentId").asText())
                .isEqualTo(cooking.contentId().toString());
    }

    private int count(String table, UUID owner) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE member_id = ?", Integer.class, owner);
    }

    private UUID member() {
        return members.saveAndFlush(Member.create(AuthProvider.GUEST, UUID.randomUUID().toString(), NOW)).getId();
    }

    private String session(UUID owner) {
        String value = UUID.randomUUID().toString();
        devices.saveAndFlush(Device.create(owner, hasher.hash(value), NOW.plusSeconds(3600), NOW));
        return value;
    }

    private HttpResponse<String> request(String method, String path, String value) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (value != null) {
            builder.header("Authorization", "Bearer " + value);
        }
        return HttpClient.newHttpClient().send(builder.method(method, HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Configuration(proxyBeanMethods = false)
    @TestComponent
    @EnableAutoConfiguration
    @EntityScan("com.ktc.chungnam3.remembrall.domain")
    @EnableJpaRepositories(basePackageClasses = MemberRepository.class)
    @EnableConfigurationProperties(RecallCandidateProperties.class)
    @Import({E2eSeedService.class, E2eSeedController.class, E2eAgentInputRecorder.class,
            ContentPersistenceService.class, ContentAnalysisResultService.class,
            MemberConsentService.class, ConsentConfiguration.class, SecurityConfig.class,
            SessionAuthenticationFilter.class, ApiAuthenticationEntryPoint.class, SessionTokenHasher.class,
            GlobalExceptionHandler.class, RecallAgentInputService.class, RecallCandidateRepository.class})
    static class TestApplication {
        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        EmbeddingClient embeddings() {
            var client = mock(EmbeddingClient.class);
            float[] vector = new float[768];
            vector[0] = 1;
            when(client.embedDocument(anyString())).thenReturn(new EmbeddingClient.Result(vector, "test-embedding"));
            return client;
        }

        @Bean
        RecallAgent agent() {
            return new StubRecallAgent();
        }
    }
}
