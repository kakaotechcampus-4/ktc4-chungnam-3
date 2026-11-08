package com.ktc.chungnam3.remembrall.bruno;

import com.ktc.chungnam3.remembrall.consent.config.ConsentProperties;
import com.ktc.chungnam3.remembrall.content.embedding.EmbeddingClient;
import com.ktc.chungnam3.remembrall.domain.memberconsent.ConsentType;
import com.ktc.chungnam3.remembrall.domain.place.GeocodingProvider;
import com.ktc.chungnam3.remembrall.domain.recallexecution.ProposalType;
import com.ktc.chungnam3.remembrall.extraction.dto.*;
import com.ktc.chungnam3.remembrall.extraction.type.*;
import com.ktc.chungnam3.remembrall.notification.push.*;
import com.ktc.chungnam3.remembrall.personalsave.service.StaleAnalysisRecoveryScheduler;
import com.ktc.chungnam3.remembrall.recall.agent.*;
import com.ktc.chungnam3.remembrall.save.analysis.VideoContentAnalyzer;
import com.ktc.chungnam3.remembrall.save.dto.ResolvedPlaceDto;
import com.ktc.chungnam3.remembrall.save.place.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.function.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@TestConfiguration(proxyBeanMethods = false)
@Profile("bruno-harness")
public class BrunoHarnessConfiguration {
    @Bean
    State harnessState(JdbcTemplate jdbc, Environment environment,
                       @Qualifier("personalSaveAnalysisExecutor") ThreadPoolTaskExecutor executor) {
        String token = environment.getRequiredProperty("BRUNO_HARNESS_TOKEN");
        if (token.length() < 32 || !"127.0.0.1".equals(environment.getProperty("server.address"))
                || !"bruno_regression".equals(jdbc.queryForObject("SELECT current_database()", String.class))) {
            throw new IllegalStateException("Harness requires loopback binding, a disposable bruno_regression DB and a token");
        }
        return new State(jdbc, token, executor);
    }

    @Bean @Order(0)
    SecurityFilterChain harnessControlSecurity(HttpSecurity http) throws Exception {
        return http.securityMatcher("/__bruno/**").csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll()).build();
    }

    @Bean
    RouterFunction<ServerResponse> harnessControls(State state, StaleAnalysisRecoveryScheduler recovery) {
        return RouterFunctions.route().POST("/__bruno/control", request -> {
            if (!MessageDigest.isEqual(state.token.getBytes(StandardCharsets.UTF_8),
                    Objects.toString(request.headers().firstHeader("X-Bruno-Control"), "").getBytes(StandardCharsets.UTF_8))) {
                return ServerResponse.status(403).build();
            }
            Map<?, ?> body = request.body(Map.class);
            try {
                return ServerResponse.ok().body(state.control(body, recovery));
            } catch (IllegalArgumentException exception) {
                return ServerResponse.badRequest().body(Map.of("error", "Invalid harness action or value"));
            }
        }).build();
    }

    @Bean @Primary
    VideoContentAnalyzer harnessAnalyzer(State state) {
        VideoContentAnalyzer analyzer = mock(VideoContentAnalyzer.class);
        when(analyzer.analyze(anyString())).thenAnswer(call -> state.analyze(call.getArgument(0)));
        return analyzer;
    }

    @Bean @Primary
    PlaceLookup harnessPlaceLookup() { return (name, branch, region) -> List.of(); }

    @Bean @Primary
    PlaceResolver harnessResolver() {
        PlaceResolver resolver = mock(PlaceResolver.class);
        when(resolver.resolve(anyString(), anyString(), nullable(String.class), nullable(String.class), anyList()))
                .thenAnswer(call -> new PlaceResolver.Result(PlaceResolver.Decision.RESOLVED,
                        new ResolvedPlaceDto(call.getArgument(0), call.getArgument(1), null, "합성 주소", 36.37, 127.38,
                                GeocodingProvider.LOCATIONIQ, "bruno-place", null), null));
        return resolver;
    }

    @Bean @Primary
    KakaoPlaceLookup harnessKakao() { return (name, branch, lat, lon) -> Optional.of("bruno-place"); }

    @Bean @Primary
    EmbeddingClient harnessEmbedding() {
        return (text, task) -> { float[] vector = new float[768]; vector[0] = 1; return new EmbeddingClient.Result(vector, "bruno-fake"); };
    }

    @Bean @Primary
    ConsentProperties harnessConsent(State state) {
        ConsentProperties properties = spy(new ConsentProperties("v1", "v1"));
        when(properties.publicCandidateContributionTermsVersion()).thenReturn("v1");
        when(properties.locationBasedServiceTermsVersion()).thenAnswer(call -> state.termsVersion);
        doAnswer(call -> call.getArgument(0) == ConsentType.LOCATION_BASED_SERVICE ? state.termsVersion : "v1")
                .when(properties).currentVersion(any());
        return properties;
    }

    @Bean @Primary
    RecallAgent harnessAgent(State state) {
        return new RecallAgent() {
            public String version() { return "bruno-test-agent"; }
            public RecallAgentResult execute(UUID executionId, RecallAgentInput input) {
                int attempt = state.agentCalls.computeIfAbsent(executionId, id -> new AtomicInteger()).incrementAndGet();
                if ("fail".equals(state.agentMode) || ("fail-once".equals(state.agentMode) && attempt == 1)) {
                    throw new IllegalStateException("Intentional test agent failure");
                }
                if ("no-action".equals(state.agentMode)) return RecallAgentResult.noAction();
                var origin = input.origin();
                return RecallAgentResult.propose(ProposalType.PLACE,
                        new ProposalPayload(List.of(new ProposalPayload.Item(origin.contents().getFirst().contentId(),
                                origin.placeId(), ProposalSource.PERSONAL))), "합성 판단", "합성 알림", "합성 본문");
            }
        };
    }

    @Bean @Primary
    PushSender harnessPush(State state) {
        return message -> {
            state.pushCalls.incrementAndGet();
            if (!"sent".equals(state.pushMode)) throw new PushSendException(state.pushMode);
            return "bruno-message-" + UUID.randomUUID();
        };
    }

    static final class State {
        final JdbcTemplate jdbc;
        final String token;
        final ThreadPoolTaskExecutor executor;
        final ConcurrentMap<String, String> analysisModes = new ConcurrentHashMap<>();
        final ConcurrentMap<String, AtomicInteger> analysisCalls = new ConcurrentHashMap<>();
        final ConcurrentMap<String, CountDownLatch> gates = new ConcurrentHashMap<>();
        final ConcurrentMap<UUID, AtomicInteger> agentCalls = new ConcurrentHashMap<>();
        final AtomicInteger active = new AtomicInteger(), pushCalls = new AtomicInteger();
        volatile String termsVersion = "v1", agentMode = "no-action", pushMode = "sent";
        State(JdbcTemplate jdbc, String token, ThreadPoolTaskExecutor executor) { this.jdbc = jdbc; this.token = token; this.executor = executor; }

        YouTubeContentExtractionResultDto analyze(String url) throws InterruptedException {
            String video = url.substring(url.length() - 11);
            active.incrementAndGet();
            try {
                int attempt = analysisCalls.computeIfAbsent(video, id -> new AtomicInteger()).incrementAndGet();
                String mode = analysisModes.getOrDefault(video, "place");
                if ("delayed".equals(mode)) {
                    CountDownLatch gate = gates.computeIfAbsent(video, id -> new CountDownLatch(1));
                    if (!gate.await(30, TimeUnit.SECONDS)) throw new IllegalStateException("Test gate timed out");
                }
                String failure = switch (mode) {
                    case "unavailable" -> "VIDEO_UNAVAILABLE";
                    case "api-error", "timeout" -> "EXTRACTION_API_ERROR";
                    case "invalid-result" -> "EXTRACTION_RESULT_ERROR";
                    case "retry" -> attempt == 1 ? "EXTRACTION_API_ERROR" : null;
                    default -> null;
                };
                if (failure != null) return new YouTubeContentExtractionResultDto(ExtractionStatus.FAILED,
                        new AnalysisMetadataDto("bruno-v1", "fake", "fake"), null, null, null, List.of(), List.of(),
                        ExtractionFailureCode.valueOf(failure));
                return new YouTubeContentExtractionResultDto(ExtractionStatus.SUCCESS,
                        new AnalysisMetadataDto("bruno-v1", "fake", "fake"), "합성 영상", "합성 분석 결과", "PLACE",
                        "empty".equals(mode) ? List.of() : List.of(new PlaceCandidateDto("p1", "합성 장소", null, null, "합성 설명", "PLACE")),
                        List.of(), null);
            } finally { active.decrementAndGet(); }
        }

        Object control(Map<?, ?> body, StaleAnalysisRecoveryScheduler recovery) {
            String action = Objects.toString(body.get("action"), "");
            switch (action) {
                case "reset" -> {
                    if (executor.getActiveCount() != 0 || executor.getQueueSize() != 0) throw new IllegalArgumentException("Active analysis");
                    jdbc.execute("TRUNCATE member, content, place CASCADE");
                    analysisModes.clear(); analysisCalls.clear(); gates.clear(); agentCalls.clear(); pushCalls.set(0);
                    termsVersion = "v1"; agentMode = "no-action"; pushMode = "sent";
                    return Map.of("reset", true);
                }
                case "seed" -> { return seed(); }
                case "configure" -> {
                    if (body.containsKey("videoId")) {
                        String mode = text(body, "mode");
                        if (!Set.of("place", "empty", "unavailable", "api-error", "timeout", "invalid-result", "retry", "delayed").contains(mode)) throw new IllegalArgumentException();
                        analysisModes.put(video(body), mode);
                    }
                    if (body.containsKey("agentMode")) {
                        String mode = text(body, "agentMode");
                        if (!Set.of("no-action", "propose", "fail", "fail-once").contains(mode)) throw new IllegalArgumentException();
                        agentMode = mode;
                    }
                    if (body.containsKey("pushMode")) {
                        String mode = text(body, "pushMode");
                        if (!Set.of("sent", "NO_FCM_TOKEN", "FCM_NOT_CONFIGURED", "UNREGISTERED", "SENDER_ID_MISMATCH", "FCM_SEND_ERROR").contains(mode)) throw new IllegalArgumentException();
                        pushMode = mode;
                    }
                    if (body.containsKey("termsVersion")) {
                        String version = text(body, "termsVersion");
                        if (!Set.of("v1", "v2").contains(version)) throw new IllegalArgumentException();
                        termsVersion = version;
                    }
                    return Map.of("configured", true);
                }
                case "release" -> { gates.computeIfAbsent(video(body), id -> new CountDownLatch(1)).countDown(); return Map.of("released", true); }
                case "expire" -> {
                    UUID member = member(body);
                    jdbc.update("UPDATE device SET session_expires_at = CURRENT_TIMESTAMP - INTERVAL '1 hour' WHERE member_id=?", member);
                    return Map.of("expired", true);
                }
                case "recover" -> {
                    jdbc.update("UPDATE content SET analysis_started_at=CURRENT_TIMESTAMP-INTERVAL '10 minutes' WHERE video_id=? AND analysis_status='ANALYZING'", video(body));
                    recovery.failStaleAnalyses(); return Map.of("recovered", true);
                }
                case "observe" -> { return observe(body); }
                default -> throw new IllegalArgumentException();
            }
        }

        Map<String, Object> observe(Map<?, ?> body) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("activeAnalyses", executor.getActiveCount()); result.put("pushCalls", pushCalls.get());
            result.put("agentCalls", agentCalls.values().stream().mapToInt(AtomicInteger::get).sum());
            if (body.containsKey("videoId")) {
                String video = video(body);
                result.put("analysisCalls", analysisCalls.getOrDefault(video, new AtomicInteger()).get());
                result.put("content", jdbc.queryForList("SELECT id,analysis_status,last_analysis_error_code FROM content WHERE video_id=?", video));
                result.put("savedCount", jdbc.queryForObject("SELECT count(*) FROM personal_save s JOIN content c ON c.id=s.content_id WHERE c.video_id=?", Integer.class, video));
            }
            if (body.containsKey("installationId")) {
                UUID member = member(body);
                result.put("devices", jdbc.queryForList("SELECT fcm_token,session_token_hash IS NOT NULL AS active_session FROM device WHERE member_id=?", member));
                result.put("triggerCount", jdbc.queryForObject("SELECT count(*) FROM trigger WHERE member_id=?", Integer.class, member));
                result.put("executions", jdbc.queryForList("SELECT r.id,r.trigger_event_id,r.status,r.result,r.failure_code,r.retry_count FROM recall_execution r JOIN trigger t ON t.id=r.trigger_id WHERE t.member_id=? ORDER BY r.created_at,r.id", member));
                result.put("notifications", jdbc.queryForList("SELECT id,execution_id,status,failure_code FROM notification WHERE member_id=? ORDER BY created_at,id", member));
            }
            return result;
        }

        Map<String, Object> seed() {
            List<UUID> contents = new ArrayList<>(), places = new ArrayList<>();
            for (int i=0;i<3;i++) {
                UUID id = UUID.randomUUID(); contents.add(id);
                jdbc.update("INSERT INTO content(id,video_id,title,summary,analysis_status) VALUES (?,?,?,?,'SUCCESS')", id, "BrunoSeed0"+i, "합성 영상 "+i, "합성 요약");
            }
            for (int i=0;i<2;i++) {
                UUID id = UUID.randomUUID(); places.add(id);
                jdbc.update("INSERT INTO place(id,name,address,latitude,longitude,geocoding_provider,geocoding_place_id,verification_provider,verification_place_id,verified_at) VALUES (?,?,?,?,?,'LOCATIONIQ',?,'KAKAO',?,CURRENT_TIMESTAMP)",
                        id, "합성 장소 "+i, "합성 주소 "+i, 36.37+i*0.01, 127.38, "bruno-geo-"+i, "bruno-kakao-"+i);
            }
            for (int i=0;i<2;i++) jdbc.update("INSERT INTO content_place(id,content_id,place_id,description) VALUES (?,?,?,?)", UUID.randomUUID(), contents.get(i), places.get(0), "합성 공통 장소");
            jdbc.update("INSERT INTO content_place(id,content_id,place_id,description) VALUES (?,?,?,?)", UUID.randomUUID(), contents.get(1), places.get(1), "합성 고유 장소");
            return Map.of("contentIds", contents, "placeIds", places, "videoIds", List.of("BrunoSeed00", "BrunoSeed01", "BrunoSeed02"));
        }

        UUID member(Map<?, ?> body) {
            UUID installation = UUID.fromString(text(body, "installationId"));
            try {
                String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(installation.toString().getBytes(StandardCharsets.UTF_8)));
                return jdbc.queryForObject("SELECT id FROM member WHERE auth_provider='GUEST' AND provider_user_id=?", UUID.class, hash);
            } catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
        }
        static String text(Map<?, ?> body, String key) {
            Object value = body.get(key); if (!(value instanceof String s) || s.isBlank()) throw new IllegalArgumentException(); return s;
        }
        static String video(Map<?, ?> body) {
            String id = text(body, "videoId"); if (!id.matches("[A-Za-z0-9_-]{11}")) throw new IllegalArgumentException(); return id;
        }
    }
}
