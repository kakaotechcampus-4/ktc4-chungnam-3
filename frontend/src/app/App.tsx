// 앱 셸. 프로바이더 구성과 RootNavigator 마운트.
import { GowunDodum_400Regular } from "@expo-google-fonts/gowun-dodum/400Regular";
import { BottomSheetModalProvider } from "@gorhom/bottom-sheet";
import {
    DefaultTheme,
    NavigationContainer,
    type Theme,
} from "@react-navigation/native";
import { useFonts } from "expo-font";
import * as SplashScreen from "expo-splash-screen";
import { StatusBar } from "expo-status-bar";
import { useEffect } from "react";
import { StyleSheet } from "react-native";
import { GestureHandlerRootView } from "react-native-gesture-handler";
import { SafeAreaProvider } from "react-native-safe-area-context";

import { ensureChannels } from "../runtime/notifications/channels";
import { registerSessionRefresher } from "../shared/auth/login";
import {
    isDemoToolsEnabled,
    registerDemoToolsSection,
} from "../shared/demo/demoTools";
import { restoreSession, useAuthState } from "../shared/auth/session";
import { colors, fontFamily } from "../shared/ui/theme";
import DemoToolsSection from "./demo/DemoToolsSection";
import { buildLinkingConfig } from "./navigation/linking";
import { navigationRef } from "./navigation/navigationRef";
import RootNavigator from "./navigation/RootNavigator";
import ShareReceiverHost from "./share/ShareReceiverHost";

SplashScreen.preventAutoHideAsync();

// 설정의 시연 도구 묶음. 개발 모드 · preview 빌드(EXPO_PUBLIC_DEMO_TOOLS=1)에서만 등록한다.
if (isDemoToolsEnabled) registerDemoToolsSection(DemoToolsSection);

const linking = buildLinkingConfig();

// 화면 전환 시 흰 배경이 비치지 않게 배경을 bg/screen 으로 맞춘다.
const navigationTheme: Theme = {
    ...DefaultTheme,
    colors: {
        ...DefaultTheme.colors,
        background: colors.bg.screen,
        card: colors.bg.screen,
    },
};

export default function App() {
    const [fontsLoaded, fontError] = useFonts({
        [fontFamily.gowunDodumRegular]: GowunDodum_400Regular,
        // 한자를 뺀 서브셋. scripts/subset-fonts.py 로 만든다.
        [fontFamily.notoSansKrRegular]: require("../../assets/fonts/NotoSansKR-Regular-subset.ttf"),
        [fontFamily.notoSansKrMedium]: require("../../assets/fonts/NotoSansKR-Medium-subset.ttf"),
    });
    const auth = useAuthState();
    // 폰트와 저장된 세션을 모두 읽을 때까지 스플래시를 유지한다. 00a 가 잠깐 비치지 않게 한다.
    const ready =
        (fontsLoaded || fontError != null) && auth.status !== "loading";

    useEffect(() => {
        // 게스트 세션이 만료 · 401 이면 API 클라이언트가 조용히 다시 받는다.
        registerSessionRefresher();
        restoreSession();
        // 로그인 여부와 무관하게 켤 때마다. 첫 알림(토큰 등록 뒤)보다 먼저 채널이 있게 한다.
        ensureChannels();
    }, []);

    // 폰트 로드에 실패해도 시스템 폰트로 계속 띄운다.
    useEffect(() => {
        if (ready) SplashScreen.hideAsync();
    }, [ready]);

    if (!ready) return null;

    return (
        // 끌 수 있는 시트(@gorhom/bottom-sheet)가 제스처를 받으려면 최상위를 GestureHandlerRootView 로 감싼다.
        <GestureHandlerRootView style={styles.root}>
            <SafeAreaProvider>
                <StatusBar style="dark" />
                <NavigationContainer
                    ref={navigationRef}
                    linking={linking}
                    theme={navigationTheme}
                >
                    {/* 모달 시트(동의 시트 · 공유 받음 시트)는 화면 위 · 하단 바 위에 뜬다. */}
                    <BottomSheetModalProvider>
                        <RootNavigator />
                        <ShareReceiverHost />
                    </BottomSheetModalProvider>
                </NavigationContainer>
            </SafeAreaProvider>
        </GestureHandlerRootView>
    );
}

const styles = StyleSheet.create({
    root: {
        flex: 1,
        backgroundColor: colors.bg.screen,
    },
});
