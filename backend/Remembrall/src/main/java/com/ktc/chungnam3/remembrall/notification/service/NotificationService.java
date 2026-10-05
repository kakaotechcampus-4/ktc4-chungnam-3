package com.ktc.chungnam3.remembrall.notification.service;

import com.ktc.chungnam3.remembrall.common.exception.ApiException;
import com.ktc.chungnam3.remembrall.common.exception.ErrorCode;
import com.ktc.chungnam3.remembrall.notification.dto.NotificationDetail;
import com.ktc.chungnam3.remembrall.recall.agent.ProposalPayload;
import com.ktc.chungnam3.remembrall.recall.agent.ProposalSource;
import com.ktc.chungnam3.remembrall.repository.ContentPlaceRepository;
import com.ktc.chungnam3.remembrall.repository.ContentRepository;
import com.ktc.chungnam3.remembrall.repository.NotificationRepository;
import com.ktc.chungnam3.remembrall.repository.PersonalSaveRepository;
import com.ktc.chungnam3.remembrall.repository.PlaceRepository;
import com.ktc.chungnam3.remembrall.repository.RecallExecutionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.ArrayList;
import java.util.UUID;

@Service
public class NotificationService {
    private final NotificationRepository notifications;
    private final RecallExecutionRepository executions;
    private final ContentRepository contents;
    private final PlaceRepository places;
    private final ContentPlaceRepository links;
    private final PersonalSaveRepository saves;
    private final ObjectMapper mapper;
    private final Clock clock;

    public NotificationService(NotificationRepository notifications, RecallExecutionRepository executions,
                               ContentRepository contents, PlaceRepository places, ContentPlaceRepository links,
                               PersonalSaveRepository saves, ObjectMapper mapper, Clock clock) {
        this.notifications = notifications;
        this.executions = executions;
        this.contents = contents;
        this.places = places;
        this.links = links;
        this.saves = saves;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional
    public void open(UUID memberId, UUID notificationId) {
        if (notifications.openIfUnopened(notificationId, memberId, clock.instant()) == 0
                && !notifications.existsByIdAndMemberId(notificationId, memberId)) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
    }

    @Transactional(readOnly = true)
    public NotificationDetail detail(UUID memberId, UUID notificationId) {
        var notification = notifications.findByIdAndMemberId(notificationId, memberId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        var execution = executions.findById(notification.getExecutionId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        var payload = mapper.convertValue(execution.getProposalPayload(), ProposalPayload.class);
        var items = new ArrayList<NotificationDetail.Item>();
        for (var item : payload.items()) {
            var content = contents.findById(item.contentId()).orElse(null);
            if (content == null || item.source() != ProposalSource.PERSONAL) {
                continue;
            }
            var save = saves.findByMemberIdAndContentId(memberId, item.contentId()).orElse(null);
            if (save == null) {
                continue;
            }
            NotificationDetail.Place place = null;
            if (item.placeId() != null) {
                var entity = places.findById(item.placeId()).orElse(null);
                if (entity == null || links.findByContent_IdAndPlace_Id(item.contentId(), item.placeId()).isEmpty()) {
                    continue;
                }
                place = new NotificationDetail.Place(entity.getId(), entity.getName(), entity.getAddress(),
                        entity.getLatitude(), entity.getLongitude());
            }
            items.add(new NotificationDetail.Item(content.getId(), item.source(), save.getId(),
                    content.getTitle(), content.getSummary(), place));
        }
        return new NotificationDetail(notificationId, notification.getTitle(), notification.getBody(),
                execution.getProposalType(), notification.getSentAt(), notification.getOpenedAt(), items);
    }
}
