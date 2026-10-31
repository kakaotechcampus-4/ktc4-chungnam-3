// YouTube 영상 주소 · 썸네일 주소. 순수 함수. 공유 링크 파서(runtime)와 기억 탭 · 저장 결과 · 장소 상세(features)가 같이 쓴다.

// 서버에 보내고 "원본 영상 다시 보기"로 여는 Shorts 주소. 상세의 sourceUrl 은 watch 형식이라 쓰지 않는다.
export function youtubeShortsUrl(videoId: string): string {
    return `https://www.youtube.com/shorts/${videoId}`;
}

// hqdefault(480×360)는 Shorts 면 가운데에 세로 영상, 양옆에 흐린 배경이 있다(kbWy8_UC0Z8 로 확인).
// 9:16 틀에 cover 로 넣으면 가운데 세로 영상만 남는다. 문서에 없는 세로 주소(oardefault)는 쓰지 않는다.
// 없는 영상이면 404 라 Thumb 가 자리표시를 보인다.
export function youtubeThumbnailUrl(videoId: string): string {
    return `https://i.ytimg.com/vi/${videoId}/hqdefault.jpg`;
}
