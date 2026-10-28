package com.ktc.chungnam3.remembrall.recall.tool;

import com.ktc.chungnam3.remembrall.content.embedding.EmbeddingClient;
import com.ktc.chungnam3.remembrall.recall.agent.ProposalSource;
import com.ktc.chungnam3.remembrall.recall.config.RecallCandidateProperties;
import com.ktc.chungnam3.remembrall.recall.config.RecallProperties;
import com.ktc.chungnam3.remembrall.recall.config.RecallToolProperties;
import com.ktc.chungnam3.remembrall.recall.evaluation.FakeWeatherClient;
import com.ktc.chungnam3.remembrall.recall.tool.RecallToolResults.*;
import com.ktc.chungnam3.remembrall.recall.weather.WeatherClient;
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
import org.springframework.context.annotation.Bean;
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

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE;

@Testcontainers
@DataJpaTest(properties = {
        "recall.candidates.radius-meters=1000", "recall.candidates.max-summary-length=5",
        "recall.tools.max-radius-meters=2000", "recall.tools.search-place-limit=2",
        "recall.tools.search-content-limit=2", "recall.tools.max-visit-places=3",
        "recall.tools.visit-summary-length=2500", "recall.tools.recent-proposal-window=7d"
})
@AutoConfigureTestDatabase(replace = NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({RecallToolRepository.class, RecallToolService.class, RecallToolIntegrationTest.PropertiesConfiguration.class})
class RecallToolIntegrationTest {
    private static final Instant SAVED_AT = Instant.parse("2026-10-01T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-10T09:00:00Z");

    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired private JdbcTemplate jdbc;
    @Autowired private RecallToolService tools;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoBean private EmbeddingClient embeddings;
    @MockitoBean private WeatherClient weather;
    @MockitoSpyBean private RecallToolRepository repository;
    private UUID memberId;
    private UUID contributor;
    private RecallToolScope scope;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM member");
        jdbc.update("DELETE FROM content");
        jdbc.update("DELETE FROM place");
        reset(embeddings, weather);
        when(embeddings.embedQuery(anyString())).thenCallRealMethod();
        when(embeddings.embed(anyString(), any())).thenReturn(new EmbeddingClient.Result(vector(1, 0), "test-model"));
        when(weather.read(anyDouble(), anyDouble(), any())).thenAnswer(call ->
                new FakeWeatherClient().read(call.getArgument(0), call.getArgument(1), call.getArgument(2)));
        memberId = member();
        contributor = member();
        consent(contributor);
        scope = new RecallToolScope(memberId, place(37.0, 127.0), 37.0, 127.0);
    }

    @Test
    void searchRanksBothListsAndHonorsPersonalPrecedenceAndPrivacy() {
        UUID near = place(37.001, 127.0);
        savedContent(memberId, near, vector(-1, 0));
        UUID best = place(37.004, 127.0);
        UUID publicContent = savedContent(contributor, best, vector(1, 0));
        UUID ownContent = savedContent(memberId, best, vector(0, 1));
        save(contributor, ownContent);
        UUID second = place(37.002, 127.0);
        savedContent(memberId, second, vector(0.8, 0.6));
        UUID publicNoPlace = savedContent(contributor, null, vector(1, 0));
        UUID ownNoPlace = savedContent(memberId, null, vector(0.8, 0.6));
        save(contributor, ownNoPlace);
        UUID privateContent = savedContent(member(), null, vector(1, 0));
        savedContent(memberId, null, null);
        var result = tools.searchCandidates(scope, "  조용한 카페  ", null);
        verify(embeddings).embed("조용한 카페", EmbeddingClient.TaskType.RETRIEVAL_QUERY);
        assertThat(result.places()).extracting(PlaceCandidate::placeId).containsExactly(best, second);
        assertThat(result.places().getFirst().contents()).extracting(ContentCandidate::contentId)
                .containsExactly(publicContent, ownContent);
        assertThat(result.contents()).extracting(ContentCandidate::contentId).containsExactly(publicNoPlace, ownNoPlace)
                .doesNotContain(privateContent);
        assertThat(result.contents()).extracting(ContentCandidate::source).containsExactly(ProposalSource.PUBLIC, ProposalSource.PERSONAL);
        assertThat(result.contents().getLast().savedAt()).isEqualTo(SAVED_AT);
        assertThat(result.contents()).allSatisfy(item -> assertThat(item.summary()).isEqualTo("가😀나다라"));
        assertThat(result.places().getFirst().distanceMeters()).isCloseTo(444.7797, offset(0.001));
    }

    @Test
    void sourceSelectionAndSeenCandidatesAreAppliedBeforeLimits() {
        UUID seenPlace = place(37.001, 127);
        UUID seenContent = savedContent(memberId, seenPlace, vector(1, 0));
        UUID nextPlace = place(37.002, 127);
        UUID nextContent = savedContent(memberId, nextPlace, vector(0.8, 0.6));
        UUID publicPlace = place(37.003, 127);
        savedContent(contributor, publicPlace, vector(1, 0));
        UUID knownNoPlace = savedContent(memberId, null, vector(1, 0));
        UUID nextNoPlace = savedContent(memberId, null, vector(0.8, 0.6));
        var personal = tools.searchCandidates(scope, "카페", null, SearchSource.PERSONAL,
                Set.of(knownNoPlace), Set.of(seenPlace));
        assertThat(personal.places()).extracting(PlaceCandidate::placeId).containsExactly(nextPlace);
        assertThat(personal.contents()).extracting(ContentCandidate::contentId).containsExactly(nextNoPlace);
        var publicOnly = tools.searchCandidates(scope, "카페", null, SearchSource.PUBLIC, Set.of(), Set.of());
        assertThat(publicOnly.places()).extracting(PlaceCandidate::placeId).containsExactly(publicPlace);
        var excludeContents = tools.searchCandidates(scope, "카페", null, SearchSource.ALL,
                Set.of(seenContent, nextContent), Set.of());
        assertThat(excludeContents.places()).extracting(PlaceCandidate::placeId).containsExactly(publicPlace);
    }

    @Test
    void radiusUsesDefaultAndClampsToMaximum() {
        UUID near = place(37.001, 127);
        UUID middle = place(37.013, 127);
        UUID far = place(37.027, 127);
        savedContent(memberId, near, vector(-1, 0));
        savedContent(memberId, middle, vector(0, 1));
        savedContent(memberId, far, vector(1, 0));
        assertThat(tools.searchCandidates(scope, "카페", null).places()).extracting(PlaceCandidate::placeId).containsExactly(near);
        assertThat(tools.searchCandidates(scope, "카페", 10000.0).places()).extracting(PlaceCandidate::placeId).containsExactly(middle, near);
    }

    @Test
    void fullOperatingSummariesAndContextAreReturnedWithoutContributorSaveInformation() {
        UUID place = place(37.001, 127);
        UUID own = savedContent(memberId, place, null);
        UUID pub = savedContent(contributor, place, null);
        String summary = "설명".repeat(700) + " 오늘은 휴무입니다.";
        jdbc.update("UPDATE content SET summary = ?", summary);
        var result = tools.checkVisitConditions(scope, List.of(place));
        assertThat(result.error()).isNull();
        assertThat(result.places()).hasSize(1);
        var conditions = result.places().getFirst().operatingConditions();
        assertThat(conditions).hasSize(2).allSatisfy(item -> assertThat(item.summary()).isEqualTo(summary));
        var personal = conditions.stream().filter(item -> item.contentId().equals(own)).findFirst().orElseThrow();
        var publicItem = conditions.stream().filter(item -> item.contentId().equals(pub)).findFirst().orElseThrow();
        assertThat(personal.referenceAt()).isEqualTo(SAVED_AT);
        assertThat(personal.referenceTimeBasis()).isEqualTo("PERSONAL_SAVE");
        assertThat(publicItem.referenceTimeBasis()).isEqualTo("CONTENT_FIRST_STORED");
        assertThat(publicItem.referenceAt()).isNotNull();
        assertThat(personal.description()).isEqualTo("Description " + own);
        verify(weather).read(scope.latitude(), scope.longitude(), NOW);
    }

    @Test
    void weatherFailureKeepsOperatingConditionsAndRecentSentHistory() {
        UUID place = place(37.001, 127);
        UUID content = savedContent(memberId, place, null);
        Instant sent = NOW.minusSeconds(3600);
        notification(memberId, place, content, "SENT", sent, true);
        notification(memberId, place, content, "FAILED", sent, false);
        notification(memberId, place, content, "PENDING", sent, false);
        notification(memberId, place, content, "SENT", NOW.minusSeconds(8 * 86400), false);
        notification(member(), place, content, "SENT", sent, false);
        doThrow(new IllegalStateException("weather secret")).when(weather).read(anyDouble(), anyDouble(), any());
        var result = tools.checkVisitConditions(scope, List.of(place));
        assertThat(result.weather().available()).isFalse();
        assertThat(result.weather().error()).isEqualTo("WEATHER_UNAVAILABLE");
        var checked = result.places().getFirst();
        assertThat(checked.operatingConditions()).hasSize(1);
        assertThat(checked.recentProposals().hasRecentProposal()).isTrue();
        assertThat(checked.recentProposals().notifications()).hasSize(1);
        assertThat(checked.recentProposals().notifications().getFirst().sentAt()).isEqualTo(sent);
        assertThat(checked.recentProposals().notifications().getFirst().opened()).isTrue();
    }

    @Test
    void unreadSentNotificationIsReportedAndWeatherIsFetchedOnceForSeveralPlaces() {
        UUID first = place(37.001, 127);
        UUID second = place(37.002, 127);
        UUID content = savedContent(memberId, first, null);
        savedContent(memberId, second, null);
        notification(memberId, first, content, "SENT", NOW.minusSeconds(60), false);
        var result = tools.checkVisitConditions(scope, List.of(first, second));
        assertThat(result.places().getFirst().recentProposals().notifications().getFirst().opened()).isFalse();
        assertThat(result.places().getLast().recentProposals().hasRecentProposal()).isFalse();
        verify(weather, times(1)).read(scope.latitude(), scope.longitude(), NOW);
    }

    @Test
    void inaccessibleAndMissingPlacesUseSameErrorAndWithdrawnPublicContentDisappears() {
        UUID pubPlace = place(37.001, 127);
        savedContent(contributor, pubPlace, vector(1, 0));
        assertThat(tools.checkVisitConditions(scope, List.of(pubPlace)).places()).hasSize(1);
        jdbc.update("UPDATE member_consent SET withdrawn_at = CURRENT_TIMESTAMP WHERE member_id = ?", contributor);
        UUID privatePlace = place(37.002, 127);
        savedContent(member(), privatePlace, null);
        var result = tools.checkVisitConditions(scope, List.of(pubPlace, privatePlace, UUID.randomUUID()));
        assertThat(result.places()).isEmpty();
        assertThat(result.errors()).extracting(TargetError::error)
                .containsExactly("대상을 찾을 수 없습니다.", "대상을 찾을 수 없습니다.", "대상을 찾을 수 없습니다.");
        assertThat(tools.searchCandidates(scope, "카페", null).places()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"FAILED, AVAILABLE", "SUCCESS, UNAVAILABLE"})
    void invalidContentStatusIsExcludedFromBothTools(String analysis, String source) {
        UUID place = place(37.001, 127);
        savedContent(memberId, place, vector(1, 0));
        savedContent(contributor, null, vector(1, 0));
        jdbc.update("UPDATE content SET analysis_status = ?, source_status = ?", analysis, source);
        assertThat(tools.searchCandidates(scope, "카페", null).places()).isEmpty();
        assertThat(tools.searchCandidates(scope, "카페", null).contents()).isEmpty();
        assertThat(tools.checkVisitConditions(scope, List.of(place)).places()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\n\t"})
    void blankSearchDoesNotCallEmbedding(String condition) {
        assertThat(tools.searchCandidates(scope, condition, null).error()).isNotBlank();
        verifyNoInteractions(embeddings);
    }

    @Test
    void malformedInputsAndEmbeddingFailureStayErrorResults() {
        assertThat(tools.checkVisitConditions(scope, IntStream.range(0, 4).mapToObj(i -> UUID.randomUUID()).toList()).error()).isNotBlank();
        assertThat(tools.checkVisitConditions(scope, null).error()).isNotBlank();
        assertThat(tools.checkVisitConditions(scope, Arrays.asList((UUID) null)).error()).isNotBlank();
        assertThat(tools.checkVisitConditions(null, List.of()).error()).isNotBlank();
        assertThat(tools.checkVisitConditions(scope, List.of()).places()).isEmpty();
        assertThat(tools.searchCandidates(scope, "카페", -1.0).error()).isNotBlank();
        assertThat(tools.searchCandidates(scope, "카페", Double.NaN).error()).isNotBlank();
        when(embeddings.embed(anyString(), any())).thenThrow(new IllegalStateException("secret"));
        assertThat(tools.searchCandidates(scope, "카페", null).error()).isEqualTo("검색 임베딩을 만들 수 없습니다.");
    }

    @Test
    void embeddingAndWeatherRunOutsideTransactionsAndDatabaseReadsAreReadOnly() {
        when(embeddings.embed(anyString(), any())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return new EmbeddingClient.Result(vector(1, 0), "model");
        });
        doAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue();
            assertThat(jdbc.queryForObject("SHOW transaction_read_only", String.class)).isEqualTo("on");
            return call.callRealMethod();
        }).when(repository).searchPlaces(any(), anyString(), anyDouble(), anyInt(), any(), anySet(), anySet());
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            assertThat(tools.searchCandidates(scope, "카페", null).error()).isNull();
            status.setRollbackOnly();
        });
        UUID place = place(37.001, 127);
        savedContent(memberId, place, null);
        doAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return new FakeWeatherClient().read(call.getArgument(0), call.getArgument(1), call.getArgument(2));
        }).when(weather).read(anyDouble(), anyDouble(), any());
        tools.checkVisitConditions(scope, List.of(place));
    }

    private void notification(UUID member, UUID place, UUID content, String status, Instant sentAt, boolean opened) {
        UUID trigger = UUID.randomUUID();
        jdbc.update("INSERT INTO trigger(id, member_id, place_id) VALUES (?, ?, ?) ON CONFLICT DO NOTHING", trigger, member, place);
        trigger = jdbc.queryForObject("SELECT id FROM trigger WHERE member_id = ? AND place_id = ?", UUID.class, member, place);
        UUID execution = UUID.randomUUID();
        String payload = "{\"items\":[{\"contentId\":\"" + content + "\",\"placeId\":\"" + place + "\",\"source\":\"PERSONAL\"}]}";
        jdbc.update("INSERT INTO recall_execution(id,trigger_id,trigger_event_id,status,result,proposal_type,proposal_payload,agent_version,event_occurred_at) VALUES(?,?,?,'COMPLETED','PROPOSE','PLACE',CAST(? AS jsonb),'test',?)",
                execution, trigger, UUID.randomUUID(), payload, Timestamp.from(sentAt));
        jdbc.update("INSERT INTO notification(id,execution_id,member_id,title,body,status,sent_at,opened_at,failure_code) VALUES(?,?,?,'title','body',?,?,?,?)",
                UUID.randomUUID(), execution, member, status, status.equals("SENT") ? Timestamp.from(sentAt) : null,
                opened ? Timestamp.from(sentAt) : null, status.equals("FAILED") ? "PUSH_FAILED" : null);
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
    @EnableConfigurationProperties({RecallCandidateProperties.class, RecallToolProperties.class, RecallProperties.class})
    static class PropertiesConfiguration {
        @Bean Clock clock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
    }
}
