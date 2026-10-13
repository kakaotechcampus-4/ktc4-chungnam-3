package com.ktc.chungnam3.remembrall.domain.localdata;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(name = "business_license", uniqueConstraints = @UniqueConstraint(
        name = "uk_business_license_source_id", columnNames = {"source_id"}
))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BusinessLicense {

    @Id
    private UUID id;

    @Column(name = "source_id", nullable = false)
    private String sourceId;

    @Column(name = "business_name", nullable = false)
    private String businessName;

    @Column(name = "category")
    private String category;

    @Column(name = "license_status", nullable = false)
    private String licenseStatus;

    @Column(name = "road_address")
    private String roadAddress;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
