import type { ConfigContext, ExpoConfig } from "expo/config";

// 빌드 시점에만 읽는다. 번들에 들어가지 않도록 EXPO_PUBLIC_ 접두어를 쓰지 않는다.
const googleMapsAndroidApiKey = process.env.GOOGLE_MAPS_ANDROID_API_KEY;

export default ({ config }: ConfigContext): ExpoConfig => {
    if (!googleMapsAndroidApiKey) {
        console.warn(
            "GOOGLE_MAPS_ANDROID_API_KEY 가 없습니다. 개발 빌드의 지도가 표시되지 않습니다.",
        );
    }

    return {
        ...(config as ExpoConfig),
        plugins: [
            ...(config.plugins ?? []),
            [
                "react-native-maps",
                { androidGoogleMapsApiKey: googleMapsAndroidApiKey },
            ],
        ],
    };
};
