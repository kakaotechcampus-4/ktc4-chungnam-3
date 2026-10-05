package com.ktc.chungnam3.remembrall.notification.controller;

import com.ktc.chungnam3.remembrall.auth.security.AuthenticatedMember;
import com.ktc.chungnam3.remembrall.notification.dto.NotificationDetail;
import com.ktc.chungnam3.remembrall.notification.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @PostMapping("/{notificationId}/open")
    public ResponseEntity<Void> open(@AuthenticationPrincipal AuthenticatedMember member,
                                     @PathVariable UUID notificationId) {
        notifications.open(member.memberId(), notificationId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{notificationId}")
    public NotificationDetail detail(@AuthenticationPrincipal AuthenticatedMember member,
                                     @PathVariable UUID notificationId) {
        return notifications.detail(member.memberId(), notificationId);
    }
}
