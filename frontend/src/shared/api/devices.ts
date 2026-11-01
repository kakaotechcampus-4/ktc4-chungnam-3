// 기기 API. PUT /api/devices/fcm-token(지금 세션의 기기에 FCM 토큰 등록, 본문 없는 204).
// 서버는 지금 세션의 기기에만 저장하고, 같은 토큰을 가진 다른 기기에서는 지운다.
// 새 세션 발급(로그인 · 게스트 세션 다시 받기) · 로그아웃 때 서버가 토큰을 지우므로 세션이 바뀌면 다시 보내야 한다.
// 실패는 클라이언트의 ApiError 를 그대로 던진다(400 INVALID_REQUEST · 401 INVALID_SESSION · 409 FCM_TOKEN_CONFLICT).
// 목 모드(API 주소 없음)에서는 서버를 부르지 않고 성공한다.
import { request } from "./client";
import { isApiConfigured } from "./config";

export async function registerFcmToken(fcmToken: string): Promise<void> {
    if (!isApiConfigured) return;
    await request<void>("/api/devices/fcm-token", {
        method: "PUT",
        body: { fcmToken },
    });
}
