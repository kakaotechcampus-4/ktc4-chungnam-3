// 기기 알림 권한(안드로이드 13+ POST_NOTIFICATIONS. 12 이하는 자동 허용). 00c · 설정(11)이 쓴다.
// expo-notifications 는 처음 쓸 때 불러온다. 이 모듈이 없는 개발 빌드에서 파일 맨 위에서 import 하면
// import 만으로 앱이 멈춘다. 모듈이 없으면 "unavailable" 을 돌려주고 경고한다.
// 안드로이드 Expo Go(SDK 53+)에서는 불러오지 않는다. expo-notifications 는 불러오는 순간 푸시 토큰 리스너를 등록하는데,
// 그 안의 warnOfExpoGoPushUsage() 가 isRunningInExpoGo() 일 때 예외를 던진다. 같은 판별로 미리 피한다.
import { isRunningInExpoGo } from "expo";
import Constants from "expo-constants";
import { Linking, Platform } from "react-native";

type NotificationsModule = typeof import("expo-notifications");
let notifications: NotificationsModule | null | undefined;

function loadNotifications(): NotificationsModule | null {
    if (notifications === undefined && isRunningInExpoGo()) {
        notifications = null;
        console.warn(
            "Expo Go 에서는 알림 권한을 확인하지 않습니다(SDK 53+ 안드로이드 Expo Go 에서 expo-notifications 가 빠졌다). 다시 만든 개발 빌드에서 확인하세요.",
        );
    }
    if (notifications === undefined) {
        try {
            notifications =
                require("expo-notifications") as NotificationsModule;
        } catch {
            notifications = null;
            console.warn(
                "expo-notifications 네이티브 모듈이 없는 빌드입니다. 알림 권한을 확인하지 않습니다. 개발 빌드를 다시 만드세요.",
            );
        }
    }
    return notifications;
}

export type NotificationPermission =
    | "granted"
    | "denied"
    | "undetermined"
    | "unavailable";

export async function getNotificationPermission(): Promise<NotificationPermission> {
    const module = loadNotifications();
    if (module == null) return "unavailable";
    const { status } = await module.getPermissionsAsync();
    return status;
}

export async function requestNotificationPermission(): Promise<NotificationPermission> {
    const module = loadNotifications();
    if (module == null) return "unavailable";
    const { status } = await module.requestPermissionsAsync();
    return status;
}

// 이 앱의 시스템 알림 설정 화면을 연다. 열지 못하면(Expo Go 등) 앱 정보 화면을 연다.
export async function openNotificationSettings(): Promise<void> {
    const packageName = Constants.expoConfig?.android?.package;
    if (
        Platform.OS === "android" &&
        packageName != null &&
        !isRunningInExpoGo()
    ) {
        try {
            await Linking.sendIntent(
                "android.settings.APP_NOTIFICATION_SETTINGS",
                [
                    {
                        key: "android.provider.extra.APP_PACKAGE",
                        value: packageName,
                    },
                ],
            );
            return;
        } catch {
            // 아래 앱 정보 화면으로 대신 연다.
        }
    }
    await Linking.openSettings();
}
