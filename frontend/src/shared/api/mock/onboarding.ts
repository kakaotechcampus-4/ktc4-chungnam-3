// 온보딩(00a · 00b · 00c) 목 데이터. 화면 props 모양 그대로이며 서버 계약이 아니다.
// 일러스트 사진은 Figma 의 C/Thumb 자리를 목 이미지로 채운다. 바램은 Figma 그대로다.
// 00a 는 첫 실행 화면이라 오프라인일 수 있고 실행 모션이 맨 앞 사진 위에 내려앉으므로, 같은 picsum 사진(360×640)을 앱에 번들했다
// (assets/images/onboarding/intro-{id}.jpg). 00b · 00c 는 아직 원격 목 이미지다.
import { img } from "./img";

export const onboardingMock = {
    // 00a 2175:683. 뒤에서 앞 순서.
    introPhotos: [
        {
            image: require("../../../../assets/images/onboarding/intro-292.jpg"),
            fade: "months",
        },
        {
            image: require("../../../../assets/images/onboarding/intro-1036.jpg"),
            fade: "months",
        },
        {
            image: require("../../../../assets/images/onboarding/intro-1060.jpg"),
            fade: "weeks",
        },
        {
            image: require("../../../../assets/images/onboarding/intro-1080.jpg"),
            fade: "recent",
        },
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
