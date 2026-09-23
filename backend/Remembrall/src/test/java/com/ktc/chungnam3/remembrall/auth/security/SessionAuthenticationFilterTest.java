package com.ktc.chungnam3.remembrall.auth.security;

import com.ktc.chungnam3.remembrall.auth.token.SessionTokenHasher;
import com.ktc.chungnam3.remembrall.domain.device.Device;
import com.ktc.chungnam3.remembrall.repository.DeviceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SessionAuthenticationFilterTest {

    private static final Instant NOW = Instant.parse("2026-09-23T00:00:00Z");

    private final DeviceRepository deviceRepository = mock(DeviceRepository.class);
    private final SessionTokenHasher sessionTokenHasher = new SessionTokenHasher();
    private final SessionAuthenticationFilter filter = new SessionAuthenticationFilter(
            deviceRepository,
            sessionTokenHasher,
            Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesWithUnexpiredSession() throws Exception {
        String token = "session-token";
        String hash = sessionTokenHasher.hash(token);
        UUID memberId = UUID.randomUUID();
        Device device = Device.create(memberId, hash, NOW.plusSeconds(60), NOW);
        when(deviceRepository.findBySessionTokenHash(hash)).thenReturn(Optional.of(device));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isEqualTo(
                new AuthenticatedMember(memberId, device.getId(), hash)
        );
    }

    @Test
    void doesNotAuthenticateExpiredSession() throws Exception {
        String token = "expired-session-token";
        String hash = sessionTokenHasher.hash(token);
        Device device = Device.create(UUID.randomUUID(), hash, NOW, NOW.minusSeconds(60));
        when(deviceRepository.findBySessionTokenHash(hash)).thenReturn(Optional.of(device));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
