// 거리 → 화면 문구. 순수 함수. 지도 요약(10b) · 장소 상세(08) · 근처 탭이 같이 쓴다.
// 도보 30분 이내는 "도보 N분", 넘으면 직선거리 "N.Nkm".
// 도보 분은 서버 꺼내기 도구(WalkingTimeCalculator)와 같은 값으로 어림한다: 직선거리 × 우회 계수 1.3 ÷ 분속 80m.
// 서버는 소수 그대로 쓰고, 화면은 반올림해 최소 1분으로 보인다.

const WALKING_METERS_PER_MINUTE = 80;
const WALKING_DETOUR_FACTOR = 1.3;
const MAX_WALK_MINUTES = 30;
const METERS_PER_KM = 1000;

export function distanceLabel(meters: number): string {
    const minutes = Math.max(
        1,
        Math.round(
            (meters * WALKING_DETOUR_FACTOR) / WALKING_METERS_PER_MINUTE,
        ),
    );
    if (minutes <= MAX_WALK_MINUTES) return `도보 ${minutes}분`;
    return `${(meters / METERS_PER_KM).toFixed(1)}km`;
}
