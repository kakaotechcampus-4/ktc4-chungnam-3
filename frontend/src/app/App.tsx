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
import { useEffect, useState } from "react";
import { StyleSheet } from "react-native";
import { GestureHandlerRootView } from "react-native-gesture-handler";
import { SafeAreaProvider } from "react-native-safe-area-context";

import { ensureChannels } from "../runtime/notifications/channels";
import { startListening } from "../runtime/notifications/notificationListener";
import { registerPushTokenAfterRefresh } from "../runtime/notifications/pushTokenSync";
import {
    registerSessionRefresher,
    setSessionRefreshedHandler,
} from "../shared/auth/login";
import {
    isDemoToolsEnabled,
    registerDemoToolsSection,
} from "../shared/demo/demoTools";
import { restoreSession, useAuthState } from "../shared/auth/session";
import { endLaunch, useLaunchTarget } from "../shared/launch/launchTarget";
import { setForegroundPresentation } from "../shared/permissions/notifications";
import { colors, fontFamily, metrics } from "../shared/ui/theme";
import DemoToolsSection from "./demo/DemoToolsSection";
import LaunchSplash from "./launch/LaunchSplash";
import { buildLinkingConfig } from "./navigation/linking";
import { navigationRef } from "./navigation/navigationRef";
import RootNavigator from "./navigation/RootNavigator";
import {
    flushPendingNotification,
    hasPendingNotification,
    openFromNotification,
} from "./notifications/notificationOpener";
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
    // 폰트와 저장된 세션을 모두 읽으면 화면을 그린다. 그 전에는 실행 모션만 덮고 있어 00a 가 잠깐 비치지 않는다.
    // 폰트 로드에 실패해도 시스템 폰트로 계속 띄운다.
    const ready =
        (fontsLoaded || fontError != null) && auth.status !== "loading";

    // 실행 모션(LaunchSplash)은 ready 전부터 떠서 첫 프레임에 네이티브 스플래시를 이어받고, 준비를 기다리는 동안 훑기를 돈다.
    // 콜드 스타트에 한 번, 앞으로 돌아올 때는 없다.
    // 모션을 생략할지는 ready 가 된 첫 렌더에서 정한다. 알림 대기 칸은 NavigationContainer onReady 에서 비워지므로 그 전에 읽는다.
    // 세션이 있고 누른 알림이 있으면 08 로 바로 가므로 근처 첫 카드에 내려앉을 수 없다(로그인 전이면 00a 로 가므로 모션 그대로).
    const [launch, setLaunch] = useState<
        { skipMotion: boolean } | "done" | null
    >(null);
    if (ready && launch === null) {
        setLaunch({
            skipMotion: auth.status === "signedIn" && hasPendingNotification(),
        });
    }
    // 앞장이 펼쳐질 자리는 도착 화면이 보고한다. 보고가 오면(또는 대기 시간 뒤 대상 없음 null 로) 끝맺음을 시작한다.
    const reportedTarget = useLaunchTarget();
    const [targetTimedOut, setTargetTimedOut] = useState(false);
    const launchTarget =
        reportedTarget !== undefined
            ? reportedTarget
            : targetTimedOut
              ? null
              : undefined;
    const finishLaunch = () => {
        endLaunch();
        setLaunch("done");
    };

    useEffect(() => {
        // 게스트 세션이 만료 · 401 이면 API 클라이언트가 조용히 다시 받는다.
        // 다시 받으면 서버가 FCM 토큰을 지우므로 원래 요청을 다시 보내기 전에 새 세션으로 다시 등록한다.
        registerSessionRefresher();
        setSessionRefreshedHandler(registerPushTokenAfterRefresh);
        restoreSession();
        // 로그인 여부와 무관하게 켤 때마다. 첫 알림(토큰 등록 뒤)보다 먼저 채널 · 앞에 있을 때의 표시가 있게 한다.
        ensureChannels();
        setForegroundPresentation();
        // 알림 누름(종료 상태에서 연 알림 포함). 열기는 대기 칸에 넣고 signedIn · 네비게이터 준비 뒤에 연다.
        return startListening(openFromNotification);
    }, []);

    useEffect(() => {
        if (!ready) return;
        const timer = setTimeout(
            () => setTargetTimedOut(true),
            metrics.launch.targetWaitMs,
        );
        return () => clearTimeout(timer);
    }, [ready]);

    return (
        // 끌 수 있는 시트(@gorhom/bottom-sheet)가 제스처를 받으려면 최상위를 GestureHandlerRootView 로 감싼다.
        <GestureHandlerRootView style={styles.root}>
            {ready && launch !== null && (
                <SafeAreaProvider>
                    <StatusBar style="dark" />
                    <NavigationContainer
                        ref={navigationRef}
                        linking={linking}
                        theme={navigationTheme}
                        onReady={flushPendingNotification}
                    >
                        {/* 모달 시트(동의 시트 · 공유 받음 시트)는 화면 위 · 하단 바 위에 뜬다. */}
                        <BottomSheetModalProvider>
                            <RootNavigator />
                            <ShareReceiverHost />
                        </BottomSheetModalProvider>
                    </NavigationContainer>
                </SafeAreaProvider>
            )}
            {/* 모달 시트까지 모두 덮도록 맨 위에 둔다. 화면이 생겨도 같은 자리라 다시 마운트되지 않는다. */}
            {launch !== "done" && (
                <LaunchSplash
                    target={launchTarget}
                    skipMotion={launch?.skipMotion ?? false}
                    onFinish={finishLaunch}
                />
            )}
        </GestureHandlerRootView>
    );
}

const styles = StyleSheet.create({
    root: {
        flex: 1,
        backgroundColor: colors.bg.screen,
    },
});
