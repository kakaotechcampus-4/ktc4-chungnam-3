// 라우트 이름과 파라미터 타입 정의.
import type { NavigatorScreenParams } from "@react-navigation/native";

export const ROUTES = {
    // 온보딩(로그인 전 · 권한 단계). 인증 상태에 따라 Main 쪽과 둘 중 하나만 등록된다.
    Intro: "Intro",
    Login: "Login",
    NotificationPermission: "NotificationPermission",
    LocationPermission: "LocationPermission",
    BackgroundLocation: "BackgroundLocation",
    FirstSave: "FirstSave",
    Main: "Main",
    Nearby: "Nearby",
    Map: "Map",
    Archive: "Archive",
    Settings: "Settings",
    ContentDetail: "ContentDetail",
    SaveResult: "SaveResult",
    Result: "Result",
    PlaceSearch: "PlaceSearch",
} as const;

export type MainTabParamList = {
    [ROUTES.Nearby]: undefined;
    // area: 06 링크가 넘기는 동네 이름. 지도 탭은 그 동네로 가운데를 잡은 뒤 파라미터를 비운다.
    [ROUTES.Map]: { area?: string } | undefined;
    [ROUTES.Archive]: undefined;
};

// 저장물로 여는 화면의 파라미터. 화면은 저장물 상세 API 를 읽는다.
// 개발 모드에서는 personalSaveId 가 화면 목(saveResults)의 키면 그 목을 그린다(개발용 딥링크).
export type PersonalSaveParams = { personalSaveId: string };

// SaveResult 모달 안의 중첩 스택. Result(02~05 · 02b) → PlaceSearch(02c · 02d).
// PlaceSearch 는 personalSaveId 면 저장 모드, placeId 면 08 에서 여는 바꾸기 모드다.
// sourceTitle 은 캡션에 쓰는 영상 제목이다(없으면 null). 넘기면 목에서 찾지 않는다.
export type SaveResultStackParamList = {
    [ROUTES.Result]: PersonalSaveParams;
    [ROUTES.PlaceSearch]:
        | (PersonalSaveParams & { sourceTitle?: string | null })
        | { placeId: string; sourceTitle?: string | null };
};

export type RootStackParamList = {
    [ROUTES.Intro]: undefined;
    [ROUTES.Login]: undefined;
    [ROUTES.NotificationPermission]: undefined;
    [ROUTES.LocationPermission]: undefined;
    [ROUTES.BackgroundLocation]: undefined;
    [ROUTES.FirstSave]: undefined;
    [ROUTES.Main]: NavigatorScreenParams<MainTabParamList>;
    [ROUTES.Settings]: undefined;
    // 지도 · 근처는 placeId(목 장소)로, 기억 탭은 저장물로, 알림은 notificationId 로 연다.
    // 저장물로 열 때 placeId 가 있으면 그 장소를 그린다(없거나 저장물에 없으면 첫 장소).
    [ROUTES.ContentDetail]:
        | { notificationId: string }
        | (PersonalSaveParams & { placeId?: string })
        | { placeId: string };
    [ROUTES.SaveResult]: NavigatorScreenParams<SaveResultStackParamList>;
};

// features 는 app 을 import 하지 않는다. useNavigation() 을 제네릭 없이 써도 라우트가 타입 검사되게 한다.
declare global {
    namespace ReactNavigation {
        interface RootParamList extends RootStackParamList {}
    }
}
