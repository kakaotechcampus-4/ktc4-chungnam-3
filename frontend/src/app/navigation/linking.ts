// 딥링크 URL 을 라우트로 매핑하는 linking 설정.
import type { LinkingOptions } from "@react-navigation/native";
import * as Linking from "expo-linking";

import { ROUTES, type RootStackParamList } from "./routes";

// prefix 는 scheme 을 하드코딩하지 않는다. Expo Go 에서는 exp://…/--/ 가 된다.
export function buildLinkingConfig(): LinkingOptions<RootStackParamList> {
    return {
        prefixes: [Linking.createURL("/")],
        config: {
            // 딥링크로 콜드 스타트해도 Main 이 항상 아래에 깔리게 한다. 뒤로 가기 · popTo 의 목적지다.
            initialRouteName: ROUTES.Main,
            screens: {
                [ROUTES.Main]: {
                    screens: {
                        [ROUTES.Nearby]: "nearby",
                        [ROUTES.Map]: "map",
                        [ROUTES.Archive]: "archive",
                    },
                },
                [ROUTES.Settings]: "settings",
                [ROUTES.ContentDetail]: "detail/:placeId",
                // 기존 경로는 그대로 Result 로 열린다. search 로 열어도 Result 가 아래에 깔린다.
                [ROUTES.SaveResult]: {
                    initialRouteName: ROUTES.Result,
                    screens: {
                        [ROUTES.Result]: "save-result/:resultId",
                        [ROUTES.PlaceSearch]: "save-result/:resultId/search",
                    },
                },
            },
        },
    };
}
