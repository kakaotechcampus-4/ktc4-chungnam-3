package com.ktc.chungnam3.remembrall.auth.service;

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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GuestAuthServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-15T00:00:00Z");
    private static final Duration TTL = Duration.ofDays(30);

    @Mock private KakaoUserClient kakao;
    @Mock private MemberRepository members;
    @Mock private DeviceRepository devices;
    @Mock private SessionTokenGenerator generator;
    @Mock private PlatformTransactionManager transactionManager;

    private final SessionTokenHasher hasher = new SessionTokenHasher();
    private final UUID installationId = UUID.fromString("f47ac10b-58cc-4372-a567-0e02b2c3d479");
    private AuthService service;
    private Member member;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(mock(TransactionStatus.class));
        when(generator.generate()).thenReturn("new-session-token");
        member = Member.create(AuthProvider.GUEST, hasher.hash(installationId.toString()), NOW);
        when(members.findByAuthProviderAndProviderUserId(AuthProvider.GUEST, member.getProviderUserId()))
                .thenReturn(Optional.of(member));
        service = new AuthService(kakao, members, devices, generator, hasher,
                new SessionProperties(TTL), Clock.fixed(NOW, ZoneOffset.UTC), transactionManager);
    }

    @Test
    void hashesGuestCredentialAndLocksMemberBeforeIssuingSessionWithoutExternalCall() {
        when(devices.findByMemberId(member.getId())).thenReturn(Optional.empty());

        var response = service.loginWithGuest(installationId);

        var order = inOrder(members, devices, generator);
        order.verify(members).insertIfAbsent(any(UUID.class), eq("GUEST"), eq(member.getProviderUserId()), eq(NOW));
        order.verify(members).findByAuthProviderAndProviderUserId(AuthProvider.GUEST, member.getProviderUserId());
        order.verify(devices).findByMemberId(member.getId());
        order.verify(generator).generate();
        var captured = org.mockito.ArgumentCaptor.forClass(Device.class);
        order.verify(devices).save(captured.capture());
        assertThat(member.getProviderUserId()).hasSize(64).isNotEqualTo(installationId.toString());
        assertThat(captured.getValue().getSessionTokenHash()).isEqualTo(hasher.hash(response.sessionToken()));
        assertThat(response.sessionToken()).isEqualTo("new-session-token");
        assertThat(response.sessionExpiresAt()).isEqualTo(NOW.plus(TTL));
        verifyNoInteractions(kakao);
    }

    @Test
    void returningGuestReplacesExistingDeviceSessionAndClearsFcmToken() {
        var device = Device.create(member.getId(), hasher.hash("old-session"), NOW.plus(TTL), NOW.minusSeconds(60));
        device.updateFcmToken("old-fcm-token", NOW.minusSeconds(60));
        when(devices.findByMemberId(member.getId())).thenReturn(Optional.of(device));

        var response = service.loginWithGuest(installationId);

        assertThat(device.getSessionTokenHash()).isEqualTo(hasher.hash(response.sessionToken()));
        assertThat(device.getSessionExpiresAt()).isEqualTo(response.sessionExpiresAt());
        assertThat(device.getFcmToken()).isNull();
        assertThat(device.getUpdatedAt()).isEqualTo(NOW);
        verify(devices, never()).save(any());
        verifyNoInteractions(kakao);
    }
}
