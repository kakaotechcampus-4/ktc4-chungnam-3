// 최상위 네비게이터. 화면 등록만 담당.
// 인증 상태에 따라 등록하는 화면이 다르다. 상태가 바뀌면 그 묶음의 첫 화면으로 간다.
// signedOut: 00a(처음) · 00b, 로그아웃 · 401 이면 00b 만. onboarding: 권한 단계 00c~00f. signedIn: Main 쪽.
import {
    type BottomTabBarProps,
    createBottomTabNavigator,
} from "@react-navigation/bottom-tabs";
import { createNativeStackNavigator } from "@react-navigation/native-stack";

import ArchiveScreen from "../../features/archive/ArchiveScreen";
import ContentDetailScreen from "../../features/content-detail/ContentDetailScreen";
import MapViewScreen from "../../features/map-view/MapViewScreen";
import BackgroundLocationScreen from "../../features/onboarding/BackgroundLocationScreen";
import FirstSaveScreen from "../../features/onboarding/FirstSaveScreen";
import IntroScreen from "../../features/onboarding/IntroScreen";
import LocationPermissionScreen from "../../features/onboarding/LocationPermissionScreen";
import LoginScreen from "../../features/onboarding/LoginScreen";
import NotificationPermissionScreen from "../../features/onboarding/NotificationPermissionScreen";
import ProposalScreen from "../../features/proposal/ProposalScreen";
import PlaceSearchScreen from "../../features/save-result/PlaceSearchScreen";
import SaveResultScreen from "../../features/save-result/SaveResultScreen";
import SettingsScreen from "../../features/settings/SettingsScreen";
import { useAuthState } from "../../shared/auth/session";
import BottomNav, { type BottomNavKey } from "../../shared/ui/BottomNav";
import {
    type MainTabParamList,
    ROUTES,
    type RootStackParamList,
    type SaveResultStackParamList,
} from "./routes";

const Stack = createNativeStackNavigator<RootStackParamList>();
const Tab = createBottomTabNavigator<MainTabParamList>();
const SaveResultStack = createNativeStackNavigator<SaveResultStackParamList>();

// 저장 결과 모달 안의 중첩 스택. 뒤로 가기는 02c → 02, 첫 화면에서 닫으면 모달이 닫힌다.
function SaveResultFlow() {
    return (
        <SaveResultStack.Navigator
            initialRouteName={ROUTES.Result}
            screenOptions={{ headerShown: false }}
        >
            <SaveResultStack.Screen
                name={ROUTES.Result}
                component={SaveResultScreen}
            />
            <SaveResultStack.Screen
                name={ROUTES.PlaceSearch}
                component={PlaceSearchScreen}
            />
        </SaveResultStack.Navigator>
    );
}

const TAB_ROUTE: Record<BottomNavKey, keyof MainTabParamList> = {
    nearby: ROUTES.Nearby,
    map: ROUTES.Map,
    archive: ROUTES.Archive,
};

// 기본 탭바 대신 BottomNav 를 그린다.
function BottomTabBar({ state, navigation }: BottomTabBarProps) {
    const activeRoute = state.routes[state.index];
    const active = (Object.keys(TAB_ROUTE) as BottomNavKey[]).find(
        (key) => TAB_ROUTE[key] === activeRoute.name,
    );

    const handleChange = (key: BottomNavKey) => {
        const target = state.routes.find(
            (route) => route.name === TAB_ROUTE[key],
        );
        if (!target) return;
        const event = navigation.emit({
            type: "tabPress",
            target: target.key,
            canPreventDefault: true,
        });
        if (target.key !== activeRoute.key && !event.defaultPrevented) {
            navigation.navigate(target.name);
        }
    };

    return <BottomNav active={active ?? "nearby"} onChange={handleChange} />;
}

// bottom-tabs 에는 탭 스와이프가 없다. 지도 팬 제스처와 충돌하지 않는다.
function MainTabs() {
    return (
        <Tab.Navigator
            initialRouteName={ROUTES.Nearby}
            tabBar={(props) => <BottomTabBar {...props} />}
            screenOptions={{
                headerShown: false,
                tabBarPosition: "bottom",
                lazy: true,
                animation: "none",
            }}
        >
            <Tab.Screen name={ROUTES.Nearby} component={ProposalScreen} />
            <Tab.Screen name={ROUTES.Map} component={MapViewScreen} />
            <Tab.Screen name={ROUTES.Archive} component={ArchiveScreen} />
        </Tab.Navigator>
    );
}

export default function RootNavigator() {
    const auth = useAuthState();

    if (auth.status === "signedOut" || auth.status === "loading") {
        const fromIntro = auth.status === "loading" || auth.startAt === "intro";
        return (
            <Stack.Navigator screenOptions={{ headerShown: false }}>
                {fromIntro && (
                    <Stack.Screen name={ROUTES.Intro} component={IntroScreen} />
                )}
                <Stack.Screen name={ROUTES.Login} component={LoginScreen} />
            </Stack.Navigator>
        );
    }

    // 권한 단계는 화면을 교체하며 넘어간다. 시스템 뒤로 가기는 앱을 닫는다.
    if (auth.status === "onboarding") {
        return (
            <Stack.Navigator screenOptions={{ headerShown: false }}>
                <Stack.Screen
                    name={ROUTES.NotificationPermission}
                    component={NotificationPermissionScreen}
                />
                <Stack.Screen
                    name={ROUTES.LocationPermission}
                    component={LocationPermissionScreen}
                />
                <Stack.Screen
                    name={ROUTES.BackgroundLocation}
                    component={BackgroundLocationScreen}
                />
                <Stack.Screen
                    name={ROUTES.FirstSave}
                    component={FirstSaveScreen}
                />
            </Stack.Navigator>
        );
    }

    return (
        <Stack.Navigator screenOptions={{ headerShown: false }}>
            <Stack.Screen name={ROUTES.Main} component={MainTabs} />
            <Stack.Screen name={ROUTES.Settings} component={SettingsScreen} />
            <Stack.Screen
                name={ROUTES.ContentDetail}
                component={ContentDetailScreen}
            />
            <Stack.Screen
                name={ROUTES.SaveResult}
                component={SaveResultFlow}
                options={{
                    presentation: "modal",
                    animation: "slide_from_bottom",
                }}
            />
        </Stack.Navigator>
    );
}
