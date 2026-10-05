package com.ktc.chungnam3.remembrall.recall.agent;

import java.util.List;
import java.util.UUID;

public record ProposalPayload(List<Item> items) {
    public record Item(UUID contentId, UUID placeId, ProposalSource source) {
    }
}
