package com.ktc.chungnam3.remembrall.repository;

import com.ktc.chungnam3.remembrall.recall.config.RecallToolProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Repository
public class RecentProposalRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final RecallToolProperties properties;
    private final Clock clock;

    public RecentProposalRepository(JdbcTemplate jdbc, RecallToolProperties properties, Clock clock) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbc);
        this.properties = properties;
        this.clock = clock;
    }

    public RecentProposals findRecent(UUID memberId) {
        var placeIds = new HashSet<UUID>();
        var contentIds = new HashSet<UUID>();
        jdbc.query("""
                SELECT item ->> 'placeId' AS place_id, item ->> 'contentId' AS content_id
                FROM notification n
                JOIN recall_execution e ON e.id = n.execution_id
                CROSS JOIN LATERAL jsonb_array_elements(COALESCE(e.proposal_payload -> 'items', '[]'::jsonb)) item
                WHERE n.member_id = :memberId AND n.status = 'SENT' AND n.sent_at >= :since
                """, Map.of("memberId", memberId,
                        "since", Timestamp.from(clock.instant().minus(properties.recentProposalWindow()))),
                (RowCallbackHandler) rs -> {
                    String placeId = rs.getString("place_id");
                    String contentId = rs.getString("content_id");
                    if (placeId != null) {
                        placeIds.add(UUID.fromString(placeId));
                    } else if (contentId != null) {
                        contentIds.add(UUID.fromString(contentId));
                    }
                });
        return new RecentProposals(Set.copyOf(placeIds), Set.copyOf(contentIds));
    }

    public record RecentProposals(Set<UUID> placeIds, Set<UUID> contentIds) {
        public static final RecentProposals NONE = new RecentProposals(Set.of(), Set.of());
    }
}
