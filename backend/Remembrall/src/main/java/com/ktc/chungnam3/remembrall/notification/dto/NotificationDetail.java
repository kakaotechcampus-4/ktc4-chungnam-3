package com.ktc.chungnam3.remembrall.notification.dto;

import com.ktc.chungnam3.remembrall.domain.recallexecution.ProposalType;
import com.ktc.chungnam3.remembrall.recall.agent.ProposalSource;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record NotificationDetail(UUID notificationId, String title, String body, ProposalType proposalType,
                                 Instant sentAt, Instant openedAt, List<Item> items) {
    public record Item(UUID contentId, ProposalSource source, UUID personalSaveId,
                       String title, String summary, Place place) {
    }

    public record Place(UUID placeId, String name, String address, double latitude, double longitude) {
    }
}
