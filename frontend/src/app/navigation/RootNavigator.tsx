// 최상위 네비게이터. 화면 등록만 담당.
import { type BottomTabBarProps, createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import ArchiveScreen from '../../features/archive/ArchiveScreen';
import ContentDetailScreen from '../../features/content-detail/ContentDetailScreen';
import ProposalScreen from '../../features/proposal/ProposalScreen';
import SaveResultScreen from '../../features/save-result/SaveResultScreen';
import { spacing } from '../../shared/ui/theme';
import TopTabs, { type TopTabKey } from '../../shared/ui/TopTabs';
import { type MainTabParamList, ROUTES, type RootStackParamList } from './routes';

const Stack = createNativeStackNavigator<RootStackParamList>();
const Tab = createBottomTabNavigator<MainTabParamList>();

const TAB_ROUTE: Record<TopTabKey, keyof MainTabParamList> = {
  nearby: ROUTES.Nearby,
  archive: ROUTES.Archive,
};

// 기본 탭바 대신 TopTabs 를 그린다. 여백은 Figma Header 프레임(pt 8 · px 20) 기준.
function TopTabBar({ state, navigation }: BottomTabBarProps) {
  const insets = useSafeAreaInsets();
  const activeRoute = state.routes[state.index];
  const active: TopTabKey = activeRoute.name === ROUTES.Archive ? 'archive' : 'nearby';

  const handleChange = (key: TopTabKey) => {
    const target = state.routes.find((route) => route.name === TAB_ROUTE[key]);
    if (!target) return;
    const event = navigation.emit({ type: 'tabPress', target: target.key, canPreventDefault: true });
    if (target.key !== activeRoute.key && !event.defaultPrevented) {
      navigation.navigate(target.name);
    }
  };

  return (
    <View style={[styles.header, { paddingTop: insets.top + spacing.sm }]}>
      <TopTabs active={active} onChange={handleChange} />
    </View>
  );
}

function MainTabs() {
  return (
    <Tab.Navigator
      initialRouteName={ROUTES.Nearby}
      tabBar={(props) => <TopTabBar {...props} />}
      screenOptions={{ headerShown: false, tabBarPosition: 'top' }}
    >
      <Tab.Screen name={ROUTES.Nearby} component={ProposalScreen} />
      <Tab.Screen name={ROUTES.Archive} component={ArchiveScreen} />
    </Tab.Navigator>
  );
}

export default function RootNavigator() {
  return (
    <Stack.Navigator screenOptions={{ headerShown: false }}>
      <Stack.Screen name={ROUTES.Main} component={MainTabs} />
      <Stack.Screen name={ROUTES.ContentDetail} component={ContentDetailScreen} />
      <Stack.Screen
        name={ROUTES.SaveResult}
        component={SaveResultScreen}
        options={{ presentation: 'modal', animation: 'slide_from_bottom' }}
      />
    </Stack.Navigator>
  );
}

const styles = StyleSheet.create({
  header: {
    paddingHorizontal: spacing.lg,
  },
});
