// 카카오맵 도보 길찾기를 연다. 08 "길 안내 시작"이 부른다. URL 은 mapLinks 가 만든다.
// 앱 스킴을 먼저 열고, 실패하면(앱 없음 · 꺼짐) 모바일웹 스킴을 연다. 둘 다 실패하면 경고만 남긴다.
// canOpenURL 은 쓰지 않는다(Android 11+ 에서 <queries> 선언 없이는 false 라 네이티브 설정이 필요해진다).
// 인앱 브라우저(openExternalUrl)는 앱 스킴을 처리하지 못해 쓰지 않는다. 누름 처리에서 부르므로 예외를 던지지 않는다.
import { Linking } from "react-native";

import type { LatLng } from "../geo/distanceMeters";
import { kakaoWalkingRouteUrls } from "./mapLinks";

export async function openWalkingRoute(
    destination: LatLng,
    origin?: LatLng,
): Promise<void> {
    try {
        const { app, web } = kakaoWalkingRouteUrls(destination, origin);
        try {
            await Linking.openURL(app);
            return;
        } catch {
            // 아래에서 모바일웹으로 연다.
        }
        await Linking.openURL(web);
    } catch {
        console.warn("길찾기를 열지 못했습니다.");
    }
}
