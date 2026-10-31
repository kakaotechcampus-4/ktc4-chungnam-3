// 기기 알림 권한(안드로이드 13+ POST_NOTIFICATIONS. 12 이하는 자동 허용). 00c · 설정(11)이 쓴다.
// 기기 FCM 토큰(Expo 푸시 토큰 아님. 서버가 firebase-admin 으로 직접 보낸다)도 여기서 받는다(runtime/notifications/pushTokenSync).
// 알림 채널 생성도 여기서 한다. 채널 정의는 runtime/notifications/channels 가 넘긴다.
// 앱이 앞에 있을 때의 표시(핸들러)와 알림 누름(응답) 구독도 여기서 한다. 응답은 data 만 넘긴다(runtime/notifications/notificationListener).
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

let tokenFailureWarned = false;

// 기기 FCM 토큰. 모듈이 없거나(Expo Go 포함) 받지 못하면 null 이다.
// google-services.json 없이 만든 빌드는 Firebase 가 초기화되지 않아 여기서 실패한다. 경고는 한 번만.
export async function getFcmToken(): Promise<string | null> {
    const module = loadNotifications();
    if (module == null) return null;
    try {
        const { data } = await module.getDevicePushTokenAsync();
        return typeof data === "string" && data !== "" ? data : null;
    } catch (error) {
        if (!tokenFailureWarned) {
            tokenFailureWarned = true;
            console.warn(
                "FCM 토큰을 받지 못했습니다. google-services.json 을 넣어 만든 빌드인지 확인하세요.",
                error,
            );
        }
        return null;
    }
}

// FCM 이 토큰을 바꾸면 새 토큰으로 부른다. 모듈이 없으면 아무것도 하지 않는다. 돌려준 함수로 구독을 푼다.
export function addFcmTokenListener(
    listener: (token: string) => void,
): () => void {
    const module = loadNotifications();
    if (module == null) return () => {};
    const subscription = module.addPushTokenListener(({ data }) => {
        if (typeof data === "string" && data !== "") listener(data);
    });
    return () => subscription.remove();
}

// "근처 알림" 채널 id. 바꾸지 않는다. app.json 의 expo-notifications 플러그인 "defaultChannel" 과 같아야 한다(app.config.ts 상단 주석).
// 채널 정의(이름 · 설명 · 중요도)는 runtime/notifications/channels 에 있다. 목 로컬 알림(shared/api)도 이 id 를 쓰므로 여기에 둔다.
export const NEARBY_CHANNEL_ID = "nearby";

// importance 는 AndroidImportance 로 바꿔 넘긴다. 필요할 때 값을 늘린다.
export type NotificationChannelSpec = {
    id: string;
    name: string;
    description: string;
    importance: "default";
};

let channelFailureWarned = false;

// 알림 채널을 만든다(있으면 이름 · 설명만 바뀐다. 중요도 · 소리 등은 처음 만들 때 정해지고 앱이 바꿀 수 없다).
// Android 가 아니거나 모듈이 없으면(Expo Go 포함) 아무것도 하지 않는다. Android 8 미만은 네이티브가 무시한다.
// 진동 · 소리 · 표시 범위는 OS 기본값을 따른다. 실패는 경고 한 번이고 예외를 던지지 않는다.
export async function ensureNotificationChannel(
    channel: NotificationChannelSpec,
): Promise<void> {
    if (Platform.OS !== "android") return;
    const module = loadNotifications();
    if (module == null) return;
    try {
        await module.setNotificationChannelAsync(channel.id, {
            name: channel.name,
            description: channel.description,
            importance: module.AndroidImportance.DEFAULT,
        });
    } catch (error) {
        if (!channelFailureWarned) {
            channelFailureWarned = true;
            console.warn(
                `알림 채널(${channel.id})을 만들지 못했습니다. 알림은 기본 채널로 올 수 있습니다.`,
                error,
            );
        }
    }
}

let localFailureWarned = false;

// 로컬 알림을 지금 띄운다(목 모드의 목 서버 알림). trigger 는 { channelId } 라 예약 없이 바로 그 채널로 표시된다.
// 같은 identifier 로 다시 띄우면 새로 쌓지 않고 바꾼다. Android 가 아니거나 모듈이 없거나(Expo Go 포함) 권한이 granted 가 아니면 띄우지 않는다.
// 실패는 경고 한 번이고 예외를 던지지 않는다.
export async function presentLocalNotification(notification: {
    identifier: string;
    title: string;
    body: string;
    data: Record<string, string>;
    channelId: string;
}): Promise<void> {
    if (Platform.OS !== "android") return;
    const module = loadNotifications();
    if (module == null) return;
    try {
        const { status } = await module.getPermissionsAsync();
        if (status !== "granted") return;
        await module.scheduleNotificationAsync({
            identifier: notification.identifier,
            content: {
                title: notification.title,
                body: notification.body,
                data: notification.data,
            },
            trigger: { channelId: notification.channelId },
        });
    } catch (error) {
        if (!localFailureWarned) {
            localFailureWarned = true;
            console.warn("로컬 알림을 띄우지 못했습니다.", error);
        }
    }
}

// 앱이 앞에 있을 때 받은 알림도 시스템 알림으로 보인다: 목록 · 소리, 배지 없음.
// 헤드업은 채널 중요도가 정한다(Android 8+ 는 banner · list 를 가르지 않고 둘 중 하나면 표시한다). 모듈이 없으면 아무것도 하지 않는다.
export function setForegroundPresentation(): void {
    const module = loadNotifications();
    if (module == null) return;
    module.setNotificationHandler({
        handleNotification: async () => ({
            shouldShowBanner: false,
            shouldShowList: true,
            shouldPlaySound: true,
            shouldSetBadge: false,
        }),
    });
}

// 알림을 누르면 그 알림의 data 로 부른다(앞 · 뒤 상태). 모듈이 없으면 아무것도 하지 않는다. 돌려준 함수로 구독을 푼다.
export function addNotificationResponseListener(
    listener: (data: unknown) => void,
): () => void {
    const module = loadNotifications();
    if (module == null) return () => {};
    const subscription = module.addNotificationResponseReceivedListener(
        (response) => listener(response.notification.request.content.data),
    );
    return () => subscription.remove();
}

// 앱을 연 마지막 알림 응답(종료 상태에서 누름)의 data. 읽고 바로 지운다. 없으면 null.
export function takeLastNotificationResponse(): unknown {
    const module = loadNotifications();
    if (module == null) return null;
    try {
        const response = module.getLastNotificationResponse();
        if (response == null) return null;
        module.clearLastNotificationResponse();
        return response.notification.request.content.data;
    } catch {
        return null;
    }
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
