package com.ktc.chungnam3.remembrall.recall.tool;

import com.ktc.chungnam3.remembrall.recall.agent.ProposalSource;
import com.ktc.chungnam3.remembrall.repository.PublicCandidateRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;
import com.ktc.chungnam3.remembrall.recall.tool.RecallToolResults.SearchSource;

@Repository
public class RecallToolRepository {

    private static final String ACCESSIBLE_CONTENTS = """
            SELECT c.id AS content_id, c.title, c.summary, c.category, c.embedding, own.saved_at,
                   CASE WHEN own.id IS NOT NULL THEN 'PERSONAL' ELSE 'PUBLIC' END AS source
            FROM content c
            LEFT JOIN personal_save own ON own.content_id = c.id AND own.member_id = :memberId
            WHERE c.analysis_status <> 'FAILED' AND c.source_status <> 'UNAVAILABLE'
                AND (own.id IS NOT NULL OR %s)
            """.formatted(PublicCandidateRepository.ACTIVE_CONTRIBUTION_EXISTS);

    private final NamedParameterJdbcTemplate jdbc;

    public RecallToolRepository(JdbcTemplate jdbc) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbc);
    }

    public List<SearchPlaceRow> searchPlaces(RecallToolScope scope, String embedding, double radiusMeters, int limit,
                                            SearchSource source, Set<UUID> seenContents, Set<UUID> seenPlaces) {
        return jdbc.query("""
                WITH accessible AS (%s), distances AS (
                    SELECT p.id AS place_id, p.name,
                           6371000.0 * 2 * asin(sqrt(least(1.0, greatest(0.0,
                               power(sin(radians(p.latitude - :latitude) / 2), 2)
                               + cos(radians(:latitude)) * cos(radians(p.latitude))
                               * power(sin(radians(p.longitude - :longitude) / 2), 2)
                           )))) AS distance_meters
                    FROM place p
                ), eligible AS (
                    SELECT d.*, c.*, c.embedding <=> CAST(:embedding AS vector) AS cosine_distance
                    FROM distances d
                    JOIN content_place cp ON cp.place_id = d.place_id
                    JOIN accessible c ON c.content_id = cp.content_id
                    WHERE d.distance_meters <= :radiusMeters AND c.embedding IS NOT NULL
                        AND (:source = 'ALL' OR c.source = :source)
                        AND c.content_id NOT IN (:seenContents) AND d.place_id NOT IN (:seenPlaces)
                ), selected_places AS (
                    SELECT place_id, min(cosine_distance) AS best_distance
                    FROM eligible GROUP BY place_id ORDER BY best_distance, place_id LIMIT :limit
                )
                SELECT e.* FROM eligible e JOIN selected_places s ON s.place_id = e.place_id
                ORDER BY s.best_distance, e.place_id, e.cosine_distance, e.content_id
                """.formatted(ACCESSIBLE_CONTENTS), searchParameters(scope, embedding, radiusMeters, limit,
                source, seenContents, seenPlaces), (rs, index) -> new SearchPlaceRow(
                uuid(rs, "place_id"), rs.getString("name"), rs.getDouble("distance_meters"), content(rs)));
    }

    public List<ContentRow> searchContents(UUID memberId, String embedding, int limit,
                                          SearchSource source, Set<UUID> seenContents) {
        return jdbc.query("""
                WITH accessible AS (%s)
                SELECT c.* FROM accessible c
                WHERE c.embedding IS NOT NULL
                    AND (:source = 'ALL' OR c.source = :source) AND c.content_id NOT IN (:seenContents)
                    AND NOT EXISTS (SELECT 1 FROM content_place cp WHERE cp.content_id = c.content_id)
                ORDER BY c.embedding <=> CAST(:embedding AS vector), c.content_id LIMIT :limit
                """.formatted(ACCESSIBLE_CONTENTS), Map.of("memberId", memberId, "embedding", embedding, "limit", limit,
                "source", source.name(), "seenContents", nonEmpty(seenContents)),
                (rs, index) -> content(rs));
    }

    public List<ConditionRow> findVisitConditions(UUID memberId, List<UUID> placeIds) {
        return jdbc.query("""
                WITH accessible AS (%s)
                SELECT c.*, p.id AS place_id, p.name, cp.description,
                       COALESCE(c.saved_at, original.created_at) AS reference_at,
                       CASE WHEN c.saved_at IS NULL THEN 'CONTENT_FIRST_STORED' ELSE 'PERSONAL_SAVE' END AS reference_basis
                FROM accessible c
                JOIN content original ON original.id = c.content_id
                JOIN content_place cp ON cp.content_id = c.content_id
                JOIN place p ON p.id = cp.place_id
                WHERE p.id IN (:placeIds) ORDER BY p.id, c.source, c.content_id
                """.formatted(ACCESSIBLE_CONTENTS), Map.of("memberId", memberId, "placeIds", placeIds),
                (rs, index) -> new ConditionRow(uuid(rs, "place_id"), rs.getString("name"), content(rs),
                        rs.getString("description"), rs.getTimestamp("reference_at").toInstant(), rs.getString("reference_basis")));
    }

    public List<RecentRow> findRecentProposals(UUID memberId, List<UUID> placeIds, Instant since, Instant now) {
        return jdbc.query("""
                SELECT DISTINCT p.id AS place_id, n.id, n.sent_at, n.opened_at
                FROM notification n JOIN recall_execution e ON e.id = n.execution_id
                JOIN LATERAL jsonb_array_elements(COALESCE(e.proposal_payload -> 'items', '[]'::jsonb)) item ON TRUE
                JOIN place p ON p.id::text = item ->> 'placeId'
                WHERE n.member_id = :memberId AND n.status = 'SENT' AND p.id IN (:placeIds)
                    AND n.sent_at >= :since AND n.sent_at <= :now
                ORDER BY n.sent_at DESC, n.id
                """, Map.of("memberId", memberId, "placeIds", placeIds,
                "since", java.sql.Timestamp.from(since), "now", java.sql.Timestamp.from(now)),
                (rs, index) -> new RecentRow(uuid(rs, "place_id"), rs.getTimestamp("sent_at").toInstant(),
                        rs.getTimestamp("opened_at") == null ? null : rs.getTimestamp("opened_at").toInstant()));
    }

    private Map<String, Object> searchParameters(RecallToolScope scope, String embedding, double radius, int limit,
                                                  SearchSource source, Set<UUID> contents, Set<UUID> places) {
        var values = new HashMap<String, Object>();
        values.put("memberId", scope.memberId());
        values.put("latitude", scope.latitude());
        values.put("longitude", scope.longitude());
        values.put("embedding", embedding);
        values.put("radiusMeters", radius);
        values.put("limit", limit);
        values.put("source", source.name());
        values.put("seenContents", nonEmpty(contents));
        values.put("seenPlaces", nonEmpty(places));
        return values;
    }

    private Set<UUID> nonEmpty(Set<UUID> ids) {
        return ids.isEmpty() ? Set.of(new UUID(0, 0)) : ids;
    }

    private ContentRow content(ResultSet rs) throws SQLException {
        var savedAt = rs.getTimestamp("saved_at");
        return new ContentRow(uuid(rs, "content_id"), ProposalSource.valueOf(rs.getString("source")),
                rs.getString("title"), rs.getString("summary"), rs.getString("category"),
                savedAt == null ? null : savedAt.toInstant());
    }

    private UUID uuid(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, UUID.class);
    }

    public record ContentRow(UUID contentId, ProposalSource source, String title, String summary,
                             String category, Instant savedAt) {}

    public record SearchPlaceRow(UUID placeId, String name, double distanceMeters, ContentRow content) {}

    public record ConditionRow(UUID placeId, String name, ContentRow content, String description,
                               Instant referenceAt, String referenceTimeBasis) {}

    public record RecentRow(UUID placeId, Instant sentAt, Instant openedAt) {}
}
