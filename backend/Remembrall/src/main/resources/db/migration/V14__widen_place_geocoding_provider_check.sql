-- Place.md 담당자 승인(2026-10-10) - LocationIQ(해외 API) 커버리지 부족을 보완하기 위해
-- 공공상가정보(DATAPORTAL)로 확정된 좌표도 geocoding_provider로 허용한다.
ALTER TABLE place DROP CONSTRAINT ck_place_geocoding_provider;
ALTER TABLE place ADD CONSTRAINT ck_place_geocoding_provider
    CHECK (geocoding_provider IN ('LOCATIONIQ', 'DATAPORTAL'));
