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
import java.util.UUID;

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

    public List<SearchPlaceRow> searchPlaces(RecallToolScope scope, String embedding, double radiusMeters, int limit) {
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
                ), selected_places AS (
                    SELECT place_id, min(cosine_distance) AS best_distance
                    FROM eligible GROUP BY place_id ORDER BY best_distance, place_id LIMIT :limit
                )
                SELECT e.* FROM eligible e JOIN selected_places s ON s.place_id = e.place_id
                ORDER BY s.best_distance, e.place_id, e.cosine_distance, e.content_id
                """.formatted(ACCESSIBLE_CONTENTS), Map.of("memberId", scope.memberId(),
                "latitude", scope.latitude(), "longitude", scope.longitude(), "embedding", embedding,
                "radiusMeters", radiusMeters, "limit", limit), (rs, index) -> new SearchPlaceRow(
                uuid(rs, "place_id"), rs.getString("name"), rs.getDouble("distance_meters"), content(rs)));
    }

    public List<ContentRow> searchContents(UUID memberId, String embedding, int limit) {
        return jdbc.query("""
                WITH accessible AS (%s)
                SELECT c.* FROM accessible c
                WHERE c.embedding IS NOT NULL
                    AND NOT EXISTS (SELECT 1 FROM content_place cp WHERE cp.content_id = c.content_id)
                ORDER BY c.embedding <=> CAST(:embedding AS vector), c.content_id LIMIT :limit
                """.formatted(ACCESSIBLE_CONTENTS), Map.of("memberId", memberId, "embedding", embedding, "limit", limit),
                (rs, index) -> content(rs));
    }

    public List<DetailRow> findContentDetails(UUID memberId, List<UUID> contentIds) {
        return jdbc.query("""
                WITH accessible AS (%s)
                SELECT c.*, p.id AS place_id, p.name, p.latitude, p.longitude, cp.description
                FROM accessible c
                LEFT JOIN content_place cp ON cp.content_id = c.content_id
                LEFT JOIN place p ON p.id = cp.place_id
                WHERE c.content_id IN (:contentIds) ORDER BY c.content_id, p.id
                """.formatted(ACCESSIBLE_CONTENTS), Map.of("memberId", memberId, "contentIds", contentIds),
                (rs, index) -> new DetailRow(content(rs), uuid(rs, "place_id"), rs.getString("name"),
                        rs.getString("description"), rs.getDouble("latitude"), rs.getDouble("longitude")));
    }

    public List<PlaceRow> findAccessiblePlaces(UUID memberId, List<UUID> placeIds) {
        return jdbc.query("""
                WITH accessible AS (%s)
                SELECT p.id AS place_id, p.latitude, p.longitude FROM place p
                WHERE p.id IN (:placeIds) AND EXISTS (
                    SELECT 1 FROM content_place cp JOIN accessible c ON c.content_id = cp.content_id
                    WHERE cp.place_id = p.id
                ) ORDER BY p.id
                """.formatted(ACCESSIBLE_CONTENTS), Map.of("memberId", memberId, "placeIds", placeIds),
                (rs, index) -> new PlaceRow(uuid(rs, "place_id"), rs.getDouble("latitude"), rs.getDouble("longitude")));
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

    public record DetailRow(ContentRow content, UUID placeId, String name, String description,
                            double latitude, double longitude) {}

    public record PlaceRow(UUID placeId, double latitude, double longitude) {}
}
