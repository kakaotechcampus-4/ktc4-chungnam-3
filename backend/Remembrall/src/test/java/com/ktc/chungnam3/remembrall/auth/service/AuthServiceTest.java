package com.ktc.chungnam3.remembrall.auth.service;

import com.ktc.chungnam3.remembrall.auth.dto.SessionResponse;
import com.ktc.chungnam3.remembrall.auth.kakao.KakaoUserClient;
import com.ktc.chungnam3.remembrall.auth.token.SessionProperties;
import com.ktc.chungnam3.remembrall.auth.token.SessionTokenGenerator;
import com.ktc.chungnam3.remembrall.auth.token.SessionTokenHasher;
import com.ktc.chungnam3.remembrall.domain.device.Device;
import com.ktc.chungnam3.remembrall.domain.member.AuthProvider;
import com.ktc.chungnam3.remembrall.domain.member.Member;
import com.ktc.chungnam3.remembrall.repository.DeviceRepository;
import com.ktc.chungnam3.remembrall.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-23T00:00:00Z");
    private static final Duration SESSION_TTL = Duration.ofDays(30);

    @Mock
    private KakaoUserClient kakaoUserClient;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private DeviceRepository deviceRepository;
    @Mock
    private SessionTokenGenerator sessionTokenGenerator;
    @Mock
    private SessionTokenHasher sessionTokenHasher;
    @Mock
    private PlatformTransactionManager transactionManager;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(mock(TransactionStatus.class));
        authService = new AuthService(
                kakaoUserClient,
                memberRepository,
                deviceRepository,
                sessionTokenGenerator,
                sessionTokenHasher,
                new SessionProperties(SESSION_TTL),
                Clock.fixed(NOW, ZoneOffset.UTC),
                transactionManager
        );
    }

    @Test
    void returnsRawSessionTokenAndStoresOnlyItsHash() {
        String providerUserId = "12345";
        String rawToken = "raw-session-token";
        String tokenHash = "a".repeat(64);
        Member member = Member.create(AuthProvider.KAKAO, providerUserId, NOW);

        when(kakaoUserClient.getProviderUserId("kakao-token")).thenReturn(providerUserId);
        when(memberRepository.findByAuthProviderAndProviderUserId(AuthProvider.KAKAO, providerUserId))
                .thenReturn(Optional.of(member));
        when(deviceRepository.findByMemberId(member.getId())).thenReturn(Optional.empty());
        when(sessionTokenGenerator.generate()).thenReturn(rawToken);
        when(sessionTokenHasher.hash(rawToken)).thenReturn(tokenHash);

        SessionResponse response = authService.loginWithKakao("kakao-token");

        ArgumentCaptor<Device> deviceCaptor = ArgumentCaptor.forClass(Device.class);
        verify(deviceRepository).save(deviceCaptor.capture());
        assertThat(response.sessionToken()).isEqualTo(rawToken);
        assertThat(response.sessionExpiresAt()).isEqualTo(NOW.plus(SESSION_TTL));
        assertThat(deviceCaptor.getValue().getSessionTokenHash()).isEqualTo(tokenHash);
        assertThat(deviceCaptor.getValue().getSessionTokenHash()).isNotEqualTo(rawToken);
    }

    @Test
    void clearsExistingFcmTokenWhenLoginReplacesSession() {
        String providerUserId = "12345";
        String rawToken = "new-raw-session-token";
        String tokenHash = "d".repeat(64);
        Member member = Member.create(AuthProvider.KAKAO, providerUserId, NOW);
        Device device = Device.create(
                member.getId(),
                "e".repeat(64),
                NOW.plus(SESSION_TTL),
                NOW.minusSeconds(60)
        );
        device.updateFcmToken("old-fcm-token", NOW.minusSeconds(60));

        when(kakaoUserClient.getProviderUserId("kakao-token")).thenReturn(providerUserId);
        when(memberRepository.findByAuthProviderAndProviderUserId(AuthProvider.KAKAO, providerUserId))
                .thenReturn(Optional.of(member));
        when(deviceRepository.findByMemberId(member.getId())).thenReturn(Optional.of(device));
        when(sessionTokenGenerator.generate()).thenReturn(rawToken);
        when(sessionTokenHasher.hash(rawToken)).thenReturn(tokenHash);

        authService.loginWithKakao("kakao-token");

        assertThat(device.getSessionTokenHash()).isEqualTo(tokenHash);
        assertThat(device.getFcmToken()).isNull();
        assertThat(device.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void clearsLoginAndPushInformationOnlyForMatchingMemberDeviceAndSessionHash() {
        UUID memberId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        String sessionTokenHash = "b".repeat(64);
        Member member = Member.create(AuthProvider.KAKAO, "12345", NOW);
        when(memberRepository.findByIdForUpdate(memberId)).thenReturn(Optional.of(member));

        authService.logout(memberId, deviceId, sessionTokenHash);

        verify(memberRepository).findByIdForUpdate(memberId);
        verify(deviceRepository).clearCurrentSession(
                eq(deviceId),
                eq(memberId),
                eq(sessionTokenHash),
                eq(NOW)
        );
    }
}
