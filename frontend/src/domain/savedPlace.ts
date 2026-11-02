// 앱 모델. 내가 저장한 장소 하나와 그 장소를 가진 가장 최근 저장물. 지도 탭 · 근처 탭이 쓴다.
// 서버 내 장소 목록(장소 × 저장물 쌍)과 저장물 목록을 shared/api/mappers/savedPlaceMapper 가 묶어 만든다.
// id 는 UUID 문자열, savedAt 은 ISO-8601 UTC.

export type SavedPlace = {
    placeId: string;
    name: string;
    address: string | null;
    latitude: number;
    longitude: number;
    personalSaveId: string;
    videoId: string;
    title: string | null;
    savedAt: string;
};
