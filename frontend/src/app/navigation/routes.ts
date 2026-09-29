// 라우트 이름과 파라미터 타입 정의.
import type { NavigatorScreenParams } from "@react-navigation/native";

export const ROUTES = {
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
    [ROUTES.Map]: undefined;
    [ROUTES.Archive]: undefined;
};

// SaveResult 모달 안의 중첩 스택. Result(02~05 · 02b) → PlaceSearch(02c · 02d).
export type SaveResultStackParamList = {
    [ROUTES.Result]: { resultId: string };
    [ROUTES.PlaceSearch]: { resultId: string };
};

export type RootStackParamList = {
    [ROUTES.Main]: NavigatorScreenParams<MainTabParamList>;
    [ROUTES.Settings]: undefined;
    [ROUTES.ContentDetail]: { placeId: string };
    [ROUTES.SaveResult]: NavigatorScreenParams<SaveResultStackParamList>;
};

// features 는 app 을 import 하지 않는다. useNavigation() 을 제네릭 없이 써도 라우트가 타입 검사되게 한다.
declare global {
    namespace ReactNavigation {
        interface RootParamList extends RootStackParamList {}
    }
}
