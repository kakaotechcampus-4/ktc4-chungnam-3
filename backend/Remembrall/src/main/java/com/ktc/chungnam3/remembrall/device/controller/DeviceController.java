package com.ktc.chungnam3.remembrall.device.controller;

import com.ktc.chungnam3.remembrall.auth.security.AuthenticatedMember;
import com.ktc.chungnam3.remembrall.device.dto.FcmTokenRequest;
import com.ktc.chungnam3.remembrall.device.service.DeviceService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @PutMapping("/fcm-token")
    public ResponseEntity<Void> updateFcmToken(
            @AuthenticationPrincipal AuthenticatedMember member,
            @Valid @RequestBody FcmTokenRequest request
    ) {
        deviceService.updateFcmToken(
                member.memberId(),
                member.deviceId(),
                member.sessionTokenHash(),
                request.fcmToken()
        );
        return ResponseEntity.noContent().build();
    }
}
