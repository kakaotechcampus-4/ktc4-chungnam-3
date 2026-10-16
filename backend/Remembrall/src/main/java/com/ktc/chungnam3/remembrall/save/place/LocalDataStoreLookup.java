package com.ktc.chungnam3.remembrall.save.place;

import com.ktc.chungnam3.remembrall.domain.localdata.BusinessLicense;
import com.ktc.chungnam3.remembrall.repository.BusinessLicenseRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 지방행정인허가 데이터(공공누리 제1유형, 좌표 영구 저장 허용)로 반경 내 업소를 조회한다.
 * 공공상가정보(`DataportalStoreClient`)에 없는 대형 직영 체인(다이소·맥도날드 등) 커버용 -
 * 외부 API 호출이 아니라 우리 DB(`BusinessLicenseRepository`, 별도 적재 스크립트/배치로 채움)를 조회한다.
 * <p>
 * 이 데이터셋은 상호명을 "브랜드+지점명"으로 분리해서 주지 않고 사업장명 하나로 통째로 주므로
 * {@code brchNm}은 항상 null로 반환한다 - 기존 호출부가 이미 null을 안전하게 처리함.
 * <p>
 * {@link CompositeNearbyStoreLookup}을 통해 공공상가정보와 합쳐져 {@code PlaceResolver}에 연결된다
 * (2026-10-09, 전국 데이터 실제 적재로 소비자용 지점명 품질을 확인한 뒤 연결 - 맥도날드·스타벅스가
 * 실측으로 확인됨).
 */
@Component
public class LocalDataStoreLookup implements NearbyStoreLookup {

    private final BusinessLicenseRepository repository;

    public LocalDataStoreLookup(BusinessLicenseRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<DataportalStoreClient.StoreResult> searchByRadius(double lon, double lat, int radiusMeters) {
        return repository.findWithinRadius(lat, lon, radiusMeters).stream()
                .map(LocalDataStoreLookup::toStoreResult)
                .toList();
    }

    private static DataportalStoreClient.StoreResult toStoreResult(BusinessLicense license) {
        return new DataportalStoreClient.StoreResult(
                license.getSourceId(),
                license.getBusinessName(),
                null,
                license.getCategory(),
                license.getRoadAddress(),
                license.getLatitude() == null ? 0 : license.getLatitude(),
                license.getLongitude() == null ? 0 : license.getLongitude()
        );
    }
}
