package com.ktc.chungnam3.remembrall.recall.tool;

import java.util.UUID;

public record RecallToolScope(UUID memberId, UUID triggerPlaceId, double latitude, double longitude) {}
