package com.ktc.chungnam3.remembrall.geofence.controller;

import com.ktc.chungnam3.remembrall.auth.security.AuthenticatedMember;
import com.ktc.chungnam3.remembrall.geofence.dto.GeofenceQuery;
import com.ktc.chungnam3.remembrall.geofence.dto.GeofenceResponse;
import com.ktc.chungnam3.remembrall.geofence.service.GeofenceService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/geofences")
public class GeofenceController {
    private final GeofenceService geofences;

    public GeofenceController(GeofenceService geofences) {
        this.geofences = geofences;
    }

    @GetMapping
    public GeofenceResponse list(@AuthenticationPrincipal AuthenticatedMember member,
                                 @Valid @ModelAttribute GeofenceQuery query) {
        return geofences.list(member.memberId(), query);
    }
}
