package com.ktc.chungnam3.remembrall.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.UUID;

@Repository
public class PublicCandidateRepository {

    public static final String ACTIVE_CONTRIBUTION_EXISTS = """
            EXISTS (
                SELECT 1 FROM personal_save ps
                JOIN member_consent mc ON mc.member_id = ps.member_id
                    AND mc.consent_type = 'PUBLIC_CANDIDATE_CONTRIBUTION'
                    AND mc.withdrawn_at IS NULL
                WHERE ps.content_id = c.id
            )
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public PublicCandidateRepository(JdbcTemplate jdbc) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbc);
    }

    public boolean isEligibleContent(UUID contentId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM content c WHERE c.id = :contentId
                        AND c.analysis_status <> 'FAILED' AND c.source_status <> 'UNAVAILABLE'
                        AND %s
                )
                """.formatted(ACTIVE_CONTRIBUTION_EXISTS), Map.of("contentId", contentId), Boolean.class));
    }
}
