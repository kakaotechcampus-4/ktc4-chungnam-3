package com.ktc.chungnam3.remembrall.repository;

import com.ktc.chungnam3.remembrall.domain.device.Device;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.UUID;

public interface DeviceRepository extends JpaRepository<Device, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Device> findByRefreshTokenHash(String refreshTokenHash);

    Optional<Device> findByMemberId(UUID memberId);

    void deleteByMemberId(UUID memberId);
}
