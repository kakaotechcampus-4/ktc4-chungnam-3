package com.ktc.chungnam3.remembrall.recall.tool;

import com.ktc.chungnam3.remembrall.content.embedding.EmbeddingClient;
import com.ktc.chungnam3.remembrall.recall.agent.ProposalSource;
import com.ktc.chungnam3.remembrall.recall.config.RecallCandidateProperties;
import com.ktc.chungnam3.remembrall.recall.config.RecallToolProperties;
import com.ktc.chungnam3.remembrall.recall.service.WalkingTimeCalculator;
import com.ktc.chungnam3.remembrall.recall.tool.RecallToolResults.ContentCandidate;
import com.ktc.chungnam3.remembrall.recall.tool.RecallToolResults.ContentDetail;
import com.ktc.chungnam3.remembrall.recall.tool.RecallToolResults.PlaceCandidate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE;

@Testcontainers
@DataJpaTest(properties = {
        "recall.candidates.radius-meters=1000", "recall.candidates.max-summary-length=5",
        "recall.tools.max-radius-meters=2000", "recall.tools.search-place-limit=2",
        "recall.tools.search-content-limit=2", "recall.tools.max-content-details=3",
        "recall.tools.details-summary-length=10", "recall.tools.max-place-distances=3",
        "recall.tools.walking-detour-factor=1.5", "recall.tools.walking-speed-meters-per-minute=100"
})
@AutoConfigureTestDatabase(replace = NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({RecallToolRepository.class, RecallToolService.class, WalkingTimeCalculator.class,
        RecallToolIntegrationTest.PropertiesConfiguration.class})
class RecallToolIntegrationTest {

    private static final Instant SAVED_AT = Instant.parse("2026-10-01T00:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired private JdbcTemplate jdbc;
    @Autowired private RecallToolService tools;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoBean private EmbeddingClient embeddings;
    @MockitoSpyBean private RecallToolRepository repository;

    private UUID memberId;
    private UUID contributor;
    private RecallToolScope scope;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM member");
        jdbc.update("DELETE FROM content");
        jdbc.update("DELETE FROM place");
        reset(embeddings);
        when(embeddings.embedQuery(anyString())).thenCallRealMethod();
        when(embeddings.embed(anyString(), any())).thenReturn(new EmbeddingClient.Result(vector(1, 0), "test-model"));
        memberId = member();
        contributor = member();
        consent(contributor);
        scope = new RecallToolScope(memberId, place(37.0, 127.0), 37.0, 127.0);
    }

    @Test
    void searchSplitsListsRanksPlaceByBestContentAndAppliesLimitsAndPersonalPrecedence() {
        UUID geographicNearest = place(37.001, 127.0);
        savedContent(memberId, geographicNearest, vector(-1, 0));
        UUID semanticBest = place(37.004, 127.0);
        UUID publicPlaceContent = savedContent(contributor, semanticBest, vector(1, 0));
        UUID ownPlaceContent = savedContent(memberId, semanticBest, vector(0, 1));
        save(contributor, ownPlaceContent);
        UUID semanticSecond = place(37.002, 127.0);
        savedContent(memberId, semanticSecond, vector(0.8, 0.6));
        UUID publicNoPlace = savedContent(contributor, null, vector(1, 0));
        UUID ownNoPlace = savedContent(memberId, null, vector(0.8, 0.6));
        save(contributor, ownNoPlace);
        UUID overLimit = savedContent(memberId, null, vector(0, 1));
        UUID privateMember = member();
        UUID privateContent = savedContent(privateMember, null, vector(1, 0));
        UUID privatePlace = place(37.0001, 127.0);
        savedContent(privateMember, privatePlace, vector(1, 0));
        UUID noEmbedding = savedContent(memberId, null, null);
        UUID noEmbeddingPlace = place(37.0002, 127.0);
        savedContent(memberId, noEmbeddingPlace, null);

        var result = tools.searchCandidates(scope, "  조용한 카페  ", null);

        assertThat(result.error()).isNull();
        verify(embeddings).embed("조용한 카페", EmbeddingClient.TaskType.RETRIEVAL_QUERY);
        assertThat(result.places()).extracting(PlaceCandidate::placeId).containsExactly(semanticBest, semanticSecond);
        assertThat(result.places().getFirst().contents()).extracting(ContentCandidate::contentId)
                .containsExactly(publicPlaceContent, ownPlaceContent);
        assertThat(result.places().getFirst().contents()).extracting(ContentCandidate::source)
                .containsExactly(ProposalSource.PUBLIC, ProposalSource.PERSONAL);
        assertThat(result.contents()).extracting(ContentCandidate::contentId).containsExactly(publicNoPlace, ownNoPlace)
                .doesNotContain(overLimit, privateContent, noEmbedding, publicPlaceContent, ownPlaceContent);
        assertThat(result.contents()).extracting(ContentCandidate::source)
                .containsExactly(ProposalSource.PUBLIC, ProposalSource.PERSONAL);
        assertThat(result.contents().getLast().savedAt()).isEqualTo(SAVED_AT);
        assertThat(result.contents()).allSatisfy(item -> assertThat(item.summary()).isEqualTo("가😀나다라"));
        var mapper = new ObjectMapper();
        var json = mapper.readTree(mapper.writeValueAsString(result.contents().getFirst()));
        assertThat(json.propertyNames()).containsExactlyInAnyOrder("contentId", "source", "title", "category", "summary");
        assertThat(result.places().getFirst().distanceMeters()).isCloseTo(444.7797, offset(0.001));
    }

    @Test
    void searchUsesDefaultRadiusAndClampsOversizedRadiusBeforeSemanticRanking() {
        UUID near = place(37.001, 127.0);
        UUID middle = place(37.013, 127.0);
        UUID outsideMaximum = place(37.027, 127.0);
        savedContent(memberId, near, vector(-1, 0));
        savedContent(memberId, middle, vector(0, 1));
        savedContent(memberId, outsideMaximum, vector(1, 0));

        assertThat(tools.searchCandidates(scope, "카페", null).places()).extracting(PlaceCandidate::placeId)
                .containsExactly(near);
        assertThat(tools.searchCandidates(scope, "카페", 10000.0).places()).extracting(PlaceCandidate::placeId)
                .containsExactly(middle, near);
        assertThat(tools.searchCandidates(scope, "카페", 50.0).places()).isEmpty();
    }

    @Test
    void withdrawnPublicContributionDisappearsFromAllThreeTools() {
        UUID place = place(37.001, 127.0);
        UUID placed = savedContent(contributor, place, vector(1, 0));
        UUID noPlace = savedContent(contributor, null, vector(1, 0));
        assertThat(tools.searchCandidates(scope, "카페", null).places()).hasSize(1);
        assertThat(tools.searchCandidates(scope, "카페", null).contents()).hasSize(1);
        assertThat(tools.getContentDetails(scope, List.of(placed, noPlace)).contents()).hasSize(2);
        assertThat(tools.getPlaceDistances(scope, List.of(place)).pairs()).hasSize(1);

        jdbc.update("UPDATE member_consent SET withdrawn_at = CURRENT_TIMESTAMP WHERE member_id = ?", contributor);

        assertThat(tools.searchCandidates(scope, "카페", null).places()).isEmpty();
        assertThat(tools.searchCandidates(scope, "카페", null).contents()).isEmpty();
        assertThat(tools.getContentDetails(scope, List.of(placed, noPlace)).contents()).isEmpty();
        assertThat(tools.getPlaceDistances(scope, List.of(place)).pairs()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\n\t"})
    void blankSearchReturnsErrorWithoutEmbedding(String condition) {
        assertThat(tools.searchCandidates(scope, condition, null).error()).isNotBlank();
        verifyNoInteractions(embeddings);
    }

    @Test
    void embeddingFailureAndMalformedVectorReturnErrors() {
        when(embeddings.embed(anyString(), any())).thenThrow(new IllegalStateException("Credential secret must not leak"));
        var result = tools.searchCandidates(scope, "카페", null);
        assertThat(result.error()).isEqualTo("검색 임베딩을 만들 수 없습니다.");
        assertThat(result.places()).isEmpty();
        assertThat(result.contents()).isEmpty();
        doReturn(new EmbeddingClient.Result(new float[2], "model")).when(embeddings).embed(anyString(), any());
        assertThat(tools.searchCandidates(scope, "카페", null).error()).isNotBlank();
        when(embeddings.embed(anyString(), any())).thenReturn(new EmbeddingClient.Result(new float[768], "model"));
        assertThat(tools.searchCandidates(scope, "카페", null).error()).isNotBlank();
        float[] invalid = vector(1, 0);
        invalid[0] = Float.NaN;
        when(embeddings.embed(anyString(), any())).thenReturn(new EmbeddingClient.Result(invalid, "model"));
        assertThat(tools.searchCandidates(scope, "카페", null).error()).isNotBlank();
    }

    @Test
    void detailsResolvePersonalAndPublicWithAllPlacesAndLongerSummariesWithoutPublicSaveInformation() {
        UUID firstPlace = place(37.001, 127.0);
        UUID secondPlace = place(37.002, 127.0);
        UUID own = savedContent(memberId, firstPlace, null);
        link(own, secondPlace);
        save(contributor, own);
        UUID publicContent = savedContent(contributor, firstPlace, null);

        var result = tools.getContentDetails(scope, List.of(own, publicContent));

        assertThat(result.error()).isNull();
        assertThat(result.errors()).isEmpty();
        assertThat(result.contents()).extracting(ContentDetail::contentId).containsExactly(own, publicContent);
        assertThat(result.contents()).extracting(ContentDetail::source)
                .containsExactly(ProposalSource.PERSONAL, ProposalSource.PUBLIC);
        assertThat(result.contents().getFirst().savedAt()).isEqualTo(SAVED_AT);
        assertThat(result.contents().getFirst().places()).extracting(RecallToolResults.ContentPlace::placeId)
                .containsExactlyInAnyOrder(firstPlace, secondPlace);
        assertThat(result.contents().getFirst().places()).allSatisfy(place -> {
            assertThat(place.description()).isEqualTo("Description " + own);
            assertThat(place.distanceMeters()).isBetween(111.0, 223.0);
        });
        assertThat(result.contents()).allSatisfy(item -> assertThat(item.summary()).isEqualTo("가😀나다라마바사아자"));
        assertThat(result.contents().getLast().savedAt()).isNull();
        var mapper = new ObjectMapper();
        var json = mapper.readTree(mapper.writeValueAsString(result.contents().getLast()));
        assertThat(json.propertyNames()).containsExactlyInAnyOrder("contentId", "source", "title", "summary", "category", "places");
    }

    @Test
    void inaccessibleAndMissingContentHaveIdenticalNotFoundErrors() {
        UUID inaccessible = savedContent(member(), null, null);
        UUID missing = UUID.randomUUID();

        var result = tools.getContentDetails(scope, List.of(inaccessible, missing));

        assertThat(result.error()).isNull();
        assertThat(result.contents()).isEmpty();
        assertThat(result.errors()).extracting(RecallToolResults.TargetError::id).containsExactly(inaccessible, missing);
        assertThat(result.errors()).extracting(RecallToolResults.TargetError::error)
                .containsExactly("대상을 찾을 수 없습니다.", "대상을 찾을 수 없습니다.");
    }

    @Test
    void distancesIncludeOriginAndAllUniquePairsWithConfiguredWalkingEstimate() {
        UUID firstPlace = place(37.001, 127.0);
        UUID secondPlace = place(37.002, 127.0);
        savedContent(memberId, firstPlace, null);
        savedContent(contributor, secondPlace, null);

        var result = tools.getPlaceDistances(scope, List.of(firstPlace, secondPlace, scope.triggerPlaceId()));

        assertThat(result.error()).isNull();
        assertThat(result.errors()).isEmpty();
        assertThat(result.pairs()).hasSize(3);
        assertThat(result.pairs()).extracting(pair -> List.of(pair.fromPlaceId(), pair.toPlaceId()))
                .containsExactly(List.of(scope.triggerPlaceId(), firstPlace), List.of(scope.triggerPlaceId(), secondPlace),
                        List.of(firstPlace, secondPlace));
        assertThat(result.pairs().getFirst().straightLineMeters()).isCloseTo(111.1949, offset(0.001));
        assertThat(result.pairs().get(1).straightLineMeters()).isCloseTo(222.3899, offset(0.001));
        assertThat(result.pairs()).allSatisfy(pair -> {
            assertThat(pair.walkingTimeEstimated()).isTrue();
            assertThat(pair.estimatedWalkingMinutes()).isCloseTo(pair.straightLineMeters() * 1.5 / 100, offset(0.000001));
        });
    }

    @Test
    void inaccessibleAndMissingPlacesGetErrorsWhileAccessiblePairsRemain() {
        UUID accessible = place(37.001, 127.0);
        UUID inaccessible = place(37.002, 127.0);
        UUID missing = UUID.randomUUID();
        savedContent(memberId, accessible, null);
        savedContent(member(), inaccessible, null);

        var result = tools.getPlaceDistances(scope, List.of(accessible, inaccessible, missing));

        assertThat(result.pairs()).hasSize(1);
        assertThat(result.pairs().getFirst().toPlaceId()).isEqualTo(accessible);
        assertThat(result.errors()).extracting(RecallToolResults.TargetError::id).containsExactly(inaccessible, missing);
        assertThat(result.errors()).extracting(RecallToolResults.TargetError::error)
                .containsExactly("대상을 찾을 수 없습니다.", "대상을 찾을 수 없습니다.");
    }

    @ParameterizedTest
    @CsvSource({"FAILED, AVAILABLE", "SUCCESS, UNAVAILABLE"})
    void badContentStatusIsExcludedFromEveryTool(String analysisStatus, String sourceStatus) {
        UUID personalPlace = place(37.001, 127.0);
        UUID publicPlace = place(37.002, 127.0);
        UUID personal = savedContent(memberId, personalPlace, vector(1, 0));
        UUID publicContent = savedContent(contributor, publicPlace, vector(1, 0));
        savedContent(memberId, null, vector(1, 0));
        savedContent(contributor, null, vector(1, 0));
        jdbc.update("UPDATE content SET analysis_status = ?, source_status = ?", analysisStatus, sourceStatus);

        var search = tools.searchCandidates(scope, "카페", null);
        assertThat(search.places()).isEmpty();
        assertThat(search.contents()).isEmpty();
        var details = tools.getContentDetails(scope, List.of(personal, publicContent));
        assertThat(details.contents()).isEmpty();
        assertThat(details.errors()).hasSize(2);
        var distances = tools.getPlaceDistances(scope, List.of(personalPlace, publicPlace));
        assertThat(distances.pairs()).isEmpty();
        assertThat(distances.errors()).hasSize(2);
    }

    @Test
    void invalidArgumentsAndLimitsReturnErrorsWithoutThrowing() {
        var oversized = IntStream.range(0, 4).mapToObj(index -> UUID.randomUUID()).toList();
        assertThat(tools.getContentDetails(scope, oversized).error()).isNotBlank();
        assertThat(tools.getPlaceDistances(scope, oversized).error()).isNotBlank();
        assertThat(tools.getContentDetails(scope, null).error()).isNotBlank();
        assertThat(tools.getPlaceDistances(scope, Arrays.asList((UUID) null)).error()).isNotBlank();
        assertThat(tools.searchCandidates(scope, "카페", -1.0).error()).isNotBlank();
        assertThat(tools.searchCandidates(scope, "카페", Double.NaN).error()).isNotBlank();
        assertThat(tools.searchCandidates(scope, "카페", null).error()).isNull();
        assertThat(tools.getContentDetails(null, List.of()).error()).isNotBlank();
        assertThat(tools.getPlaceDistances(null, List.of()).error()).isNotBlank();
        assertThat(tools.searchCandidates(null, "카페", null).error()).isNotBlank();
        assertThat(tools.getContentDetails(scope, List.of()).contents()).isEmpty();
        assertThat(tools.getPlaceDistances(scope, List.of()).pairs()).isEmpty();
    }

    @Test
    void embeddingRunsOutsideCallerTransactionAndSearchQueriesRunReadOnly() {
        when(embeddings.embed(anyString(), any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return new EmbeddingClient.Result(vector(1, 0), "model");
        });
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue();
            assertThat(jdbc.queryForObject("SHOW transaction_read_only", String.class)).isEqualTo("on");
            return invocation.callRealMethod();
        }).when(repository).searchPlaces(any(), anyString(), anyDouble(), anyInt());
        var transactions = new TransactionTemplate(transactionManager);

        transactions.executeWithoutResult(status -> {
            assertThat(tools.searchCandidates(scope, "카페", null).error()).isNull();
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            status.setRollbackOnly();
        });
    }

    private UUID member() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO member (id, auth_provider, provider_user_id) VALUES (?, 'KAKAO', ?)", id, id.toString());
        return id;
    }

    private void consent(UUID id) {
        jdbc.update("""
                INSERT INTO member_consent (id, member_id, consent_type, terms_version, agreed_at)
                VALUES (?, ?, 'PUBLIC_CANDIDATE_CONTRIBUTION', 'v1', ?)
                """, UUID.randomUUID(), id, Timestamp.from(SAVED_AT));
    }

    private UUID place(double latitude, double longitude) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO place (id, name, latitude, longitude, geocoding_provider, geocoding_place_id,
                                   verification_provider, verification_place_id, verified_at)
                VALUES (?, ?, ?, ?, 'LOCATIONIQ', ?, 'KAKAO', ?, ?)
                """, id, "Place " + id, latitude, longitude, id.toString(), id.toString(), Timestamp.from(SAVED_AT));
        return id;
    }

    private UUID savedContent(UUID memberId, UUID placeId, float[] embedding) {
        UUID id = UUID.randomUUID();
        String literal = embedding == null ? null : IntStream.range(0, embedding.length)
                .mapToObj(index -> Float.toString(embedding[index])).collect(Collectors.joining(",", "[", "]"));
        jdbc.update("""
                INSERT INTO content (id, video_id, title, summary, category, analysis_status, source_status, embedding)
                VALUES (?, ?, ?, ?, 'FOOD', 'SUCCESS', 'AVAILABLE', CAST(? AS vector))
                """, id, id.toString().substring(0, 32), "Title " + id, "가😀나다라마바사아자차카타파하", literal);
        save(memberId, id);
        if (placeId != null) {
            link(id, placeId);
        }
        return id;
    }

    private void save(UUID memberId, UUID contentId) {
        jdbc.update("INSERT INTO personal_save (id, member_id, content_id, saved_at) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), memberId, contentId, Timestamp.from(SAVED_AT));
    }

    private void link(UUID contentId, UUID placeId) {
        jdbc.update("INSERT INTO content_place (id, content_id, place_id, description) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), contentId, placeId, "Description " + contentId);
    }

    private float[] vector(double first, double second) {
        float[] result = new float[768];
        result[0] = (float) first;
        result[1] = (float) second;
        return result;
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableConfigurationProperties({RecallCandidateProperties.class, RecallToolProperties.class})
    static class PropertiesConfiguration {}
}
