package com.ktc.chungnam3.remembrall.auth.service;

import com.ktc.chungnam3.remembrall.auth.dto.TokenResponse;
import com.ktc.chungnam3.remembrall.auth.kakao.KakaoUserClient;
import com.ktc.chungnam3.remembrall.auth.token.JwtProperties;
import com.ktc.chungnam3.remembrall.auth.token.JwtTokenProvider;
import com.ktc.chungnam3.remembrall.auth.token.RefreshTokenGenerator;
import com.ktc.chungnam3.remembrall.auth.token.RefreshTokenHasher;
import com.ktc.chungnam3.remembrall.common.exception.ApiException;
import com.ktc.chungnam3.remembrall.common.exception.ErrorCode;
import com.ktc.chungnam3.remembrall.domain.device.Device;
import com.ktc.chungnam3.remembrall.domain.member.AuthProvider;
import com.ktc.chungnam3.remembrall.domain.member.Member;
import com.ktc.chungnam3.remembrall.repository.DeviceRepository;
import com.ktc.chungnam3.remembrall.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class AuthService {

    private final KakaoUserClient kakaoUserClient;
    private final MemberRepository memberRepository;
    private final DeviceRepository deviceRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenGenerator refreshTokenGenerator;
    private final RefreshTokenHasher refreshTokenHasher;
    private final JwtProperties jwtProperties;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public AuthService(
            KakaoUserClient kakaoUserClient,
            MemberRepository memberRepository,
            DeviceRepository deviceRepository,
            JwtTokenProvider jwtTokenProvider,
            RefreshTokenGenerator refreshTokenGenerator,
            RefreshTokenHasher refreshTokenHasher,
            JwtProperties jwtProperties,
            Clock clock,
            PlatformTransactionManager transactionManager
    ) {
        this.kakaoUserClient = kakaoUserClient;
        this.memberRepository = memberRepository;
        this.deviceRepository = deviceRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenGenerator = refreshTokenGenerator;
        this.refreshTokenHasher = refreshTokenHasher;
        this.jwtProperties = jwtProperties;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public TokenResponse loginWithKakao(String kakaoAccessToken) {
        String providerUserId = kakaoUserClient.getProviderUserId(kakaoAccessToken);

        return Objects.requireNonNull(transactionTemplate.execute(status -> {
            Instant now = clock.instant();
            memberRepository.insertIfAbsent(
                    UUID.randomUUID(),
                    AuthProvider.KAKAO.name(),
                    providerUserId,
                    now
            );

            Member member = memberRepository
                    .findByAuthProviderAndProviderUserId(AuthProvider.KAKAO, providerUserId)
                    .orElseThrow(() -> new IllegalStateException("Member 생성 또는 조회에 실패했습니다."));

            Device device = deviceRepository.findByMemberId(member.getId()).orElse(null);
            return issueAndStoreTokens(member.getId(), device, now);
        }));
    }

    public TokenResponse refresh(String refreshToken) {
        String refreshTokenHash = refreshTokenHasher.hash(refreshToken);

        return Objects.requireNonNull(transactionTemplate.execute(status -> {
            Instant now = clock.instant();
            Device device = deviceRepository.findByRefreshTokenHash(refreshTokenHash)
                    .orElseThrow(() -> new ApiException(ErrorCode.INVALID_REFRESH_TOKEN));

            if (!device.getRefreshTokenExpiresAt().isAfter(now)) {
                throw new ApiException(ErrorCode.EXPIRED_REFRESH_TOKEN);
            }
            return issueAndStoreTokens(device.getMemberId(), device, now);
        }));
    }

    public void logout(UUID memberId) {
        transactionTemplate.executeWithoutResult(status -> deviceRepository.deleteByMemberId(memberId));
    }

    private TokenResponse issueAndStoreTokens(UUID memberId, Device device, Instant now) {
        JwtTokenProvider.AccessToken accessToken = jwtTokenProvider.issueAccessToken(memberId);
        String refreshToken = refreshTokenGenerator.generate();
        String refreshTokenHash = refreshTokenHasher.hash(refreshToken);
        Instant refreshTokenExpiresAt = now.plus(jwtProperties.refreshTokenTtl());

        if (device == null) {
            deviceRepository.save(Device.create(
                    memberId,
                    refreshTokenHash,
                    refreshTokenExpiresAt,
                    now
            ));
        } else {
            device.rotateRefreshToken(refreshTokenHash, refreshTokenExpiresAt, now);
        }

        return new TokenResponse(
                accessToken.value(),
                refreshToken,
                accessToken.expiresAt(),
                refreshTokenExpiresAt
        );
    }
}
