package com.ktc.chungnam3.remembrall.recall.controller;

import com.ktc.chungnam3.remembrall.auth.security.AuthenticatedMember;
import com.ktc.chungnam3.remembrall.recall.dto.LocationEventRequest;
import com.ktc.chungnam3.remembrall.recall.service.LocationEventService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/location-events")
public class LocationEventController {

    private final LocationEventService eventService;

    public LocationEventController(LocationEventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    public ResponseEntity<Void> receive(
            @AuthenticationPrincipal AuthenticatedMember member,
            @Valid @RequestBody LocationEventRequest event
    ) {
        eventService.accept(member.memberId(), event);
        return ResponseEntity.accepted().build();
    }
}
