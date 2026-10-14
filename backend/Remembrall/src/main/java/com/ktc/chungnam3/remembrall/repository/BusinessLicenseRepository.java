package com.ktc.chungnam3.remembrall.repository;

import com.ktc.chungnam3.remembrall.domain.localdata.BusinessLicense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface BusinessLicenseRepository extends JpaRepository<BusinessLicense, UUID> {

    /**
     * 최초 적재(1회성 스크립트)와 매일 델타 동기화 둘 다 이 메서드 하나로 처리한다 - source_id가
     * 이미 있으면 내용만 갱신(id는 유지), 없으면 새로 만든다.
     */
    @Modifying
    @Query(value = """
            INSERT INTO business_license (
                id, source_id, business_name, category, license_status, road_address,
                latitude, longitude, created_at, updated_at
            )
            VALUES (
                :id, :sourceId, :businessName, :category, :licenseStatus, :roadAddress,
                :latitude, :longitude, :now, :now
            )
            ON CONFLICT (source_id) DO UPDATE SET
                business_name = EXCLUDED.business_name,
                category = EXCLUDED.category,
                license_status = EXCLUDED.license_status,
                road_address = EXCLUDED.road_address,
                latitude = EXCLUDED.latitude,
                longitude = EXCLUDED.longitude,
                updated_at = EXCLUDED.updated_at
            """, nativeQuery = true)
    void upsert(
            @Param("id") UUID id,
            @Param("sourceId") String sourceId,
            @Param("businessName") String businessName,
            @Param("category") String category,
            @Param("licenseStatus") String licenseStatus,
            @Param("roadAddress") String roadAddress,
            @Param("latitude") Double latitude,
            @Param("longitude") Double longitude,
            @Param("now") Instant now
    );

    /**
     * 바운딩박스(위경도 인덱스 활용)로 먼저 거르고, {@code PlaceResolver.distanceMeters()}와 같은
     * 하버사인 공식(PostgreSQL 기본 내장 함수만 사용, PostGIS 불필요)으로 정확한 반경만 남긴다.
     * 영업상태 필터는 여기서 안 한다 - 적재 스크립트가 애초에 폐업·휴업 행을 안 넣으므로(실제 API의
     * "영업중" 표기 문자열이 데이터셋마다 다를 수 있어 적재 시점 한 곳에서만 판단하는 게 안전함),
     * 이 테이블엔 영업중인 곳만 있다고 가정한다.
     */
    @Query(value = """
            SELECT * FROM business_license
            WHERE latitude BETWEEN :lat - (:radiusMeters / 111320.0)
                               AND :lat + (:radiusMeters / 111320.0)
              AND longitude BETWEEN :lon - (:radiusMeters / (111320.0 * COS(RADIANS(:lat))))
                                AND :lon + (:radiusMeters / (111320.0 * COS(RADIANS(:lat))))
              AND 6371000 * 2 * ASIN(SQRT(
                    POWER(SIN(RADIANS(latitude - :lat) / 2), 2)
                    + COS(RADIANS(:lat)) * COS(RADIANS(latitude))
                      * POWER(SIN(RADIANS(longitude - :lon) / 2), 2)
                  )) <= :radiusMeters
            """, nativeQuery = true)
    List<BusinessLicense> findWithinRadius(
            @Param("lat") double lat,
            @Param("lon") double lon,
            @Param("radiusMeters") int radiusMeters
    );
}
