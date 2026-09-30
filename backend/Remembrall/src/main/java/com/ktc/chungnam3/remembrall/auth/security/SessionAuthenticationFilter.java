package com.ktc.chungnam3.remembrall.auth.security;

import com.ktc.chungnam3.remembrall.auth.token.SessionTokenHasher;
import com.ktc.chungnam3.remembrall.repository.DeviceRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.util.List;

@Component
public class SessionAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final DeviceRepository deviceRepository;
    private final SessionTokenHasher sessionTokenHasher;
    private final Clock clock;

    public SessionAuthenticationFilter(
            DeviceRepository deviceRepository,
            SessionTokenHasher sessionTokenHasher,
            Clock clock
    ) {
        this.deviceRepository = deviceRepository;
        this.sessionTokenHasher = sessionTokenHasher;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String sessionToken = resolveSessionToken(request);

        if (sessionToken != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            authenticate(request, sessionToken);
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request, String sessionToken) {
        String sessionTokenHash = sessionTokenHasher.hash(sessionToken);

        deviceRepository.findBySessionTokenHash(sessionTokenHash)
                .filter(device -> device.getSessionExpiresAt().isAfter(clock.instant()))
                .ifPresent(device -> {
                    AuthenticatedMember principal = new AuthenticatedMember(
                            device.getMemberId(),
                            device.getId(),
                            sessionTokenHash
                    );
                    UsernamePasswordAuthenticationToken authentication =
                            UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of());
                    authentication.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request)
                    );
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                });
    }

    private String resolveSessionToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return null;
        }

        String sessionToken = authorization.substring(BEARER_PREFIX.length()).trim();
        return sessionToken.isEmpty() ? null : sessionToken;
    }
}
