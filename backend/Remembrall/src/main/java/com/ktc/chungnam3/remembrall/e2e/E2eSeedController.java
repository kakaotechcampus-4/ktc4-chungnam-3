package com.ktc.chungnam3.remembrall.e2e;

import com.ktc.chungnam3.remembrall.auth.security.AuthenticatedMember;
import com.ktc.chungnam3.remembrall.common.exception.ApiException;
import com.ktc.chungnam3.remembrall.common.exception.ErrorCode;
import com.ktc.chungnam3.remembrall.recall.agent.RecallAgentInput;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Profile("e2e")
@RequestMapping("/dev/e2e")
public class E2eSeedController {
    private final E2eSeedService seeds;
    private final E2eAgentInputRecorder inputs;
    private final JdbcTemplate jdbc;

    public E2eSeedController(E2eSeedService seeds, E2eAgentInputRecorder inputs, JdbcTemplate jdbc) {
        this.seeds = seeds;
        this.inputs = inputs;
        this.jdbc = jdbc;
    }

    @PostMapping("/seed")
    public E2eSeedService.SeedResponse seed(@AuthenticationPrincipal AuthenticatedMember member) {
        return seeds.seed(member.memberId());
    }

    @GetMapping("/executions/{executionId}/input")
    public RecallAgentInput input(@AuthenticationPrincipal AuthenticatedMember member,
                                  @PathVariable UUID executionId) {
        Boolean owned = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM recall_execution r JOIN trigger t ON t.id = r.trigger_id
                               WHERE r.id = ? AND t.member_id = ?)
                """, Boolean.class, executionId, member.memberId());
        if (!Boolean.TRUE.equals(owned)) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        return inputs.find(executionId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }
}
