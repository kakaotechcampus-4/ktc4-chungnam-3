package com.ktc.chungnam3.remembrall.personalsave;

import com.ktc.chungnam3.remembrall.auth.security.ApiAuthenticationEntryPoint;
import com.ktc.chungnam3.remembrall.auth.security.SecurityConfig;
import com.ktc.chungnam3.remembrall.auth.security.SessionAuthenticationFilter;
import com.ktc.chungnam3.remembrall.auth.token.SessionTokenHasher;
import com.ktc.chungnam3.remembrall.common.exception.ApiErrorResponse;
import com.ktc.chungnam3.remembrall.common.exception.ErrorCode;
import com.ktc.chungnam3.remembrall.common.exception.GlobalExceptionHandler;
import com.ktc.chungnam3.remembrall.content.dto.AnalysisOutcome;
import com.ktc.chungnam3.remembrall.content.service.ContentPersistenceService;
import com.ktc.chungnam3.remembrall.domain.content.Content;
import com.ktc.chungnam3.remembrall.domain.content.ContentAnalysisFailureCode;
import com.ktc.chungnam3.remembrall.domain.content.ContentAnalysisStatus;
import com.ktc.chungnam3.remembrall.domain.content.ContentSourceStatus;
import com.ktc.chungnam3.remembrall.domain.contentplace.ContentPlace;
import com.ktc.chungnam3.remembrall.domain.device.Device;
import com.ktc.chungnam3.remembrall.domain.member.AuthProvider;
import com.ktc.chungnam3.remembrall.domain.member.Member;
import com.ktc.chungnam3.remembrall.domain.personalsave.PersonalSave;
import com.ktc.chungnam3.remembrall.domain.place.GeocodingProvider;
import com.ktc.chungnam3.remembrall.domain.place.Place;
import com.ktc.chungnam3.remembrall.domain.trigger.Trigger;
import com.ktc.chungnam3.remembrall.personalsave.config.PersonalSaveConfiguration;
import com.ktc.chungnam3.remembrall.personalsave.config.PersonalSaveProperties;
import com.ktc.chungnam3.remembrall.personalsave.controller.PersonalSaveController;
import com.ktc.chungnam3.remembrall.personalsave.dto.PersonalSaveResponse;
import com.ktc.chungnam3.remembrall.personalsave.repository.PersonalSaveReadRepository;
import com.ktc.chungnam3.remembrall.personalsave.service.PersonalSaveAnalysisService;
import com.ktc.chungnam3.remembrall.personalsave.service.PersonalSaveService;
import com.ktc.chungnam3.remembrall.repository.ContentPlaceRepository;
import com.ktc.chungnam3.remembrall.repository.ContentRepository;
import com.ktc.chungnam3.remembrall.repository.DeviceRepository;
import com.ktc.chungnam3.remembrall.repository.MemberRepository;
import com.ktc.chungnam3.remembrall.repository.PersonalSaveRepository;
import com.ktc.chungnam3.remembrall.repository.PlaceRepository;
import com.ktc.chungnam3.remembrall.repository.TriggerRepository;
import com.ktc.chungnam3.remembrall.save.SavePipelineService;
import com.ktc.chungnam3.remembrall.save.dto.SavePipelineRequestDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
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
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@Testcontainers
@SpringBootTest(classes = PersonalSaveIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.ai.google.genai.api-key=test-api-key", "spring.ai.google.genai.embedding.api-key=test-api-key",
        "personal-save.analysis-concurrency=1", "personal-save.analysis-queue-capacity=1"
})
class PersonalSaveIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-10-18T00:00:00Z");
    private static final String VIDEO = "dQw4w9WgXcQ";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired private JdbcTemplate jdbc;
    @Autowired private MemberRepository members;
    @Autowired private DeviceRepository devices;
    @Autowired private SessionTokenHasher hasher;
    @Autowired private ObjectMapper mapper;
    @Autowired private MutableClock clock;
    @Autowired private ContentPersistenceService persistence;
    @Autowired private ContentRepository contents;
    @Autowired private PersonalSaveService saves;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private PersonalSaveProperties properties;
    @Autowired @Qualifier("personalSaveAnalysisExecutor") private ThreadPoolTaskExecutor executor;
    @MockitoBean private SavePipelineService pipeline;
    @MockitoSpyBean private NamedParameterJdbcTemplate namedJdbc;
    @LocalServerPort private int port;

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ConcurrentLinkedQueue<CountDownLatch> gates = new ConcurrentLinkedQueue<>();
    private UUID memberId;
    private String token;

    @BeforeEach
    void setUp() {
        clock.now.set(NOW);
        jdbc.update("DELETE FROM member");
        jdbc.update("DELETE FROM content");
        jdbc.update("DELETE FROM place");
        memberId = member();
        token = session(memberId);
        doAnswer(call -> {
            persistence.applyOutcome(call.getArgument(0), success(List.of()));
            return null;
        }).when(pipeline).saveAndPersist(any(), any());
    }

    @AfterEach
    void finishTasks() throws Exception {
        gates.forEach(CountDownLatch::countDown);
        await(() -> executor.getActiveCount() == 0 && executor.getQueueSize() == 0);
    }

    @Test
    void firstSaveReturns201AndRepeatReturns200WithTheSameSaveAndTimestamp() throws Exception {
        var first = post(VIDEO, token);
        assertThat(first.statusCode()).isEqualTo(201);
        var created = saved(first);
        assertThat(created.savedAt()).isEqualTo(NOW);
        assertThat(created.personalSaveId()).isNotNull();
        assertThat(created.analysisStatus()).isEqualTo(ContentAnalysisStatus.ANALYZING);
        await(() -> status(created.contentId()).equals("SUCCESS"));
        clock.now.set(NOW.plusSeconds(60));

        var repeat = post(VIDEO, token);

        assertThat(repeat.statusCode()).isEqualTo(200);
        assertThat(saved(repeat)).isEqualTo(new PersonalSaveResponse(created.personalSaveId(), created.contentId(),
                ContentAnalysisStatus.SUCCESS, NOW));
        assertThat(count("content")).isEqualTo(1);
        assertThat(count("personal_save")).isEqualTo(1);
        verify(pipeline).saveAndPersist(any(), any());
    }

    @Test
    void respondsBeforeAnalysisCompletesAndWorkerSeesCommittedRowsOutsideATransaction() throws Exception {
        var started = new CountDownLatch(1);
        var release = gate();
        var observed = new AtomicReference<UUID>();
        doAnswer(call -> {
            UUID contentId = call.getArgument(0);
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM content WHERE id = ?", Integer.class, contentId)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM personal_save WHERE content_id = ?", Integer.class, contentId)).isEqualTo(1);
            observed.set(contentId);
            started.countDown();
            assertThat(release.await(20, TimeUnit.SECONDS)).isTrue();
            persistence.applyOutcome(contentId, success(List.of(place("commit-test"))));
            return null;
        }).when(pipeline).saveAndPersist(any(), any());

        var response = post(VIDEO, token);

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        var saved = saved(response);
        assertThat(observed.get()).isEqualTo(saved.contentId());
        assertThat(status(saved.contentId())).isEqualTo("ANALYZING");
        assertThat(count("trigger")).isZero();
        assertThat(release.getCount()).isEqualTo(1);
        release.countDown();
        await(() -> status(saved.contentId()).equals("SUCCESS"));
        assertThat(count("content_place")).isEqualTo(1);
        assertThat(count("trigger")).isEqualTo(1);
    }

    @Test
    void rollbackDoesNotSubmitAnalysisOrLeaveSavedRows() {
        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            saves.save(memberId, "https://youtu.be/" + VIDEO);
            verify(pipeline, never()).saveAndPersist(any(), any());
            transaction.setRollbackOnly();
        });

        assertThat(count("content")).isZero();
        assertThat(count("personal_save")).isZero();
        assertThat(executor.getQueueSize()).isZero();
        verify(pipeline, never()).saveAndPersist(any(), any());
    }

    @Test
    void concurrentMembersClaimOneAnalysisAndCompletionCreatesBothTriggers() throws Exception {
        UUID other = member();
        String otherToken = session(other);
        var release = gate();
        var calls = new AtomicInteger();
        doAnswer(call -> {
            calls.incrementAndGet();
            assertThat(release.await(20, TimeUnit.SECONDS)).isTrue();
            persistence.applyOutcome(call.getArgument(0), success(List.of(place("concurrent"))));
            return null;
        }).when(pipeline).saveAndPersist(any(), any());
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);

        try (var threads = Executors.newFixedThreadPool(2)) {
            var responses = List.of(token, otherToken).stream().map(session -> threads.submit(() -> {
                ready.countDown();
                assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                return post(VIDEO, session);
            })).toList();
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            UUID contentId = null;
            for (var response : responses) {
                var result = response.get(10, TimeUnit.SECONDS);
                assertThat(result.statusCode()).isEqualTo(201);
                var body = saved(result);
                if (contentId != null) {
                    assertThat(body.contentId()).isEqualTo(contentId);
                }
                contentId = body.contentId();
            }
            await(() -> calls.get() == 1);
            assertThat(count("content")).isEqualTo(1);
            assertThat(count("personal_save")).isEqualTo(2);
            release.countDown();
            await(() -> count("trigger") == 2);
            assertThat(calls.get()).isEqualTo(1);
        } finally {
            release.countDown();
        }
    }

    @Test
    void alreadyAnalyzedContentCreatesTriggerWithoutAnalysis() throws Exception {
        var original = persistence.saveAndClaim(member(), VIDEO);
        persistence.applyOutcome(original.contentId(), success(List.of(place("existing"))));

        var response = post(VIDEO, token);

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(saved(response).analysisStatus()).isEqualTo(ContentAnalysisStatus.SUCCESS);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM trigger WHERE member_id = ?", Integer.class, memberId)).isEqualTo(1);
        verify(pipeline, never()).saveAndPersist(any(), any());
    }

    @Test
    void geminiFailureIsReclaimedAndAnalyzedAgainOnTheNextSave() throws Exception {
        var calls = new AtomicInteger();
        doAnswer(call -> {
            UUID contentId = call.getArgument(0);
            if (calls.incrementAndGet() == 1) {
                persistence.applyOutcome(contentId, new AnalysisOutcome(ContentAnalysisStatus.FAILED,
                        ContentAnalysisFailureCode.EXTRACTION_API_ERROR, null, null, null, null, null, null, List.of()));
            } else {
                persistence.applyOutcome(contentId, success(List.of(place("retried"))));
            }
            return null;
        }).when(pipeline).saveAndPersist(any(), any());
        var first = saved(post(VIDEO, token));
        await(() -> status(first.contentId()).equals("FAILED"));

        var retry = post(VIDEO, token);

        assertThat(retry.statusCode()).isEqualTo(200);
        assertThat(saved(retry).personalSaveId()).isEqualTo(first.personalSaveId());
        await(() -> status(first.contentId()).equals("SUCCESS"));
        assertThat(calls.get()).isEqualTo(2);
        assertThat(count("trigger")).isEqualTo(1);
    }

    @Test
    void queuedWorkRefreshesStartedAtWhenItActuallyStartsAndUsesCanonicalUrl() throws Exception {
        var started = new CountDownLatch(1);
        var release = gate();
        var refreshed = new AtomicReference<Instant>();
        var calls = new AtomicInteger();
        doAnswer(call -> {
            UUID id = call.getArgument(0);
            var request = call.getArgument(1, SavePipelineRequestDto.class);
            assertThat(request.alreadyAsked()).isFalse();
            if (calls.incrementAndGet() == 1) {
                started.countDown();
                assertThat(release.await(20, TimeUnit.SECONDS)).isTrue();
            } else {
                refreshed.set(jdbc.queryForObject("SELECT analysis_started_at FROM content WHERE id = ?", Timestamp.class, id).toInstant());
                assertThat(request.youtubeUrl()).isEqualTo("https://www.youtube.com/watch?v=secondVideo");
            }
            persistence.applyOutcome(id, success(List.of()));
            return null;
        }).when(pipeline).saveAndPersist(any(), any());
        post(VIDEO, token);
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        var second = saved(request("POST", "", token, Map.of("url", "https://youtu.be/secondVideo?si=tracking&t=15")));
        assertThat(jdbc.queryForObject("SELECT analysis_started_at FROM content WHERE id = ?", Timestamp.class,
                second.contentId()).toInstant()).isEqualTo(NOW);
        clock.now.set(NOW.plusSeconds(300));

        release.countDown();

        await(() -> status(second.contentId()).equals("SUCCESS"));
        assertThat(refreshed.get()).isEqualTo(NOW.plusSeconds(300));
    }

    @Test
    void fullQueueReleasesClaimAndNextSaveCanSubmitItAgain() throws Exception {
        var release = gate();
        var started = new CountDownLatch(1);
        doAnswer(call -> {
            started.countDown();
            assertThat(release.await(20, TimeUnit.SECONDS)).isTrue();
            persistence.applyOutcome(call.getArgument(0), success(List.of()));
            return null;
        }).when(pipeline).saveAndPersist(any(), any());
        post("running", token);
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        post("queued", token);
        assertThat(executor.getQueueSize()).isEqualTo(1);

        var rejected = post("rejected", token);

        assertThat(rejected.statusCode()).isEqualTo(201);
        var pending = saved(rejected);
        assertThat(status(pending.contentId())).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT analysis_started_at FROM content WHERE id = ?", Timestamp.class,
                pending.contentId())).isNull();
        release.countDown();
        await(() -> executor.getActiveCount() == 0 && executor.getQueueSize() == 0);
        var retry = post("rejected", token);
        assertThat(retry.statusCode()).isEqualTo(200);
        assertThat(saved(retry).personalSaveId()).isEqualTo(pending.personalSaveId());
        await(() -> status(pending.contentId()).equals("SUCCESS"));
    }

    @Test
    void taskExceptionDoesNotOverwriteFailureAlreadyPersistedByThePipeline() throws Exception {
        doAnswer(call -> {
            persistence.applyOutcome(call.getArgument(0), new AnalysisOutcome(ContentAnalysisStatus.FAILED,
                    ContentAnalysisFailureCode.EXTRACTION_API_ERROR, null, null, null, null, null, null, List.of()));
            throw new IllegalStateException("fake failure after persistence");
        }).when(pipeline).saveAndPersist(any(), any());

        var response = post(VIDEO, token);

        assertThat(response.statusCode()).isEqualTo(201);
        UUID id = saved(response).contentId();
        await(() -> executor.getActiveCount() == 0);
        assertThat(status(id)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT last_analysis_error_code FROM content WHERE id = ?", String.class, id))
                .isEqualTo("EXTRACTION_API_ERROR");
    }

    @Test
    void listUsesOneQueryWithPlaceCountsAndTruncatesSummaryByCharacters() throws Exception {
        UUID contentId = content("list", "SUCCESS");
        UUID saveId = savedRow(memberId, contentId, NOW, UUID.randomUUID());
        link(contentId, "one");
        link(contentId, "two");
        savedRow(member(), content("private", "SUCCESS"), NOW.plusSeconds(30), UUID.randomUUID());
        jdbc.update("UPDATE content SET title = '제목', summary = ?, category = '여행' WHERE id = ?",
                "😀".repeat(250), contentId);
        clearInvocations(namedJdbc);

        var page = saves.list(memberId, null, null);

        assertThat(page.items()).hasSize(1);
        var item = page.items().getFirst();
        assertThat(item.personalSaveId()).isEqualTo(saveId);
        assertThat(item.contentId()).isEqualTo(contentId);
        assertThat(item.videoId()).isEqualTo("list");
        assertThat(item.title()).isEqualTo("제목");
        assertThat(item.summary()).isEqualTo("😀".repeat(200));
        assertThat(item.placeCount()).isEqualTo(2);
        assertThat(page.nextCursor()).isNull();
        verify(namedJdbc).query(anyString(), any(SqlParameterSource.class),
                org.mockito.ArgumentMatchers.<RowMapper<PersonalSaveResponse.Item>>any());
    }

    @Test
    void cursorPaginationHasNoGapsOrDuplicatesWithTimestampAndUuidTies() throws Exception {
        var tiedIds = List.of("80000000-0000-0000-0000-000000000000", "00000000-0000-0000-0000-000000000001",
                "7fffffff-ffff-ffff-ffff-ffffffffffff", "00000000-0000-0000-0000-000000000002");
        var expected = new ArrayList<UUID>();
        expected.add(savedRow(memberId, content("newest", "PENDING"), NOW.plusSeconds(1), UUID.randomUUID()));
        for (int index = 0; index < tiedIds.size(); index++) {
            savedRow(memberId, content("tie" + index, "SUCCESS"), NOW, UUID.fromString(tiedIds.get(index)));
        }
        expected.addAll(tiedIds.stream().sorted().map(UUID::fromString).toList());
        expected.add(savedRow(memberId, content("oldest", "FAILED"), NOW.minusSeconds(1), UUID.randomUUID()));
        savedRow(member(), content("other", "SUCCESS"), NOW.plusSeconds(2), UUID.randomUUID());
        var actual = new ArrayList<UUID>();
        String cursor = null;
        do {
            var response = request("GET", "?size=2" + (cursor == null ? "" : "&cursor=" + cursor), token, null);
            assertThat(response.statusCode()).isEqualTo(200);
            var page = mapper.readValue(response.body(), PersonalSaveResponse.Page.class);
            actual.addAll(page.items().stream().map(PersonalSaveResponse.Item::personalSaveId).toList());
            cursor = page.nextCursor();
        } while (cursor != null);

        assertThat(actual).containsExactlyElementsOf(expected).doesNotHaveDuplicates();
    }

    @Test
    void defaultPageSizeAndEmptyPageHaveExpectedCursors() throws Exception {
        var empty = mapper.readValue(request("GET", "", token, null).body(), PersonalSaveResponse.Page.class);
        assertThat(empty.items()).isEmpty();
        assertThat(empty.nextCursor()).isNull();
        for (int index = 0; index < 21; index++) {
            savedRow(memberId, content("page" + index, "PENDING"), NOW.plusSeconds(index), UUID.randomUUID());
        }

        var page = mapper.readValue(request("GET", "", token, null).body(), PersonalSaveResponse.Page.class);

        assertThat(page.items()).hasSize(20);
        assertThat(page.nextCursor()).isNotBlank();
        assertThat(properties.maxPageSize()).isEqualTo(100);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "SUCCESS", "PARTIAL", "FAILED"})
    void workerUpdatesAreConditionalOnAnalyzingStatus(String status) {
        UUID id = content("conditional", status);
        jdbc.update("UPDATE content SET analysis_started_at = ? WHERE id = ?", Timestamp.from(NOW), id);

        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            assertThat(contents.refreshAnalysisStartedAt(id, NOW.plusSeconds(1))).isZero();
            assertThat(contents.releaseAnalysisClaim(id, NOW.plusSeconds(1))).isZero();
        });

        assertThat(status(id)).isEqualTo(status);
        assertThat(jdbc.queryForObject("SELECT analysis_started_at FROM content WHERE id = ?", Timestamp.class, id)
                .toInstant()).isEqualTo(NOW);
    }

    @ParameterizedTest
    @ValueSource(strings = {"?size=0", "?size=-1", "?size=101", "?size=abc", "?cursor=bad", "?cursor="})
    void invalidPaginationReturns400(String query) throws Exception {
        assertError(request("GET", query, token, null), 400, ErrorCode.INVALID_REQUEST);
    }

    @ParameterizedTest
    @CsvSource({"PARTIAL,PLACE_SEARCH_API_ERROR", "FAILED,EXTRACTION_API_ERROR", "SUCCESS,", "ANALYZING,", "PENDING,"})
    void detailIncludesOwnPlacesAndOnlyTerminalFailureCodes(String status, String expectedCode) throws Exception {
        UUID contentId = content("detail", status);
        jdbc.update("UPDATE content SET title = '상세 제목', summary = '전체 요약', category = '카페', last_analysis_error_code = ? WHERE id = ?",
                expectedCode == null ? "EXTRACTION_API_ERROR" : expectedCode, contentId);
        UUID saveId = savedRow(memberId, contentId, NOW, UUID.randomUUID());
        UUID placeId = link(contentId, "detail-place");

        var response = request("GET", "/" + saveId, token, null);

        assertThat(response.statusCode()).isEqualTo(200);
        var detail = mapper.readValue(response.body(), PersonalSaveResponse.Detail.class);
        assertThat(detail.personalSaveId()).isEqualTo(saveId);
        assertThat(detail.contentId()).isEqualTo(contentId);
        assertThat(detail.videoId()).isEqualTo("detail");
        assertThat(detail.sourceUrl()).isEqualTo("https://www.youtube.com/watch?v=detail");
        assertThat(detail.title()).isEqualTo("상세 제목");
        assertThat(detail.summary()).isEqualTo("전체 요약");
        assertThat(detail.category()).isEqualTo("카페");
        assertThat(detail.analysisStatus().name()).isEqualTo(status);
        assertThat(detail.analysisFailureCode()).isEqualTo(expectedCode);
        assertThat(detail.savedAt()).isEqualTo(NOW);
        assertThat(detail.places()).containsExactly(new PersonalSaveResponse.Place(placeId, "장소 detail-place",
                "서울시", 37, 127, "영상 장소 소개"));
        assertError(request("GET", "/" + saveId, session(member()), null), 404, ErrorCode.NOT_FOUND);
        assertError(request("GET", "/" + UUID.randomUUID(), token, null), 404, ErrorCode.NOT_FOUND);
    }

    @Test
    void detailWithNoPlacesReturnsAnEmptyArray() throws Exception {
        UUID id = savedRow(memberId, content("empty-places", "PENDING"), NOW, UUID.randomUUID());

        var response = request("GET", "/" + id, token, null);

        assertThat(mapper.readValue(response.body(), PersonalSaveResponse.Detail.class).places()).isEmpty();
    }

    @Test
    void deleteUsesExistingOwnershipAndTriggerCleanupAndIsNotIdempotent() throws Exception {
        var first = persistence.saveAndClaim(memberId, "first");
        persistence.applyOutcome(first.contentId(), success(List.of(place("shared-place"))));
        var second = persistence.saveAndClaim(memberId, "second");
        persistence.applyOutcome(second.contentId(), success(List.of(place("shared-place"))));
        assertThat(count("trigger")).isEqualTo(1);
        String otherSession = session(member());
        assertError(request("DELETE", "/" + first.personalSaveId(), otherSession, null), 404, ErrorCode.NOT_FOUND);
        assertThat(count("personal_save")).isEqualTo(2);

        var deleted = request("DELETE", "/" + first.personalSaveId(), token, null);

        assertThat(deleted.statusCode()).isEqualTo(204);
        assertThat(deleted.body()).isEmpty();
        assertThat(count("trigger")).isEqualTo(1);
        assertError(request("GET", "/" + first.personalSaveId(), token, null), 404, ErrorCode.NOT_FOUND);
        assertError(request("DELETE", "/" + first.personalSaveId(), token, null), 404, ErrorCode.NOT_FOUND);
        assertThat(request("DELETE", "/" + second.personalSaveId(), token, null).statusCode()).isEqualTo(204);
        assertThat(count("trigger")).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"not a url", "https://example.com/video", "https://youtube.com/watch", "https://youtu.be/",
            "https://youtu.be////", "", " ", "https://youtube.com/watch?v=invalid%2Fvalue"})
    void invalidUrlsReturn400WithoutSavingOrStartingAnalysis(String url) throws Exception {
        assertError(request("POST", "", token, Map.of("url", url)), 400, ErrorCode.INVALID_REQUEST);
        assertThat(count("content")).isZero();
        verify(pipeline, never()).saveAndPersist(any(), any());
    }

    @Test
    void missingUrlReturns400AndAllEndpointsRequireSession() throws Exception {
        assertError(request("POST", "", token, Map.of()), 400, ErrorCode.INVALID_REQUEST);
        assertError(request("POST", "", null, Map.of("url", "https://youtu.be/" + VIDEO)), 401, ErrorCode.INVALID_SESSION);
        assertError(request("GET", "", null, null), 401, ErrorCode.INVALID_SESSION);
        assertError(request("GET", "/" + UUID.randomUUID(), null, null), 401, ErrorCode.INVALID_SESSION);
        assertError(request("DELETE", "/" + UUID.randomUUID(), null, null), 401, ErrorCode.INVALID_SESSION);
    }

    private HttpResponse<String> post(String videoId, String session) throws Exception {
        return request("POST", "", session, Map.of("url", "https://youtu.be/" + videoId + "?si=tracking"));
    }

    private HttpResponse<String> request(String method, String suffix, String session, Object body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/personal-saves" + suffix))
                .timeout(Duration.ofSeconds(5));
        if (session != null) {
            request.header("Authorization", "Bearer " + session);
        }
        var publisher = body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body));
        if (body != null) {
            request.header("Content-Type", "application/json");
        }
        return client.send(request.method(method, publisher).build(), HttpResponse.BodyHandlers.ofString());
    }

    private PersonalSaveResponse saved(HttpResponse<String> response) {
        assertThat(response.statusCode()).isIn(200, 201);
        return mapper.readValue(response.body(), PersonalSaveResponse.class);
    }

    private void assertError(HttpResponse<String> response, int status, ErrorCode code) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(mapper.readValue(response.body(), ApiErrorResponse.class)).isEqualTo(ApiErrorResponse.from(code));
    }

    private UUID member() {
        return members.save(Member.create(AuthProvider.KAKAO, UUID.randomUUID().toString(), NOW)).getId();
    }

    private String session(UUID owner) {
        String session = UUID.randomUUID().toString();
        devices.save(Device.create(owner, hasher.hash(session), NOW.plusSeconds(3600), NOW));
        return session;
    }

    private UUID content(String videoId, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO content (id, video_id, analysis_status) VALUES (?, ?, ?)", id, videoId, status);
        return id;
    }

    private UUID savedRow(UUID owner, UUID contentId, Instant at, UUID id) {
        jdbc.update("INSERT INTO personal_save (id, member_id, content_id, saved_at) VALUES (?, ?, ?, ?)",
                id, owner, contentId, Timestamp.from(at));
        return id;
    }

    private UUID link(UUID contentId, String key) {
        UUID placeId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO place (id, name, address, latitude, longitude, geocoding_provider, geocoding_place_id,
                                   verification_provider, verification_place_id, verified_at)
                VALUES (?, ?, '서울시', 37, 127, 'LOCATIONIQ', ?, 'KAKAO', ?, ?)
                """, placeId, "장소 " + key, key, key, Timestamp.from(NOW));
        jdbc.update("INSERT INTO content_place (id, content_id, place_id, description) VALUES (?, ?, ?, '영상 장소 소개')",
                UUID.randomUUID(), contentId, placeId);
        return placeId;
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }

    private String status(UUID contentId) {
        return jdbc.queryForObject("SELECT analysis_status FROM content WHERE id = ?", String.class, contentId);
    }

    private AnalysisOutcome success(List<AnalysisOutcome.PlaceResult> places) {
        return new AnalysisOutcome(ContentAnalysisStatus.SUCCESS, null, ContentSourceStatus.AVAILABLE,
                "테스트 영상", "테스트 요약", "여행", "test-v1", clock.instant(), places);
    }

    private AnalysisOutcome.PlaceResult place(String key) {
        return new AnalysisOutcome.PlaceResult("영상 장소 소개", "장소 " + key, "서울시", 37, 127,
                GeocodingProvider.LOCATIONIQ, key, key);
    }

    private CountDownLatch gate() {
        var gate = new CountDownLatch(1);
        gates.add(gate);
        return gate;
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }

    static class MutableClock extends Clock {
        final AtomicReference<Instant> now = new AtomicReference<>(NOW);

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant(), zone);
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @TestComponent
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = {Member.class, Device.class, Content.class, PersonalSave.class,
            Place.class, ContentPlace.class, Trigger.class})
    @EnableJpaRepositories(basePackageClasses = MemberRepository.class, includeFilters = @ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE, classes = {MemberRepository.class, DeviceRepository.class,
            ContentRepository.class, PersonalSaveRepository.class, PlaceRepository.class,
            ContentPlaceRepository.class, TriggerRepository.class}))
    @Import({SecurityConfig.class, SessionAuthenticationFilter.class, ApiAuthenticationEntryPoint.class,
            SessionTokenHasher.class, GlobalExceptionHandler.class, PersonalSaveConfiguration.class,
            PersonalSaveController.class, PersonalSaveService.class, PersonalSaveAnalysisService.class,
            PersonalSaveReadRepository.class, ContentPersistenceService.class})
    static class TestApplication {
        @Bean
        MutableClock personalSaveTestClock() {
            return new MutableClock();
        }
    }
}
