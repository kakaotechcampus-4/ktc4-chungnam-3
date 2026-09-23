package com.ktc.chungnam3.remembrall.auth.controller;

import com.ktc.chungnam3.remembrall.auth.dto.KakaoLoginRequest;
import com.ktc.chungnam3.remembrall.auth.dto.SessionResponse;
import com.ktc.chungnam3.remembrall.auth.security.AuthenticatedMember;
import com.ktc.chungnam3.remembrall.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/kakao")
    public SessionResponse loginWithKakao(@Valid @RequestBody KakaoLoginRequest request) {
        return authService.loginWithKakao(request.kakaoAccessToken());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthenticatedMember member) {
        authService.logout(member.memberId(), member.deviceId(), member.sessionTokenHash());
        return ResponseEntity.noContent().build();
    }
}
