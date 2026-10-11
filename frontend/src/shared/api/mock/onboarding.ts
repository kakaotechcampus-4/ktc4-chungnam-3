// 온보딩(00a · 00b · 00c) 목 데이터. 화면 props 모양 그대로이며 서버 계약이 아니다.
// 일러스트 사진은 Figma 의 C/Thumb 자리를 목 이미지로 채운다. 바램은 Figma 그대로다.
// 첫 실행은 오프라인일 수 있어, 나중에 앱에 번들한 이미지로 바꾼다(다음 커밋 후보).
import { img } from "./img";

export const onboardingMock = {
    // 00a 2175:683. 뒤에서 앞 순서.
    introPhotos: [
        { uri: img(292), fade: "months" },
        { uri: img(1036), fade: "months" },
        { uri: img(1060), fade: "weeks" },
        { uri: img(1080), fade: "recent" },
    ],
    // 00b 2175:1034. 뒤에서 앞 순서.
    loginPhotos: [
        { uri: img(674), fade: "months" },
        { uri: img(488), fade: "weeks" },
        { uri: img(1080), fade: "recent" },
    ],
    // 00c 알림 미리보기 2175:772. 01 의 성심당과 같은 저장물이다.
    notificationPreview: {
        title: "3주 전에 저장한 빵집이 도보 4분 거리에 있어요",
        imageUri: img(1080),
    },
} as const;
