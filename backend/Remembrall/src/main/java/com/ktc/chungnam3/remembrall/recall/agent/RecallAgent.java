package com.ktc.chungnam3.remembrall.recall.agent;

import java.util.UUID;

public interface RecallAgent {

    String version();

    RecallAgentResult execute(UUID executionId, RecallAgentInput input) throws Exception;
}
