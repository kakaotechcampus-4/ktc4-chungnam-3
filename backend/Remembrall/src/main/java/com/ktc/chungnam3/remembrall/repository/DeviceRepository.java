package com.ktc.chungnam3.remembrall.repository;

import com.ktc.chungnam3.remembrall.domain.device.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface DeviceRepository extends JpaRepository<Device, UUID> {

    Optional<Device> findByMemberId(UUID memberId);

    Optional<Device> findBySessionTokenHash(String sessionTokenHash);

    @Query("""
            SELECT device FROM Device device WHERE device.memberId = :memberId
              AND device.sessionTokenHash IS NOT NULL AND device.sessionExpiresAt > :now
              AND device.fcmToken IS NOT NULL
            """)
    Optional<Device> findActiveWithFcmToken(@Param("memberId") UUID memberId, @Param("now") Instant now);

    @Modifying
    @Query("""
            UPDATE Device device SET device.fcmToken = NULL, device.updatedAt = :now
             WHERE device.id = :deviceId AND device.fcmToken = :token
            """)
    int clearFcmTokenIfMatches(@Param("deviceId") UUID deviceId, @Param("token") String token,
                              @Param("now") Instant now);

    @Modifying
    @Query("""
            UPDATE Device device
               SET device.sessionTokenHash = NULL,
                   device.sessionExpiresAt = NULL,
                   device.fcmToken = NULL,
                   device.updatedAt = :updatedAt
            WHERE device.id = :deviceId
              AND device.memberId = :memberId
              AND device.sessionTokenHash = :sessionTokenHash
            """)
    int clearCurrentSession(
            @Param("deviceId") UUID deviceId,
            @Param("memberId") UUID memberId,
            @Param("sessionTokenHash") String sessionTokenHash,
            @Param("updatedAt") Instant updatedAt
    );

    @Modifying
    @Query("""
            UPDATE Device device
               SET device.fcmToken = NULL,
                   device.updatedAt = :updatedAt
            WHERE device.id <> :deviceId
              AND device.fcmToken = :fcmToken
            """)
    int clearFcmTokenFromOtherDevices(
            @Param("deviceId") UUID deviceId,
            @Param("fcmToken") String fcmToken,
            @Param("updatedAt") Instant updatedAt
    );

    @Modifying
    @Query("""
            UPDATE Device device
               SET device.fcmToken = :fcmToken,
                   device.updatedAt = :updatedAt
            WHERE device.id = :deviceId
              AND device.memberId = :memberId
              AND device.sessionTokenHash = :sessionTokenHash
              AND device.sessionExpiresAt > :now
            """)
    int registerFcmTokenForCurrentSession(
            @Param("deviceId") UUID deviceId,
            @Param("memberId") UUID memberId,
            @Param("sessionTokenHash") String sessionTokenHash,
            @Param("fcmToken") String fcmToken,
            @Param("now") Instant now,
            @Param("updatedAt") Instant updatedAt
    );
}
