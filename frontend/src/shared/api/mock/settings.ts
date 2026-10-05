// 설정 목 데이터. 화면 props 모양 그대로이며 서버 계약이 아니다. 문구는 Figma 11 그대로.
// 실제 값은 runtime 작업 때 저장소 · 서버 설정에서 읽는다.
export const settingsMock = {
    nearbyAlerts: true,
    quietHours: "밤 10시 – 아침 8시",
} as const;
