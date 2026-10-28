package com.ktc.chungnam3.remembrall.recall;

import com.ktc.chungnam3.remembrall.recall.agent.ProposalSource;
import com.ktc.chungnam3.remembrall.recall.agent.RecallAgentInput;
import com.ktc.chungnam3.remembrall.recall.config.RecallCandidateProperties;
import com.ktc.chungnam3.remembrall.recall.service.RecallAgentInputService;
import com.ktc.chungnam3.remembrall.repository.RecallCandidateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

import java.time.DayOfWeek;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE;

@Testcontainers
@DataJpaTest(properties = {
        "recall.candidates.radius-meters=1000",
        "recall.candidates.nearby-personal-limit=2",
        "recall.candidates.nearby-public-limit=2",
        "recall.candidates.semantic-limit=3",
        "recall.candidates.max-summary-length=5"
})
@AutoConfigureTestDatabase(replace = NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({RecallCandidateRepository.class, RecallAgentInputService.class,
        RecallAgentInputIntegrationTest.PropertiesConfiguration.class})
class RecallAgentInputIntegrationTest {

    private static final Instant EVENT_TIME = Instant.parse("2026-10-08T16:30:00Z");
    private static final Instant SAVED_AT = Instant.parse("2026-10-01T00:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RecallAgentInputService inputs;

    @MockitoSpyBean
    private RecallCandidateRepository candidates;

    private UUID memberId;
    private UUID originPlaceId;
    private UUID executionId;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM member");
        jdbc.update("DELETE FROM content");
        jdbc.update("DELETE FROM place");
        memberId = member();
        originPlaceId = place(37.0, 127.0);
        UUID triggerId = UUID.randomUUID();
        jdbc.update("INSERT INTO trigger (id, member_id, place_id) VALUES (?, ?, ?)",
                triggerId, memberId, originPlaceId);
        executionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO recall_execution (id, trigger_id, trigger_event_id, status, agent_version, event_occurred_at)
                VALUES (?, ?, ?, 'RUNNING', 'stub-v1', ?)
                """, executionId, triggerId, UUID.randomUUID(), Timestamp.from(EVENT_TIME));
    }

    @Test
    void originContainsOnlyOwnLinkedContentsWithDescriptionAndTruncatedSummary() {
        UUID contentId = content("SUCCESS", "AVAILABLE", null);
        UUID saveId = save(memberId, contentId);
        link(contentId, originPlaceId);
        UUID otherContent = content("SUCCESS", "AVAILABLE", null);
        save(member(), otherContent);
        link(otherContent, originPlaceId);

        var input = inputs.prepare(executionId);

        assertThat(input.origin()).extracting(RecallAgentInput.Origin::placeId, RecallAgentInput.Origin::name,
                RecallAgentInput.Origin::latitude, RecallAgentInput.Origin::longitude)
                .containsExactly(originPlaceId, "Place " + originPlaceId, 37.0, 127.0);
        assertThat(input.origin().contents()).hasSize(1);
        assertThat(input.origin().contents().getFirst()).isEqualTo(new RecallAgentInput.PersonalContent(
                contentId, saveId, "Title " + contentId, "가😀나다라", "FOOD", SAVED_AT, "Description " + contentId));
        assertThat(input.situation()).isEqualTo(new RecallAgentInput.Situation(
                LocalDateTime.of(2026, 10, 9, 1, 30), DayOfWeek.FRIDAY));
    }

    @Test
    void nearbyPersonalGroupsAllContentsAndAppliesRadiusPlaceLimitAndOwnership() {
        UUID near = place(37.001, 127.0);
        UUID farther = place(37.002, 127.0);
        UUID overLimit = place(37.003, 127.0);
        UUID outside = place(37.02, 127.0);
        UUID foreignPlace = place(37.0001, 127.0);
        UUID first = savedAt(memberId, near);
        UUID second = savedAt(memberId, near);
        savedAt(memberId, farther);
        savedAt(memberId, overLimit);
        savedAt(memberId, outside);
        savedAt(memberId, originPlaceId);
        savedAt(member(), foreignPlace);

        var places = inputs.prepare(executionId).nearbyPersonalCandidates();

        assertThat(places).extracting(RecallAgentInput.PersonalPlace::placeId).containsExactly(near, farther);
        assertThat(places.getFirst().contents()).extracting(RecallAgentInput.PersonalContent::contentId)
                .containsExactlyInAnyOrder(first, second);
        assertThat(places.getFirst().contents()).allSatisfy(item -> {
            assertThat(item.personalSaveId()).isNotNull();
            assertThat(item.savedAt()).isEqualTo(SAVED_AT);
            assertThat(item.description()).isEqualTo("Description " + item.contentId());
            assertThat(item.summary()).isEqualTo("가😀나다라");
        });
        assertThat(places.getFirst().distanceMeters()).isCloseTo(111.1949,
                org.assertj.core.data.Offset.offset(0.001));
        assertThat(places.getLast().distanceMeters()).isGreaterThan(places.getFirst().distanceMeters());
    }

    @Test
    void equalDistancesUsePlaceIdAsTieBreakerForPersonalAndPublic() {
        UUID lower = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID higher = UUID.fromString("00000000-0000-0000-0000-000000000002");
        place(higher, 37.001, 127.0);
        place(lower, 37.001, 127.0);
        savedAt(memberId, higher);
        savedAt(memberId, lower);
        UUID contributor = member();
        consent(contributor);
        savedAt(contributor, higher);
        savedAt(contributor, lower);

        var input = inputs.prepare(executionId);

        assertThat(input.nearbyPersonalCandidates()).extracting(RecallAgentInput.PersonalPlace::placeId)
                .containsExactly(lower, higher);
        assertThat(input.nearbyPublicCandidates()).extracting(RecallAgentInput.PublicPlace::placeId)
                .containsExactly(lower, higher);
    }

    @Test
    void nearbyPublicRequiresConsentExcludesOwnContentAndLimitsNearestPlaces() {
        UUID contributor = member();
        consent(contributor);
        UUID near = place(37.001, 127.0);
        UUID farther = place(37.002, 127.0);
        UUID overLimit = place(37.003, 127.0);
        UUID outside = place(37.02, 127.0);
        UUID unconsented = place(37.0001, 127.0);
        UUID publicContent = savedAt(contributor, near);
        UUID anotherPublic = savedAt(contributor, near);
        UUID alreadyOwned = savedAt(contributor, near);
        save(memberId, alreadyOwned);
        savedAt(contributor, farther);
        savedAt(contributor, overLimit);
        savedAt(contributor, outside);
        savedAt(member(), unconsented);

        var input = inputs.prepare(executionId);

        assertThat(input.nearbyPublicCandidates()).extracting(RecallAgentInput.PublicPlace::placeId)
                .containsExactly(near, farther);
        assertThat(input.nearbyPublicCandidates().getFirst().contents())
                .extracting(RecallAgentInput.PublicContent::contentId)
                .containsExactlyInAnyOrder(publicContent, anotherPublic);
        assertThat(input.nearbyPersonalCandidates().getFirst().contents())
                .extracting(RecallAgentInput.PersonalContent::contentId).containsExactly(alreadyOwned);
        var json = new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(
                input.nearbyPublicCandidates().getFirst().contents().getFirst()));
        assertThat(json.propertyNames()).containsExactlyInAnyOrder("contentId", "title", "summary", "category", "description");
        assertThat(json.get("summary").asString()).isEqualTo("가😀나다라");
    }

    @Test
    void publicContentsAtOriginAreIncludedUnlessMemberAlreadySavedThem() {
        UUID contributor = member();
        consent(contributor);
        UUID publicContent = savedAt(contributor, originPlaceId);
        UUID personalContent = savedAt(memberId, originPlaceId);
        save(contributor, personalContent);

        var input = inputs.prepare(executionId);

        assertThat(input.nearbyPublicCandidates()).hasSize(1);
        assertThat(input.nearbyPublicCandidates().getFirst().placeId()).isEqualTo(originPlaceId);
        assertThat(input.nearbyPublicCandidates().getFirst().distanceMeters()).isZero();
        assertThat(input.nearbyPublicCandidates().getFirst().contents())
                .extracting(RecallAgentInput.PublicContent::contentId).containsExactly(publicContent);
        assertThat(input.origin().contents()).extracting(RecallAgentInput.PersonalContent::contentId)
                .containsExactly(personalContent);
    }

    @Test
    void publicEligibilityTracksWithdrawalsAndDeletedSavesWithoutDuplicatingContributors() {
        UUID first = member();
        UUID second = member();
        consent(first);
        consent(second);
        savedOrigin(vector(1, 0));
        UUID nearby = savedAt(first, place(37.001, 127.0));
        UUID semantic = content("SUCCESS", "AVAILABLE", vector(1, 0));
        save(first, semantic);
        save(second, nearby);
        save(second, semantic);
        assertPublicContains(nearby, semantic);

        jdbc.update("UPDATE member_consent SET withdrawn_at = ? WHERE member_id = ?", Timestamp.from(EVENT_TIME), first);
        assertPublicContains(nearby, semantic);

        jdbc.update("DELETE FROM personal_save WHERE member_id = ?", second);
        assertPublicEmpty();

        consent(first);
        assertPublicContains(nearby, semantic);
        jdbc.update("DELETE FROM personal_save WHERE member_id = ?", first);
        assertPublicEmpty();
    }

    @Test
    void semanticUsesMinimumPgvectorCosineDistanceAndPersonalPrecedence() {
        UUID originFirst = savedOrigin(vector(1, 0));
        UUID originSecond = savedOrigin(vector(0, 1));
        UUID contributor = member();
        consent(contributor);
        UUID closestPublic = content("SUCCESS", "AVAILABLE", vector(0, 1));
        save(contributor, closestPublic);
        UUID personal = content("SUCCESS", "AVAILABLE", vector(0.8, 0.6));
        UUID personalSaveId = save(memberId, personal);
        save(contributor, personal);
        UUID thirdPublic = content("PARTIAL", "UNKNOWN", vector(-1, 0));
        save(contributor, thirdPublic);
        UUID overLimit = content("SUCCESS", "AVAILABLE", vector(-1, -1));
        save(memberId, overLimit);
        UUID placed = content("SUCCESS", "AVAILABLE", vector(1, 0));
        save(memberId, placed);
        link(placed, place(37.1, 127.0));
        UUID missingEmbedding = content("SUCCESS", "AVAILABLE", null);
        save(memberId, missingEmbedding);
        UUID unconsented = content("SUCCESS", "AVAILABLE", vector(1, 0));
        save(member(), unconsented);

        var items = inputs.prepare(executionId).semanticCandidates();

        assertThat(items).extracting(RecallAgentInput.SemanticContent::contentId)
                .containsExactly(closestPublic, personal, thirdPublic)
                .doesNotContain(originFirst, originSecond, overLimit, placed, missingEmbedding, unconsented);
        assertThat(items).extracting(RecallAgentInput.SemanticContent::source)
                .containsExactly(ProposalSource.PUBLIC, ProposalSource.PERSONAL, ProposalSource.PUBLIC);
        assertThat(items.get(1).personalSaveId()).isEqualTo(personalSaveId);
        assertThat(items.get(1).savedAt()).isEqualTo(SAVED_AT);
        assertThat(items).allSatisfy(item -> assertThat(item.summary()).isEqualTo("가😀나다라"));
        var mapper = new ObjectMapper();
        var json = mapper.readTree(mapper.writeValueAsString(items.getFirst()));
        assertThat(json.propertyNames()).containsExactlyInAnyOrder("contentId", "source", "title", "summary", "category");
        assertThat(items.getFirst().personalSaveId()).isNull();
        assertThat(items.getFirst().savedAt()).isNull();
    }

    @Test
    void semanticIsEmptyWhenOriginHasNoEmbedding() {
        savedOrigin(null);
        UUID personal = content("SUCCESS", "AVAILABLE", vector(1, 0));
        save(memberId, personal);
        UUID contributor = member();
        consent(contributor);
        save(contributor, content("SUCCESS", "AVAILABLE", vector(0, 1)));

        assertThat(inputs.prepare(executionId).semanticCandidates()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"FAILED, AVAILABLE", "SUCCESS, UNAVAILABLE"})
    void excludesFailedAndUnavailableContentFromEveryCandidateQuery(String analysisStatus, String sourceStatus) {
        savedOrigin(vector(1, 0));
        UUID badOrigin = content(analysisStatus, sourceStatus, vector(0, 1));
        save(memberId, badOrigin);
        link(badOrigin, originPlaceId);
        UUID contributor = member();
        consent(contributor);
        UUID near = place(37.001, 127.0);
        UUID badPersonal = content(analysisStatus, sourceStatus, vector(1, 0));
        save(memberId, badPersonal);
        link(badPersonal, near);
        UUID badPublic = content(analysisStatus, sourceStatus, vector(1, 0));
        save(contributor, badPublic);
        link(badPublic, near);
        UUID badSemanticPersonal = content(analysisStatus, sourceStatus, vector(1, 0));
        save(memberId, badSemanticPersonal);
        UUID badSemanticPublic = content(analysisStatus, sourceStatus, vector(1, 0));
        save(contributor, badSemanticPublic);

        var input = inputs.prepare(executionId);

        assertThat(input.origin().contents()).hasSize(1).extracting(RecallAgentInput.PersonalContent::contentId)
                .doesNotContain(badOrigin);
        assertThat(input.nearbyPersonalCandidates()).isEmpty();
        assertThat(input.nearbyPublicCandidates()).isEmpty();
        assertThat(input.semanticCandidates()).isEmpty();
        jdbc.update("DELETE FROM content WHERE id <> ?", badOrigin);
        UUID candidate = content("SUCCESS", "AVAILABLE", vector(0, 1));
        save(memberId, candidate);
        assertThat(inputs.prepare(executionId).semanticCandidates()).isEmpty();
    }

    @Test
    void preparationQueriesRunReadOnlyAndTransactionEndsBeforeReturningInput() {
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue();
            assertThat(jdbc.queryForObject("SHOW transaction_read_only", String.class)).isEqualTo("on");
            return invocation.callRealMethod();
        }).when(candidates).findOrigin(any());

        var input = inputs.prepare(executionId);

        assertThat(input.origin().contents()).isEmpty();
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    }

    private void assertPublicContains(UUID nearbyContentId, UUID semanticContentId) {
        var input = inputs.prepare(executionId);
        assertThat(input.nearbyPublicCandidates()).hasSize(1);
        assertThat(input.nearbyPublicCandidates().getFirst().contents())
                .extracting(RecallAgentInput.PublicContent::contentId).containsExactly(nearbyContentId);
        assertThat(input.semanticCandidates()).extracting(RecallAgentInput.SemanticContent::contentId)
                .containsExactly(semanticContentId);
    }

    private void assertPublicEmpty() {
        var input = inputs.prepare(executionId);
        assertThat(input.nearbyPublicCandidates()).isEmpty();
        assertThat(input.semanticCandidates()).isEmpty();
    }

    private UUID member() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO member (id, auth_provider, provider_user_id) VALUES (?, 'KAKAO', ?)",
                id, id.toString());
        return id;
    }

    private void consent(UUID memberId) {
        jdbc.update("""
                INSERT INTO member_consent (id, member_id, consent_type, terms_version, agreed_at)
                VALUES (?, ?, 'PUBLIC_CANDIDATE_CONTRIBUTION', 'v1', ?)
                """, UUID.randomUUID(), memberId, Timestamp.from(SAVED_AT));
    }

    private UUID place(double latitude, double longitude) {
        UUID id = UUID.randomUUID();
        place(id, latitude, longitude);
        return id;
    }

    private void place(UUID id, double latitude, double longitude) {
        jdbc.update("""
                INSERT INTO place (id, name, latitude, longitude, geocoding_provider, geocoding_place_id,
                                   verification_provider, verification_place_id, verified_at)
                VALUES (?, ?, ?, ?, 'LOCATIONIQ', ?, 'KAKAO', ?, ?)
                """, id, "Place " + id, latitude, longitude, id.toString(), id.toString(), Timestamp.from(SAVED_AT));
    }

    private UUID content(String analysisStatus, String sourceStatus, String embedding) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO content (id, video_id, title, summary, category, analysis_status, source_status, embedding)
                VALUES (?, ?, ?, ?, 'FOOD', ?, ?, CAST(? AS vector))
                """, id, id.toString().substring(0, 32), "Title " + id, "가😀나다라마바사", analysisStatus, sourceStatus, embedding);
        return id;
    }

    private UUID save(UUID memberId, UUID contentId) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO personal_save (id, member_id, content_id, saved_at) VALUES (?, ?, ?, ?)",
                id, memberId, contentId, Timestamp.from(SAVED_AT));
        return id;
    }

    private void link(UUID contentId, UUID placeId) {
        jdbc.update("INSERT INTO content_place (id, content_id, place_id, description) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), contentId, placeId, "Description " + contentId);
    }

    private UUID savedAt(UUID memberId, UUID placeId) {
        UUID id = content("SUCCESS", "AVAILABLE", null);
        save(memberId, id);
        link(id, placeId);
        return id;
    }

    private UUID savedOrigin(String embedding) {
        UUID id = content("SUCCESS", "AVAILABLE", embedding);
        save(memberId, id);
        link(id, originPlaceId);
        return id;
    }

    private String vector(double first, double second) {
        return IntStream.range(0, 768).mapToObj(index -> Double.toString(index == 0 ? first : index == 1 ? second : 0))
                .collect(Collectors.joining(",", "[", "]"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableConfigurationProperties(RecallCandidateProperties.class)
    static class PropertiesConfiguration {}
}
