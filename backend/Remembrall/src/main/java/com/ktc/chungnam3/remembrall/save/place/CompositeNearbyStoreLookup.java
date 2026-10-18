package com.ktc.chungnam3.remembrall.save.place;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 공공상가정보({@link DataportalStoreClient})와 지방행정인허가({@link LocalDataStoreLookup}) 두
 * 데이터소스를 합쳐서 반경검색한다(2026-10-09, 맥도날드·스타벅스가 공공상가정보엔 없고 지방행정인허가엔
 * 있다는 걸 실제 적재로 확인한 뒤 연결). 업종(대/소분류) 필터는 공공상가정보 쪽만 걸 수 있어서
 * 그대로 위임하고, 지방행정인허가 쪽은 필터 없이(이 데이터셋엔 그 코드 체계가 없음) 항상 전체를 더한다.
 */
@Primary
@Component
public class CompositeNearbyStoreLookup implements NearbyStoreLookup {

    private final DataportalStoreClient dataportalStoreClient;
    private final LocalDataStoreLookup localDataStoreLookup;

    public CompositeNearbyStoreLookup(
            DataportalStoreClient dataportalStoreClient, LocalDataStoreLookup localDataStoreLookup) {
        this.dataportalStoreClient = dataportalStoreClient;
        this.localDataStoreLookup = localDataStoreLookup;
    }

    @Override
    public List<DataportalStoreClient.StoreResult> searchByRadius(double lon, double lat, int radiusMeters) {
        return merge(
                dataportalStoreClient.searchByRadius(lon, lat, radiusMeters),
                localDataStoreLookup.searchByRadius(lon, lat, radiusMeters));
    }

    @Override
    public List<DataportalStoreClient.StoreResult> searchByRadius(
            double lon, double lat, int radiusMeters, List<String> industryLargeCategoryCodes) {
        return merge(
                dataportalStoreClient.searchByRadius(lon, lat, radiusMeters, industryLargeCategoryCodes),
                localDataStoreLookup.searchByRadius(lon, lat, radiusMeters));
    }

    @Override
    public List<DataportalStoreClient.StoreResult> searchByRadiusBySubCategory(
            double lon, double lat, int radiusMeters, List<String> industrySubCategoryCodes) {
        return merge(
                dataportalStoreClient.searchByRadiusBySubCategory(lon, lat, radiusMeters, industrySubCategoryCodes),
                localDataStoreLookup.searchByRadius(lon, lat, radiusMeters));
    }

    private List<DataportalStoreClient.StoreResult> merge(
            List<DataportalStoreClient.StoreResult> a, List<DataportalStoreClient.StoreResult> b) {
        List<DataportalStoreClient.StoreResult> merged = new ArrayList<>(a);
        merged.addAll(b);
        return merged;
    }
}
