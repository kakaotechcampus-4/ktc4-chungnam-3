// 미확정: 백엔드 계약 없음
// 앱 모델. 장소. 전역 장소 키는 아직 백엔드에 존재하지 않는다.

export type PlaceId = string;

export type PlaceResolution = string;

export interface Place {
  id: PlaceId;
  name: string;
  resolution: PlaceResolution;
  latitude: number | null;
  longitude: number | null;
}
