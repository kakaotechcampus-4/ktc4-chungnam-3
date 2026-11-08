// 두 좌표 사이 거리(m). 순수 함수. 서버 GeoDistance.meters 와 같은 haversine(지구 반지름 6371000m)이다.
// 지오펜스 목의 정렬 · 재조회 반경과 "이미 반경 안인지" 판단이 같이 쓴다.

export type LatLng = { latitude: number; longitude: number };

const EARTH_RADIUS_METERS = 6_371_000;

export function distanceMeters(from: LatLng, to: LatLng): number {
    const toRadians = (degrees: number) => (degrees * Math.PI) / 180;
    const latitudeDelta = toRadians(to.latitude - from.latitude);
    const longitudeDelta = toRadians(to.longitude - from.longitude);
    const haversine =
        Math.sin(latitudeDelta / 2) ** 2 +
        Math.cos(toRadians(from.latitude)) *
            Math.cos(toRadians(to.latitude)) *
            Math.sin(longitudeDelta / 2) ** 2;
    return (
        EARTH_RADIUS_METERS *
        2 *
        Math.asin(Math.sqrt(Math.min(1, Math.max(0, haversine))))
    );
}
