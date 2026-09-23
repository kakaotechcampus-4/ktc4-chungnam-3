// 근처 탭 목 데이터. 화면 props 모양 그대로이며 서버 계약이 아니다. 문구는 Figma 01 · 06 그대로.
const img = (id: number) => `https://picsum.photos/id/${id}/360/640`;

// 01 근처 · 다시 꺼낸 곳 (2061:760)
export const nearbyMock = {
  area: '대흥동',
  nearby: [
    {
      id: 'sungsimdang',
      detailId: 'sungsimdang',
      now: '지금 도보 4분',
      time: '3주 전',
      place: '성심당 본점',
      summary: '튀김소보로는 오전에 가면 줄이 짧다는 영상',
      tags: ['줄서는', '오전'],
      thumbUri: img(1080),
      fade: 'recent',
    },
  ],
  memoryGroups: [
    [
      {
        id: 'afternoon-four',
        time: '2주 전',
        place: '오후 네시',
        meta: '도보 9분 · 카페',
        summary: '창가 자리에서 골목이 내려다보인다고 저장한 영상',
        tags: ['조용한', '창가'],
        thumbUri: img(1060),
        fade: 'weeks',
      },
      {
        id: 'daeheung-cathedral',
        time: '6주 전',
        place: '대흥동 성당',
        meta: '도보 11분 · 가볼 곳',
        summary: '붉은 벽돌 성당, 해 질 무렵 사진이 잘 나온다는 영상',
        tags: ['해질녘', '사진'],
        thumbUri: img(488),
        fade: 'weeks',
      },
    ],
    [
      {
        id: 'hanbat-kalguksu',
        time: '5개월 전',
        place: '한밭 손칼국수',
        meta: '도보 13분 · 식당',
        summary: '국물이 진하고 만두는 따로 주문해야 한다는 영상',
        tags: ['노포', '점심'],
        thumbUri: img(292),
        fade: 'months',
      },
    ],
  ],
} as const;

// 06 근처 · 빈 상태 (2061:881)
export const nearbyEmptyMock = {
  area: '유성구 봉명동',
  nearestDistance: '2.4km',
  nearestArea: '대흥동',
  stackThumbUris: [img(488), img(292), img(1060)],
} as const;
