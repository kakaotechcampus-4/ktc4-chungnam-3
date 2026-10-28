package com.ktc.chungnam3.remembrall.personalsave.controller;

import com.ktc.chungnam3.remembrall.auth.security.AuthenticatedMember;
import com.ktc.chungnam3.remembrall.personalsave.dto.PersonalSaveRequest;
import com.ktc.chungnam3.remembrall.personalsave.dto.PersonalSaveResponse;
import com.ktc.chungnam3.remembrall.personalsave.service.PersonalSaveService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/personal-saves")
public class PersonalSaveController {
    private final PersonalSaveService saves;

    public PersonalSaveController(PersonalSaveService saves) {
        this.saves = saves;
    }

    @PostMapping
    public ResponseEntity<PersonalSaveResponse> save(@AuthenticationPrincipal AuthenticatedMember member,
                                                     @Valid @RequestBody PersonalSaveRequest request) {
        var claim = saves.save(member.memberId(), request.url());
        return ResponseEntity.status(claim.personalSaveCreated() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(PersonalSaveResponse.from(claim));
    }

    @GetMapping
    public PersonalSaveResponse.Page list(@AuthenticationPrincipal AuthenticatedMember member,
                                           @Valid @ModelAttribute PersonalSaveRequest.Query query) {
        return saves.list(member.memberId(), query.size(), query.cursor());
    }

    @GetMapping("/{personalSaveId}")
    public PersonalSaveResponse.Detail detail(@AuthenticationPrincipal AuthenticatedMember member,
                                               @PathVariable UUID personalSaveId) {
        return saves.detail(member.memberId(), personalSaveId);
    }

    @DeleteMapping("/{personalSaveId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedMember member,
                                       @PathVariable UUID personalSaveId) {
        saves.delete(member.memberId(), personalSaveId);
        return ResponseEntity.noContent().build();
    }
}
