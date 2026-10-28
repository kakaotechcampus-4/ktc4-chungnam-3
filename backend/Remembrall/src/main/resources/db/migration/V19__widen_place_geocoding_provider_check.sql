-- TourAPI(지점명 없는 일반 장소)로 확정된 좌표도 geocoding_provider로 허용한다.
ALTER TABLE place DROP CONSTRAINT ck_place_geocoding_provider;
ALTER TABLE place ADD CONSTRAINT ck_place_geocoding_provider
    CHECK (geocoding_provider IN ('LOCATIONIQ', 'DATAPORTAL', 'TOURAPI'));
