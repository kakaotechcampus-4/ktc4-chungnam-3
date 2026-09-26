// 장소 상세 목 데이터. placeId 로 찾는다. 화면 props 모양 그대로이며 서버 계약이 아니다.
// 문구는 Figma 08 그대로. 태그의 # 은 컴포넌트가 붙인다. 이미지는 01 · 07 의 성심당과 같다.
const img = (id: number) => `https://picsum.photos/id/${id}/360/640`;

export const placeDetailsMock = {
  // 08 장소 상세 · 지도 (2061:971)
  sungsimdang: {
    time: '3주 전',
    place: '성심당 본점',
    meta: '대전 중구 은행동 · 도보 4분',
    thumbUri: img(1080),
    fade: 'recent',
    note: '튀김소보로는 오전에 가면 줄이 짧다. 부추빵도 같이 사라고 함.',
    tags: ['줄서는', '오전', '빵집'],
    sourceMeta: '@daejeon.bread · YouTube Shorts',
  },
} as const;
