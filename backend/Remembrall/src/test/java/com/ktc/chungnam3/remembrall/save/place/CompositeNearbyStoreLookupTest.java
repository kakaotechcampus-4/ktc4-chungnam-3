package com.ktc.chungnam3.remembrall.save.place;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompositeNearbyStoreLookupTest {

    private static final DataportalStoreClient.StoreResult MCDONALDS = new DataportalStoreClient.StoreResult(
            "LD-1", "한국맥도날드(유) 종로3가점", null, "패스트푸드", "서울특별시 종로구 종로 115", 37.5707, 126.9903);

    /** 모든 호출에서 시간 초과처럼 예외를 던지는 공공상가정보. */
    private static final DataportalStoreClient FAILING_DATAPORTAL = new DataportalStoreClient("test-key", 1, 1) {
        @Override
        public List<StoreResult> searchByRadius(double lon, double lat, int radiusMeters) {
            throw new IllegalStateException("timeout");
        }

        @Override
        public List<StoreResult> searchByRadius(double lon, double lat, int radiusMeters, List<String> codes) {
            throw new IllegalStateException("timeout");
        }

        @Override
        public List<StoreResult> searchByRadiusBySubCategory(double lon, double lat, int radiusMeters, List<String> codes) {
            throw new IllegalStateException("timeout");
        }
    };

    private static final LocalDataStoreLookup LOCALDATA = new LocalDataStoreLookup(null) {
        @Override
        public List<DataportalStoreClient.StoreResult> searchByRadius(double lon, double lat, int radiusMeters) {
            return List.of(MCDONALDS);
        }
    };

    private final CompositeNearbyStoreLookup lookup = new CompositeNearbyStoreLookup(FAILING_DATAPORTAL, LOCALDATA);

    @Test
    void 공공상가정보가_실패해도_업종_대분류_검색은_LOCALDATA_결과를_돌려준다() {
        // PR #70 리뷰(doheelab-coder): 예전엔 공공상가정보 예외가 그대로 올라가 LOCALDATA에만 있는
        // 맥도날드 종로3가점이 외부 호출 한 번의 시간 초과로 NO_PLACE가 됐다.
        assertThat(lookup.searchByRadius(126.99, 37.57, 1000, List.of("I2", "G2"))).containsExactly(MCDONALDS);
    }

    @Test
    void 공공상가정보가_실패해도_업종_소분류_검색은_LOCALDATA_결과를_돌려준다() {
        assertThat(lookup.searchByRadiusBySubCategory(126.99, 37.57, 1000, List.of("G20405"))).containsExactly(MCDONALDS);
    }

    @Test
    void 인자_3개짜리_검색은_실패를_그대로_던진다() {
        // hasNearbyStoreMismatch가 조회 실패를 "반경 검증 생략"으로 쓰므로 삼키지 않는다.
        assertThatThrownBy(() -> lookup.searchByRadius(126.99, 37.57, 100)).isInstanceOf(IllegalStateException.class);
    }
}
