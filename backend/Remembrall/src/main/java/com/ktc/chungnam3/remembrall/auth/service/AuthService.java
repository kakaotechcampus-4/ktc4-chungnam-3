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
    private final SessionTokenGenerator sessionTokenGenerator;
    private final SessionTokenHasher sessionTokenHasher;
    private final SessionProperties sessionProperties;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public AuthService(
            KakaoUserClient kakaoUserClient,
            MemberRepository memberRepository,
            DeviceRepository deviceRepository,
            SessionTokenGenerator sessionTokenGenerator,
            SessionTokenHasher sessionTokenHasher,
            SessionProperties sessionProperties,
            Clock clock,
            PlatformTransactionManager transactionManager
    ) {
        this.kakaoUserClient = kakaoUserClient;
        this.memberRepository = memberRepository;
        this.deviceRepository = deviceRepository;
        this.sessionTokenGenerator = sessionTokenGenerator;
        this.sessionTokenHasher = sessionTokenHasher;
        this.sessionProperties = sessionProperties;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public SessionResponse loginWithKakao(String kakaoAccessToken) {
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
                    .orElseThrow(() -> new IllegalStateException("회원 생성 또는 조회에 실패했습니다."));

            Device device = deviceRepository.findByMemberId(member.getId()).orElse(null);
            return issueAndStoreSession(member.getId(), device, now);
        }));
    }

    public void logout(UUID memberId, UUID deviceId, String sessionTokenHash) {
        transactionTemplate.executeWithoutResult(status -> memberRepository
                .findByIdForUpdate(memberId)
                .ifPresent(member -> deviceRepository.clearCurrentSession(
                        deviceId,
                        memberId,
                        sessionTokenHash,
                        clock.instant()
                ))
        );
    }

    private SessionResponse issueAndStoreSession(UUID memberId, Device device, Instant now) {
        String sessionToken = sessionTokenGenerator.generate();
        String sessionTokenHash = sessionTokenHasher.hash(sessionToken);
        Instant sessionExpiresAt = now.plus(sessionProperties.ttl());

        if (device == null) {
            deviceRepository.save(Device.create(
                    memberId,
                    sessionTokenHash,
                    sessionExpiresAt,
                    now
            ));
        } else {
            device.replaceSession(sessionTokenHash, sessionExpiresAt, now);
        }

        return new SessionResponse(sessionToken, sessionExpiresAt);
    }
}
