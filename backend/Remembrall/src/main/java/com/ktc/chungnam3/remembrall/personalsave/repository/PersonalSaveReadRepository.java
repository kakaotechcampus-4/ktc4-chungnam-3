package com.ktc.chungnam3.remembrall.personalsave.repository;

import com.ktc.chungnam3.remembrall.domain.content.ContentAnalysisStatus;
import com.ktc.chungnam3.remembrall.personalsave.dto.PersonalSaveResponse;
import com.ktc.chungnam3.remembrall.personalsave.service.PersonalSaveAnalysisService;
import com.ktc.chungnam3.remembrall.personalsave.service.PersonalSaveCursor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PersonalSaveReadRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public PersonalSaveReadRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<PersonalSaveResponse.Item> findPage(UUID memberId, PersonalSaveCursor cursor,
                                                   int limit, int maxSummaryLength) {
        var parameters = new MapSqlParameterSource("memberId", memberId)
                .addValue("limit", limit).addValue("summaryLength", maxSummaryLength);
        String afterCursor = "";
        if (cursor != null) {
            afterCursor = " AND (ps.saved_at < :savedAt OR (ps.saved_at = :savedAt AND ps.id > :id))";
            parameters.addValue("savedAt", Timestamp.from(cursor.savedAt())).addValue("id", cursor.id());
        }
        String sql = """
                SELECT ps.id, ps.content_id, c.video_id, c.title, left(c.summary, :summaryLength) AS summary,
                       c.analysis_status, ps.saved_at,
                       (SELECT count(*) FROM content_place cp WHERE cp.content_id = c.id) AS place_count
                  FROM personal_save ps
                  JOIN content c ON c.id = ps.content_id
                 WHERE ps.member_id = :memberId
                """ + afterCursor + " ORDER BY ps.saved_at DESC, ps.id ASC LIMIT :limit";
        return jdbc.query(sql, parameters, (row, index) -> new PersonalSaveResponse.Item(
                row.getObject("id", UUID.class), row.getObject("content_id", UUID.class),
                row.getString("video_id"), row.getString("title"), row.getString("summary"),
                ContentAnalysisStatus.valueOf(row.getString("analysis_status")), row.getLong("place_count"),
                row.getTimestamp("saved_at").toInstant()));
    }

    public Optional<PersonalSaveResponse.Detail> findOwnedDetail(UUID memberId, UUID personalSaveId) {
        return jdbc.query("""
                SELECT ps.id, ps.content_id, ps.saved_at, c.video_id, c.title, c.summary, c.category,
                       c.analysis_status,
                       CASE WHEN c.analysis_status IN ('PARTIAL', 'FAILED')
                            THEN c.last_analysis_error_code END AS failure_code
                  FROM personal_save ps
                  JOIN content c ON c.id = ps.content_id
                 WHERE ps.id = :id AND ps.member_id = :memberId
                """, new MapSqlParameterSource("id", personalSaveId).addValue("memberId", memberId),
                (row, index) -> new PersonalSaveResponse.Detail(
                        row.getObject("id", UUID.class), row.getObject("content_id", UUID.class),
                        row.getString("video_id"), PersonalSaveAnalysisService.sourceUrl(row.getString("video_id")),
                        row.getString("title"), row.getString("summary"), row.getString("category"),
                        ContentAnalysisStatus.valueOf(row.getString("analysis_status")), row.getString("failure_code"),
                        row.getTimestamp("saved_at").toInstant(), List.of())).stream().findFirst();
    }

    public List<PersonalSaveResponse.Place> findPlaces(UUID contentId) {
        return jdbc.query("""
                SELECT p.id, p.name, p.address, p.latitude, p.longitude, cp.description
                  FROM content_place cp
                  JOIN place p ON p.id = cp.place_id
                 WHERE cp.content_id = :contentId
                 ORDER BY p.id
                """, new MapSqlParameterSource("contentId", contentId),
                (row, index) -> new PersonalSaveResponse.Place(row.getObject("id", UUID.class), row.getString("name"),
                        row.getString("address"), row.getDouble("latitude"), row.getDouble("longitude"),
                        row.getString("description")));
    }
}
