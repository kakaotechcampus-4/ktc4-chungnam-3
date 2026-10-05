package com.ktc.chungnam3.remembrall.notification.service;

import com.ktc.chungnam3.remembrall.domain.notification.Notification;
import com.ktc.chungnam3.remembrall.notification.push.PushMessage;
import com.ktc.chungnam3.remembrall.notification.push.PushSendException;
import com.ktc.chungnam3.remembrall.notification.push.PushSender;
import com.ktc.chungnam3.remembrall.repository.DeviceRepository;
import com.ktc.chungnam3.remembrall.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class NotificationDeliveryService {
    private final NotificationRepository notifications;
    private final DeviceRepository devices;
    private final PushSender sender;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public NotificationDeliveryService(NotificationRepository notifications, DeviceRepository devices,
                                       PushSender sender, Clock clock, PlatformTransactionManager manager) {
        this.notifications = notifications;
        this.devices = devices;
        this.sender = sender;
        this.clock = clock;
        transactions = new TransactionTemplate(manager);
        transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void send(UUID notificationId) {
        Attempt attempt = transactions.execute(status -> claim(notificationId));
        if (attempt == null) {
            return;
        }
        String providerMessageId;
        try {
            Notification notification = attempt.notification();
            providerMessageId = sender.send(PushMessage.notification(attempt.token(), notification.getTitle(),
                    notification.getBody(), notificationId, clock.instant()));
            if (providerMessageId == null || providerMessageId.isBlank()) {
                throw new PushSendException("FCM_SEND_ERROR");
            }
        } catch (PushSendException exception) {
            failed(attempt, exception.failureCode());
            return;
        } catch (RuntimeException exception) {
            failed(attempt, "FCM_SEND_ERROR");
            return;
        }
        String messageId = providerMessageId;
        transactions.executeWithoutResult(status -> notifications.markSent(notificationId, messageId, clock.instant()));
    }

    private Attempt claim(UUID id) {
        Instant now = clock.instant();
        if (notifications.claimSending(id, now) != 1) {
            return null;
        }
        Notification notification = notifications.findById(id).orElseThrow();
        var device = devices.findActiveWithFcmToken(notification.getMemberId(), now).orElse(null);
        if (device == null || device.getFcmToken().isBlank()) {
            notifications.markFailed(id, "NO_FCM_TOKEN", now);
            return null;
        }
        return new Attempt(notification, device.getId(), device.getFcmToken());
    }

    private void failed(Attempt attempt, String code) {
        transactions.executeWithoutResult(status -> {
            Instant now = clock.instant();
            notifications.markFailed(attempt.notification().getId(), code, now);
            if ("UNREGISTERED".equals(code)) {
                devices.clearFcmTokenIfMatches(attempt.deviceId(), attempt.token(), now);
            }
        });
    }

    private record Attempt(Notification notification, UUID deviceId, String token) {
    }
}
