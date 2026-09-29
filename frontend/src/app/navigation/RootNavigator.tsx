// 최상위 네비게이터. 화면 등록만 담당.
import {
    type BottomTabBarProps,
    createBottomTabNavigator,
} from "@react-navigation/bottom-tabs";
import { createNativeStackNavigator } from "@react-navigation/native-stack";

import ArchiveScreen from "../../features/archive/ArchiveScreen";
import ContentDetailScreen from "../../features/content-detail/ContentDetailScreen";
import MapViewScreen from "../../features/map-view/MapViewScreen";
import ProposalScreen from "../../features/proposal/ProposalScreen";
import SaveResultScreen from "../../features/save-result/SaveResultScreen";
import SettingsScreen from "../../features/settings/SettingsScreen";
import BottomNav, { type BottomNavKey } from "../../shared/ui/BottomNav";
import {
    type MainTabParamList,
    ROUTES,
    type RootStackParamList,
} from "./routes";

const Stack = createNativeStackNavigator<RootStackParamList>();
const Tab = createBottomTabNavigator<MainTabParamList>();

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
                component={SaveResultScreen}
                options={{
                    presentation: "modal",
                    animation: "slide_from_bottom",
                }}
            />
        </Stack.Navigator>
    );
}
