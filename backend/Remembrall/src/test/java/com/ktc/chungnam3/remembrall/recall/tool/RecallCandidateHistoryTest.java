package com.ktc.chungnam3.remembrall.recall.tool;

import com.ktc.chungnam3.remembrall.recall.agent.RecallAgentInput;
import com.ktc.chungnam3.remembrall.recall.agent.ProposalSource;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RecallCandidateHistoryTest {
    @Test
    void collectsEveryInitialCandidateAndReturnedIdsWithoutSharingStateAcrossExecutions() {
        UUID origin = UUID.randomUUID(), personalPlace = UUID.randomUUID(), publicPlace = UUID.randomUUID();
        UUID personal = UUID.randomUUID(), publicContent = UUID.randomUUID(), semantic = UUID.randomUUID();
        var own = new RecallAgentInput.PersonalContent(personal, UUID.randomUUID(), "제목", "요약", "CAFE", null, "소개");
        var input = new RecallAgentInput(new RecallAgentInput.Origin(origin, "출발점", 36, 127, List.of(own)),
                List.of(new RecallAgentInput.PersonalPlace(personalPlace, "개인", 1, List.of(own))),
                List.of(new RecallAgentInput.PublicPlace(publicPlace, "공용", 2,
                        List.of(new RecallAgentInput.PublicContent(publicContent, "제목", "요약", "CAFE", "소개")))),
                List.of(new RecallAgentInput.SemanticContent(semantic, ProposalSource.PUBLIC, null, "제목", "요약", "CAFE", null)), null);
        var first = new RecallCandidateHistory(input);
        var second = new RecallCandidateHistory(input);
        UUID found = UUID.randomUUID();
        first.observe(JsonMapper.builder().build().readTree("{\"contents\":[{\"contentId\":\"" + found + "\"}]}"));
        assertThat(first.placeIds()).containsExactlyInAnyOrder(origin, personalPlace, publicPlace);
        assertThat(first.contentIds()).containsExactlyInAnyOrder(personal, publicContent, semantic, found);
        assertThat(second.contentIds()).doesNotContain(found);
    }
}
