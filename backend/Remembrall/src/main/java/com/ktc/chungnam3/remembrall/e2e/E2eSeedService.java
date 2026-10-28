package com.ktc.chungnam3.remembrall.e2e;

import com.ktc.chungnam3.remembrall.consent.config.ConsentProperties;
import com.ktc.chungnam3.remembrall.consent.dto.ConsentRequest;
import com.ktc.chungnam3.remembrall.consent.service.MemberConsentService;
import com.ktc.chungnam3.remembrall.content.dto.AnalysisOutcome;
import com.ktc.chungnam3.remembrall.content.service.ContentAnalysisResultService;
import com.ktc.chungnam3.remembrall.content.service.ContentPersistenceService;
import com.ktc.chungnam3.remembrall.domain.content.ContentAnalysisStatus;
import com.ktc.chungnam3.remembrall.domain.content.ContentSourceStatus;
import com.ktc.chungnam3.remembrall.domain.member.AuthProvider;
import com.ktc.chungnam3.remembrall.domain.memberconsent.ConsentType;
import com.ktc.chungnam3.remembrall.domain.place.GeocodingProvider;
import com.ktc.chungnam3.remembrall.repository.MemberRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Profile("e2e")
public class E2eSeedService {
    private static final String PUBLIC_MEMBER = "e2e-seed-public-contributor-v1";
    private final ContentPersistenceService persistence;
    private final ContentAnalysisResultService results;
    private final MemberRepository members;
    private final MemberConsentService consents;
    private final ConsentProperties consentProperties;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final List<SeedContent> seeds;

    public E2eSeedService(ContentPersistenceService persistence, ContentAnalysisResultService results,
                          MemberRepository members, MemberConsentService consents,
                          ConsentProperties consentProperties, JdbcTemplate jdbc,
                          PlatformTransactionManager transactionManager, Clock clock, ObjectMapper mapper)
            throws IOException {
        this.persistence = persistence;
        this.results = results;
        this.members = members;
        this.consents = consents;
        this.consentProperties = consentProperties;
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
        try (var stream = new ClassPathResource("e2e/seed.json").getInputStream()) {
            this.seeds = List.of(mapper.readValue(stream, SeedContent[].class));
        }
    }

    public SeedResponse seed(UUID memberId) {
        Instant now = clock.instant();
        UUID publicMemberId = transactions.execute(status -> {
            members.insertIfAbsent(UUID.randomUUID(), AuthProvider.GUEST.name(), PUBLIC_MEMBER, now);
            return members.findByAuthProviderAndProviderUserId(AuthProvider.GUEST, PUBLIC_MEMBER)
                    .orElseThrow().getId();
        });
        consents.change(publicMemberId, ConsentType.PUBLIC_CANDIDATE_CONTRIBUTION,
                new ConsentRequest(true, consentProperties.currentVersion(ConsentType.PUBLIC_CANDIDATE_CONTRIBUTION)));
        var contents = new ArrayList<ContentView>();
        var places = new ArrayList<PlaceView>();
        PlaceView origin = null;
        for (var seed : seeds) {
            UUID ownerId = seed.publicContribution() ? publicMemberId : memberId;
            var claim = persistence.saveAndClaim(ownerId, seed.videoId());
            boolean applied = false;
            if (claim.analysisClaimed()) {
                var placeResults = seed.place() == null ? List.<AnalysisOutcome.PlaceResult>of()
                        : List.of(seed.place().outcome(seed.videoId()));
                applied = results.applyOutcome(claim.contentId(), new AnalysisOutcome(
                        ContentAnalysisStatus.SUCCESS, null, ContentSourceStatus.AVAILABLE,
                        seed.title(), seed.summary(), seed.category(), "e2e-seed-v1", now, placeResults));
            }
            if (claim.personalSaveCreated()) {
                Instant savedAt = now.atZone(ZoneOffset.UTC).minus(Period.parse(seed.savedAgo())).toInstant();
                jdbc.update("UPDATE personal_save SET saved_at = ? WHERE id = ? AND member_id = ?",
                        Timestamp.from(savedAt), claim.personalSaveId(), ownerId);
            }
            var contentPlaces = jdbc.query("""
                    SELECT p.id, p.name, p.address, p.latitude, p.longitude, cp.description
                    FROM content_place cp JOIN place p ON p.id = cp.place_id
                    WHERE cp.content_id = ? ORDER BY p.id
                    """, (rs, index) -> new PlaceView(rs.getObject("id", UUID.class), rs.getString("name"),
                    rs.getString("address"), rs.getDouble("latitude"), rs.getDouble("longitude"),
                    rs.getString("description")), claim.contentId());
            boolean outcomeApplied = applied;
            contents.add(jdbc.queryForObject("""
                    SELECT c.analysis_status, c.embedding IS NOT NULL AS embedding_filled,
                           c.embedding_model, ps.saved_at
                    FROM content c JOIN personal_save ps ON ps.content_id = c.id
                    WHERE ps.id = ?
                    """, (rs, index) -> new ContentView(claim.contentId(), claim.personalSaveId(), ownerId,
                    seed.videoId(), seed.title(), seed.publicContribution(), rs.getString("analysis_status"),
                    claim.analysisClaimed(), outcomeApplied, rs.getBoolean("embedding_filled"),
                    rs.getString("embedding_model"), rs.getTimestamp("saved_at").toInstant(), contentPlaces),
                    claim.personalSaveId()));
            places.addAll(contentPlaces);
            if (seed.departure() && !contentPlaces.isEmpty()) {
                origin = contentPlaces.getFirst();
            }
        }
        if (origin == null) {
            throw new IllegalStateException("Seed departure place has not completed analysis");
        }
        return new SeedResponse(memberId, publicMemberId, origin, List.copyOf(contents), List.copyOf(places));
    }

    public record SeedResponse(UUID memberId, UUID publicMemberId, PlaceView origin,
                               List<ContentView> contents, List<PlaceView> places) {}

    public record ContentView(UUID contentId, UUID personalSaveId, UUID memberId, String videoId, String title,
                              boolean publicContribution, String analysisStatus, boolean analysisClaimed,
                              boolean outcomeApplied, boolean embeddingFilled, String embeddingModel,
                              Instant savedAt, List<PlaceView> places) {}

    public record PlaceView(UUID placeId, String name, String address, double latitude, double longitude,
                            String description) {}

    public record SeedContent(String videoId, String title, String summary, String category, String savedAgo,
                              boolean departure, boolean publicContribution, SeedPlace place) {}

    public record SeedPlace(String name, String address, double latitude, double longitude, String description) {
        AnalysisOutcome.PlaceResult outcome(String videoId) {
            return new AnalysisOutcome.PlaceResult(description, name, address, latitude, longitude,
                    GeocodingProvider.LOCATIONIQ, videoId, videoId);
        }
    }
}
