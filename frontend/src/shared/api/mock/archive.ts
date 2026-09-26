// 전체 기억 탭 목 데이터. 화면 props 모양 그대로이며 서버 계약이 아니다. 문구는 Figma 07 그대로.
// 영상 제목은 videoTitle 에 따옴표 없이 둔다. 따옴표는 컴포넌트가 붙인다.
// 01 근처와 같은 장소는 같은 이미지를 쓴다.
const img = (id: number) => `https://picsum.photos/id/${id}/360/640`;

// 07 전체 기억 · 시간순 앨범 (2061:901)
export const archiveMock = {
  sections: [
    {
      title: '이번 주',
      items: [
        {
          id: 'daejeon-bread',
          videoTitle: '대전 빵집 3곳',
          thumbUri: img(431),
          fade: 'recent',
          status: 'needsConfirmation',
          resultId: 'needs-confirmation',
        },
        {
          id: 'just-saved',
          label: '방금 저장한 영상',
          thumbUri: img(225),
          fade: 'recent',
          status: 'analyzing',
          resultId: 'analyzing',
        },
        { id: 'sojedong', label: '소제동 카페거리', thumbUri: img(312), fade: 'recent' },
      ],
    },
    {
      title: '지난 몇 주',
      items: [
        { id: 'sungsimdang', label: '성심당 본점', thumbUri: img(1080), fade: 'weeks', detailId: 'sungsimdang' },
        { id: 'afternoon-four', label: '오후 네시', thumbUri: img(1060), fade: 'weeks' },
        { id: 'daeheung-cathedral', label: '대흥동 성당', thumbUri: img(488), fade: 'weeks' },
      ],
    },
    {
      title: '올해 봄',
      items: [
        { id: 'hanbat-kalguksu', label: '한밭 손칼국수', thumbUri: img(292), fade: 'months' },
        { id: 'skyroad', label: '으능정이 스카이로드', thumbUri: img(674), fade: 'months' },
        { id: 'gyejoksan', label: '계족산 황톳길', thumbUri: img(1015), fade: 'months' },
      ],
    },
  ],
} as const;
