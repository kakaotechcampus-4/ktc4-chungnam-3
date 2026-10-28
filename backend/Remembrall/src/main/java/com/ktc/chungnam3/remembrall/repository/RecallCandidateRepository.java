package com.ktc.chungnam3.remembrall.repository;

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
public class RecallCandidateRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public RecallCandidateRepository(JdbcTemplate jdbc) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbc);
    }

    public List<OriginRow> findOrigin(UUID executionId) {
        return jdbc.query("""
                WITH eligible AS (
                    SELECT cp.place_id, ps.member_id, c.id AS content_id, ps.id AS personal_save_id,
                           c.title, c.summary, c.category, ps.saved_at, cp.description
                    FROM content_place cp
                    JOIN content c ON c.id = cp.content_id
                    JOIN personal_save ps ON ps.content_id = c.id
                    WHERE c.analysis_status <> 'FAILED' AND c.source_status <> 'UNAVAILABLE'
                )
                SELECT t.member_id, p.id AS place_id, p.name, p.latitude, p.longitude,
                       e.event_occurred_at, c.content_id, c.personal_save_id,
                       c.title, c.summary, c.category, c.saved_at, c.description
                FROM recall_execution e
                JOIN trigger t ON t.id = e.trigger_id
                JOIN place p ON p.id = t.place_id
                LEFT JOIN eligible c ON c.place_id = p.id AND c.member_id = t.member_id
                WHERE e.id = :executionId
                ORDER BY c.saved_at, c.content_id
                """, Map.of("executionId", executionId), (rs, index) -> new OriginRow(
                uuid(rs, "member_id"), uuid(rs, "place_id"), rs.getString("name"),
                rs.getDouble("latitude"), rs.getDouble("longitude"), instant(rs, "event_occurred_at"),
                uuid(rs, "content_id") == null ? null : personalContent(rs)));
    }

    public List<PersonalPlaceRow> findNearbyPersonal(UUID memberId, UUID originPlaceId, double latitude,
                                                    double longitude, double radiusMeters, int limit) {
        return jdbc.query("""
                WITH distances AS (
                    SELECT p.id AS place_id, p.name,
                           6371000.0 * 2 * asin(sqrt(least(1.0, greatest(0.0,
                               power(sin(radians(p.latitude - :latitude) / 2), 2)
                               + cos(radians(:latitude)) * cos(radians(p.latitude))
                               * power(sin(radians(p.longitude - :longitude) / 2), 2)
                           )))) AS distance_meters
                    FROM place p WHERE p.id <> :originPlaceId
                ), eligible AS (
                    SELECT d.*, c.id AS content_id, ps.id AS personal_save_id,
                           c.title, c.summary, c.category, ps.saved_at, cp.description
                    FROM distances d
                    JOIN content_place cp ON cp.place_id = d.place_id
                    JOIN content c ON c.id = cp.content_id
                    JOIN personal_save ps ON ps.content_id = c.id AND ps.member_id = :memberId
                    WHERE d.distance_meters <= :radiusMeters
                      AND c.analysis_status <> 'FAILED' AND c.source_status <> 'UNAVAILABLE'
                ), selected_places AS (
                    SELECT DISTINCT place_id, distance_meters FROM eligible
                    ORDER BY distance_meters, place_id LIMIT :limit
                )
                SELECT e.* FROM eligible e JOIN selected_places s ON s.place_id = e.place_id
                ORDER BY e.distance_meters, e.place_id, e.saved_at, e.content_id
                """, nearbyParameters(memberId, originPlaceId, latitude, longitude, radiusMeters, limit),
                (rs, index) -> new PersonalPlaceRow(uuid(rs, "place_id"), rs.getString("name"),
                        rs.getDouble("distance_meters"), personalContent(rs)));
    }

    public List<PublicPlaceRow> findNearbyPublic(UUID memberId, double latitude,
                                                double longitude, double radiusMeters, int limit) {
        return jdbc.query("""
                WITH distances AS (
                    SELECT p.id AS place_id, p.name,
                           6371000.0 * 2 * asin(sqrt(least(1.0, greatest(0.0,
                               power(sin(radians(p.latitude - :latitude) / 2), 2)
                               + cos(radians(:latitude)) * cos(radians(p.latitude))
                               * power(sin(radians(p.longitude - :longitude) / 2), 2)
                           )))) AS distance_meters
                    FROM place p
                ), eligible AS (
                    SELECT d.*, c.id AS content_id, c.title, c.summary, c.category, cp.description
                    FROM distances d
                    JOIN content_place cp ON cp.place_id = d.place_id
                    JOIN content c ON c.id = cp.content_id
                    WHERE d.distance_meters <= :radiusMeters
                      AND c.analysis_status <> 'FAILED' AND c.source_status <> 'UNAVAILABLE'
                      AND NOT EXISTS (
                          SELECT 1 FROM personal_save own
                          WHERE own.content_id = c.id AND own.member_id = :memberId
                      )
                      AND %s
                ), selected_places AS (
                    SELECT DISTINCT place_id, distance_meters FROM eligible
                    ORDER BY distance_meters, place_id LIMIT :limit
                )
                SELECT e.* FROM eligible e JOIN selected_places s ON s.place_id = e.place_id
                ORDER BY e.distance_meters, e.place_id, e.content_id
                """.formatted(PublicCandidateRepository.ACTIVE_CONTRIBUTION_EXISTS),
                Map.of("memberId", memberId, "latitude", latitude, "longitude", longitude,
                        "radiusMeters", radiusMeters, "limit", limit),
                (rs, index) -> new PublicPlaceRow(uuid(rs, "place_id"), rs.getString("name"),
                        rs.getDouble("distance_meters"), uuid(rs, "content_id"), rs.getString("title"),
                        rs.getString("summary"), rs.getString("category"), rs.getString("description")));
    }

    public List<SemanticRow> findSemantic(UUID memberId, UUID originPlaceId, int limit) {
        return jdbc.query("""
                WITH origins AS (
                    SELECT c.id, c.embedding FROM content c
                    JOIN content_place cp ON cp.content_id = c.id AND cp.place_id = :originPlaceId
                    JOIN personal_save ps ON ps.content_id = c.id AND ps.member_id = :memberId
                    WHERE c.embedding IS NOT NULL
                      AND c.analysis_status <> 'FAILED' AND c.source_status <> 'UNAVAILABLE'
                )
                SELECT c.id AS content_id, c.title, c.summary, c.category,
                       own.id AS personal_save_id, own.saved_at,
                       (SELECT min(c.embedding <=> o.embedding) FROM origins o) AS cosine_distance
                FROM content c
                LEFT JOIN personal_save own ON own.content_id = c.id AND own.member_id = :memberId
                WHERE c.embedding IS NOT NULL
                  AND c.analysis_status <> 'FAILED' AND c.source_status <> 'UNAVAILABLE'
                  AND NOT EXISTS (SELECT 1 FROM content_place cp WHERE cp.content_id = c.id)
                  AND NOT EXISTS (SELECT 1 FROM origins o WHERE o.id = c.id)
                  AND EXISTS (SELECT 1 FROM origins)
                  AND (own.id IS NOT NULL OR %s)
                ORDER BY cosine_distance, c.id LIMIT :limit
                """.formatted(PublicCandidateRepository.ACTIVE_CONTRIBUTION_EXISTS),
                Map.of("memberId", memberId, "originPlaceId", originPlaceId, "limit", limit),
                (rs, index) -> new SemanticRow(uuid(rs, "content_id"), uuid(rs, "personal_save_id"),
                        rs.getString("title"), rs.getString("summary"), rs.getString("category"),
                        instant(rs, "saved_at")));
    }

    private Map<String, Object> nearbyParameters(UUID memberId, UUID originPlaceId, double latitude,
                                                  double longitude, double radiusMeters, int limit) {
        return Map.of("memberId", memberId, "originPlaceId", originPlaceId, "latitude", latitude,
                "longitude", longitude, "radiusMeters", radiusMeters, "limit", limit);
    }

    private PersonalContentRow personalContent(ResultSet rs) throws SQLException {
        return new PersonalContentRow(uuid(rs, "content_id"), uuid(rs, "personal_save_id"),
                rs.getString("title"), rs.getString("summary"), rs.getString("category"),
                instant(rs, "saved_at"), rs.getString("description"));
    }

    private UUID uuid(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, UUID.class);
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        var timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    public record OriginRow(UUID memberId, UUID placeId, String name, double latitude, double longitude,
                            Instant eventOccurredAt, PersonalContentRow content) {}

    public record PersonalContentRow(UUID contentId, UUID personalSaveId, String title, String summary,
                                    String category, Instant savedAt, String description) {}

    public record PersonalPlaceRow(UUID placeId, String name, double distanceMeters, PersonalContentRow content) {}

    public record PublicPlaceRow(UUID placeId, String name, double distanceMeters, UUID contentId,
                                String title, String summary, String category, String description) {}

    public record SemanticRow(UUID contentId, UUID personalSaveId, String title, String summary,
                              String category, Instant savedAt) {}
}
