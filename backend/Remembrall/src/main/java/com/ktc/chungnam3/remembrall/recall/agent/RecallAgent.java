package com.ktc.chungnam3.remembrall.recall.agent;

import com.ktc.chungnam3.remembrall.recall.dto.LocationEventRequest;

import java.util.UUID;

public interface RecallAgent {

    RecallAgentResult execute(UUID executionId, LocationEventRequest event) throws Exception;
}
