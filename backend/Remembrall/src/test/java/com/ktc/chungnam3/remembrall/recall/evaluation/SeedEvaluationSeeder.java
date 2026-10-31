package com.ktc.chungnam3.remembrall.recall.evaluation;

import com.ktc.chungnam3.remembrall.consent.config.ConsentProperties;
import com.ktc.chungnam3.remembrall.consent.dto.ConsentRequest;
import com.ktc.chungnam3.remembrall.consent.service.MemberConsentService;
import com.ktc.chungnam3.remembrall.content.dto.AnalysisOutcome;
import com.ktc.chungnam3.remembrall.content.service.ContentAnalysisResultService;
import com.ktc.chungnam3.remembrall.content.service.ContentPersistenceService;
import com.ktc.chungnam3.remembrall.domain.content.ContentAnalysisStatus;
import com.ktc.chungnam3.remembrall.domain.content.ContentSourceStatus;
import com.ktc.chungnam3.remembrall.domain.member.AuthProvider;
import com.ktc.chungnam3.remembrall.domain.member.Member;
import com.ktc.chungnam3.remembrall.domain.memberconsent.ConsentType;
import com.ktc.chungnam3.remembrall.domain.place.GeocodingProvider;
import com.ktc.chungnam3.remembrall.repository.MemberRepository;
import com.ktc.chungnam3.remembrall.repository.RecallExecutionRepository;
import com.ktc.chungnam3.remembrall.repository.TriggerRepository;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class SeedEvaluationSeeder {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final PostgreSQLContainer database;
    private final JdbcTemplate jdbc;
    private final ContentPersistenceService persistence;
    private final ContentAnalysisResultService results;
    private final MemberRepository members;
    private final MemberConsentService consents;
    private final ConsentProperties consentProperties;
    private final TriggerRepository triggers;
    private final RecallExecutionRepository executions;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final FakeWeatherClient weather;

    public SeedEvaluationSeeder(PostgreSQLContainer database, JdbcTemplate jdbc,
                                ContentPersistenceService persistence, ContentAnalysisResultService results,
                                MemberRepository members, MemberConsentService consents,
                                ConsentProperties consentProperties, TriggerRepository triggers,
                                RecallExecutionRepository executions, PlatformTransactionManager transactionManager,
                                Clock clock, FakeWeatherClient weather) {
        this.database = database;
        this.jdbc = jdbc;
        this.persistence = persistence;
        this.results = results;
        this.members = members;
        this.consents = consents;
        this.consentProperties = consentProperties;
        this.triggers = triggers;
        this.executions = executions;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.weather = weather;
    }

    public SeededScene resetAndSeed(SeedEvaluationScene scene) {
        assertTestDatabase();
        if (clock instanceof EvaluationClock evaluationClock && scene.event() != null) {
            evaluationClock.set(seoulInstant(scene.event().occurredAt()));
        }
        weather.configure(scene.weather());
        transactions.executeWithoutResult(status -> {
            jdbc.update("DELETE FROM member");
            jdbc.update("DELETE FROM content");
            jdbc.update("DELETE FROM place");
        });
        Objects.requireNonNull(scene.seed(), "Scene seed is required");
        Objects.requireNonNull(scene.seed().places(), "Scene places are required");
        Objects.requireNonNull(scene.seed().personalSaves(), "Personal saves are required");
        var definitions = new HashMap<String, SeedEvaluationScene.Save>();
        var placeIds = new LinkedHashMap<String, UUID>();
        UUID memberId = member();
        saveAll(scene, memberId, scene.seed().personalSaves(), definitions, placeIds);
        if (scene.seed().contributors() != null) {
            for (var contributor : scene.seed().contributors()) {
                UUID contributorId = member();
                consents.change(contributorId, ConsentType.PUBLIC_CANDIDATE_CONTRIBUTION,
                        new ConsentRequest(contributor.agreed(),
                                consentProperties.currentVersion(ConsentType.PUBLIC_CANDIDATE_CONTRIBUTION)));
                saveAll(scene, contributorId, contributor.saves(), definitions, placeIds);
            }
        }
        seedNotifications(scene, memberId, placeIds);
        return new SeededScene(memberId, Map.copyOf(placeIds));
    }

    private void seedNotifications(SeedEvaluationScene scene, UUID memberId, Map<String, UUID> placeIds) {
        for (var record : scene.recentNotifications()) {
            UUID placeId = placeIds.get(record.placeKey());
            if (placeId == null) throw new IllegalArgumentException("Unknown notification place key");
            Instant sentAt = seoulInstant(record.sentAt());
            if (sentAt.isAfter(clock.instant())) throw new IllegalArgumentException("Notification is in the future");
            UUID triggerId = jdbc.queryForObject("SELECT id FROM trigger WHERE member_id = ? ORDER BY id LIMIT 1",
                    UUID.class, memberId);
            UUID contentId = jdbc.queryForObject("SELECT content_id FROM content_place WHERE place_id = ? ORDER BY content_id LIMIT 1",
                    UUID.class, placeId);
            Boolean personal = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM personal_save WHERE member_id = ? AND content_id = ?)",
                    Boolean.class, memberId, contentId);
            String payload = tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(Map.of("items",
                    List.of(Map.of("contentId", contentId, "placeId", placeId, "source", Boolean.TRUE.equals(personal) ? "PERSONAL" : "PUBLIC"))));
            UUID executionId = UUID.randomUUID();
            transactions.executeWithoutResult(status -> {
                jdbc.update("""
                        INSERT INTO recall_execution (id, trigger_id, trigger_event_id, status, result, proposal_type,
                            proposal_payload, agent_version, event_occurred_at, started_at, finished_at, created_at, updated_at)
                        VALUES (?, ?, ?, 'COMPLETED', 'PROPOSE', 'PLACE', CAST(? AS jsonb), 'eval-history', ?, ?, ?, ?, ?)
                        """, executionId, triggerId, UUID.randomUUID(), payload, Timestamp.from(sentAt), Timestamp.from(sentAt),
                        Timestamp.from(sentAt), Timestamp.from(sentAt), Timestamp.from(sentAt));
                jdbc.update("""
                        INSERT INTO notification (id, execution_id, member_id, title, body, status, sent_at, opened_at, created_at, updated_at)
                        VALUES (?, ?, ?, '평가 이력', '평가 알림', 'SENT', ?, ?, ?, ?)
                        """, UUID.randomUUID(), executionId, memberId, Timestamp.from(sentAt),
                        record.opened() ? Timestamp.from(sentAt) : null, Timestamp.from(sentAt), Timestamp.from(sentAt));
            });
        }
    }

    public PreparedExecution createExecution(SeedEvaluationScene scene, SeededScene seeded, String agentVersion) {
        Objects.requireNonNull(scene.event(), "Scene event is required");
        UUID placeId = seeded.placeIds().get(scene.event().placeKey());
        if (placeId == null) {
            throw new IllegalArgumentException("Event place has not been seeded: " + scene.event().placeKey());
        }
        var trigger = triggers.findByMemberIdAndPlaceId(seeded.memberId(), placeId)
                .orElseThrow(() -> new IllegalArgumentException("Event place has no target-member trigger"));
        Instant occurredAt = seoulInstant(scene.event().occurredAt());
        UUID executionId = UUID.randomUUID();
        transactions.executeWithoutResult(status -> {
            int inserted = executions.insertPendingIfAbsent(executionId, trigger.getId(), UUID.randomUUID(),
                    agentVersion, occurredAt, clock.instant());
            if (inserted != 1) {
                throw new IllegalStateException("Could not create evaluation execution");
            }
        });
        return new PreparedExecution(executionId, placeId);
    }

    void assertTestDatabase() {
        if (!database.isRunning()) {
            throw new IllegalStateException("Seed evaluation requires its running Testcontainers database");
        }
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            if (!database.getJdbcUrl().equals(connection.getMetaData().getURL())) {
                throw new IllegalStateException("Refusing to seed or clear a database outside this Testcontainer");
            }
            return null;
        });
    }

    private UUID member() {
        return members.saveAndFlush(Member.create(AuthProvider.GUEST,
                "eval-seed-" + UUID.randomUUID(), clock.instant())).getId();
    }

    private void saveAll(SeedEvaluationScene scene, UUID memberId, List<SeedEvaluationScene.Save> saves,
                         Map<String, SeedEvaluationScene.Save> definitions, Map<String, UUID> placeIds) {
        Objects.requireNonNull(saves, "Contributor saves are required");
        var ownVideoIds = new HashSet<String>();
        for (var save : saves) {
            if (save.videoId() == null || !save.videoId().startsWith("eval-seed-")) {
                throw new IllegalArgumentException("Seed videoId must start with eval-seed-");
            }
            if (!ownVideoIds.add(save.videoId())) {
                throw new IllegalArgumentException("Duplicate videoId for the same member: " + save.videoId());
            }
            var previous = definitions.putIfAbsent(save.videoId(), save);
            if (previous != null && (!Objects.equals(previous.title(), save.title())
                    || !Objects.equals(previous.summary(), save.summary())
                    || !Objects.equals(previous.category(), save.category())
                    || !Objects.equals(previous.places(), save.places()))) {
                throw new IllegalArgumentException("Conflicting seed content definitions: " + save.videoId());
            }
            if (save.summary() == null || save.summary().isBlank()) {
                throw new IllegalArgumentException("Seed content requires a summary for embedding: " + save.videoId());
            }
            Instant savedAt = seoulInstant(save.savedAt());
            var links = Objects.requireNonNull(save.places(), "Content places are required, possibly empty");
            var placeResults = links.stream().map(link -> placeOutcome(scene, link)).toList();
            var claim = persistence.saveAndClaim(memberId, save.videoId());
            if (claim.analysisClaimed() && !results.applyOutcome(claim.contentId(), new AnalysisOutcome(
                    ContentAnalysisStatus.SUCCESS, null, ContentSourceStatus.AVAILABLE, save.title(),
                    save.summary(), save.category(), "eval-seed-v1", clock.instant(), placeResults))) {
                throw new IllegalStateException("Could not apply seed analysis: " + save.videoId());
            }
            Boolean embedded = jdbc.queryForObject("""
                    SELECT analysis_status = 'SUCCESS' AND embedding IS NOT NULL
                           AND vector_dims(embedding) = 768 AND embedding_model IS NOT NULL
                    FROM content WHERE id = ?
                    """, Boolean.class, claim.contentId());
            if (!Boolean.TRUE.equals(embedded)) {
                throw new IllegalStateException("Seed analysis or embedding is incomplete: " + save.videoId());
            }
            jdbc.update("UPDATE personal_save SET saved_at = ? WHERE id = ? AND member_id = ?",
                    Timestamp.from(savedAt), claim.personalSaveId(), memberId);
            jdbc.update("UPDATE content SET created_at = LEAST(created_at, ?) WHERE id = ?",
                    Timestamp.from(savedAt), claim.contentId());
            for (var link : links) {
                UUID placeId = jdbc.queryForObject("""
                        SELECT p.id FROM place p JOIN content_place cp ON cp.place_id = p.id
                        WHERE cp.content_id = ? AND p.verification_provider = 'KAKAO'
                              AND p.verification_place_id = ?
                        """, UUID.class, claim.contentId(), externalPlaceId(scene.id(), link.placeKey()));
                placeIds.put(link.placeKey(), placeId);
            }
        }
    }

    private AnalysisOutcome.PlaceResult placeOutcome(SeedEvaluationScene scene, SeedEvaluationScene.PlaceLink link) {
        var place = scene.seed().places().get(link.placeKey());
        if (place == null) {
            throw new IllegalArgumentException("Unknown scene place key: " + link.placeKey());
        }
        String externalId = externalPlaceId(scene.id(), link.placeKey());
        return new AnalysisOutcome.PlaceResult(link.description(), place.name(), place.address(),
                place.latitude(), place.longitude(), GeocodingProvider.LOCATIONIQ, externalId, externalId);
    }

    private String externalPlaceId(String sceneId, String key) {
        return "eval-seed-" + UUID.nameUUIDFromBytes((sceneId + ":" + key).getBytes(StandardCharsets.UTF_8));
    }

    static Instant seoulInstant(String value) {
        Objects.requireNonNull(value, "Asia/Seoul timestamp is required");
        try {
            var timestamp = OffsetDateTime.parse(value);
            if (!timestamp.getOffset().equals(timestamp.atZoneSameInstant(SEOUL).getOffset())) {
                throw new IllegalArgumentException("Timestamp offset must match Asia/Seoul: " + value);
            }
            return timestamp.toInstant();
        } catch (DateTimeParseException exception) {
            return LocalDateTime.parse(value).atZone(SEOUL).toInstant();
        }
    }

    public record SeededScene(UUID memberId, Map<String, UUID> placeIds) {}

    public record PreparedExecution(UUID executionId, UUID triggerPlaceId) {}
}
