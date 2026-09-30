package com.ktc.chungnam3.remembrall.device.service;

import com.ktc.chungnam3.remembrall.common.exception.ApiException;
import com.ktc.chungnam3.remembrall.common.exception.ErrorCode;
import com.ktc.chungnam3.remembrall.repository.DeviceRepository;
import com.ktc.chungnam3.remembrall.repository.MemberRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class DeviceService {

    private static final int MAX_REGISTRATION_ATTEMPTS = 3;

    private final MemberRepository memberRepository;
    private final DeviceRepository deviceRepository;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public DeviceService(
            MemberRepository memberRepository,
            DeviceRepository deviceRepository,
            Clock clock,
            PlatformTransactionManager transactionManager
    ) {
        this.memberRepository = memberRepository;
        this.deviceRepository = deviceRepository;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public void updateFcmToken(
            UUID memberId,
            UUID deviceId,
            String sessionTokenHash,
            String fcmToken
    ) {
        DataIntegrityViolationException lastConflict = null;

        for (int attempt = 0; attempt < MAX_REGISTRATION_ATTEMPTS; attempt++) {
            try {
                registerFcmToken(memberId, deviceId, sessionTokenHash, fcmToken);
                return;
            } catch (DataIntegrityViolationException exception) {
                lastConflict = exception;
            }
        }

        throw new ApiException(ErrorCode.FCM_TOKEN_CONFLICT, lastConflict);
    }

    private void registerFcmToken(
            UUID memberId,
            UUID deviceId,
            String sessionTokenHash,
            String fcmToken
    ) {
        Objects.requireNonNull(transactionTemplate.execute(status -> {
            Instant now = clock.instant();
            memberRepository.findByIdForUpdate(memberId)
                    .orElseThrow(() -> new ApiException(ErrorCode.INVALID_SESSION));

            deviceRepository.clearFcmTokenFromOtherDevices(deviceId, fcmToken, now);
            int updatedRows = deviceRepository.registerFcmTokenForCurrentSession(
                    deviceId,
                    memberId,
                    sessionTokenHash,
                    fcmToken,
                    now,
                    now
            );
            if (updatedRows != 1) {
                throw new ApiException(ErrorCode.INVALID_SESSION);
            }
            return Boolean.TRUE;
        }));
    }
}
