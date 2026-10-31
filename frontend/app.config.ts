import type { ConfigContext, ExpoConfig } from "expo/config";

// app.json 은 JSON 이라 주석을 달 수 없어 여기에 적는다.
// expo-notifications 플러그인의 "defaultChannel"(nearby)은 src/runtime/notifications/channels.ts 의 NEARBY_CHANNEL_ID 와 같아야 한다.

// 설정 평가는 Node 에서 돈다. @types/node 가 프로젝트 타입에 없어 쓰는 함수에만 타입을 붙인다.
const { existsSync } = require("node:fs") as {
    existsSync(path: string): boolean;
};
const path = require("node:path") as {
    resolve(...paths: string[]): string;
};

// 빌드 시점에만 읽는다. 번들에 들어가지 않도록 EXPO_PUBLIC_ 접두어를 쓰지 않는다.
const googleMapsAndroidApiKey = process.env.GOOGLE_MAPS_ANDROID_API_KEY;
// EAS file 환경 변수. 빌드 서버에 내려받은 google-services.json 의 경로다.
const googleServicesJson = process.env.GOOGLE_SERVICES_JSON;
// 로컬 빌드용. 공개 레포라 gitignore 돼 있다.
const LOCAL_GOOGLE_SERVICES_FILE = "./google-services.json";

// 환경 변수 → 로컬 파일 순서. 환경 변수를 넣었는데 파일이 없으면 FCM 없는 빌드가 조용히 나오지 않게 실패한다.
// 둘 다 없으면 설정하지 않는다(경고만, FCM 토큰을 받지 못한다).
function resolveGoogleServicesFile(projectRoot: string): string | undefined {
    if (googleServicesJson) {
        if (!existsSync(path.resolve(projectRoot, googleServicesJson))) {
            throw new Error(
                `GOOGLE_SERVICES_JSON 경로에 파일이 없습니다: ${googleServicesJson}`,
            );
        }
        return googleServicesJson;
    }
    if (existsSync(path.resolve(projectRoot, LOCAL_GOOGLE_SERVICES_FILE))) {
        return LOCAL_GOOGLE_SERVICES_FILE;
    }
    console.warn(
        "google-services.json 이 없습니다. 이 빌드는 FCM 토큰을 받지 못해 푸시 알림이 오지 않습니다.",
    );
    return undefined;
}

export default ({ config, projectRoot }: ConfigContext): ExpoConfig => {
    if (!googleMapsAndroidApiKey) {
        console.warn(
            "GOOGLE_MAPS_ANDROID_API_KEY 가 없습니다. 개발 빌드의 지도가 표시되지 않습니다.",
        );
    }
    const googleServicesFile = resolveGoogleServicesFile(projectRoot);

    return {
        ...(config as ExpoConfig),
        android: {
            ...config.android,
            ...(googleServicesFile != null && { googleServicesFile }),
        },
        plugins: [
            ...(config.plugins ?? []),
            [
                "react-native-maps",
                { androidGoogleMapsApiKey: googleMapsAndroidApiKey },
            ],
        ],
    };
};
