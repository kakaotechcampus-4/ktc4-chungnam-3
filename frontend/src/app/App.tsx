// 앱 셸. 프로바이더 구성과 RootNavigator 마운트.
import { GowunDodum_400Regular } from '@expo-google-fonts/gowun-dodum/400Regular';
import { DefaultTheme, NavigationContainer, type Theme } from '@react-navigation/native';
import { useFonts } from 'expo-font';
import * as SplashScreen from 'expo-splash-screen';
import { StatusBar } from 'expo-status-bar';
import { useEffect } from 'react';
import { StyleSheet, View } from 'react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import { colors, fontFamily } from '../shared/ui/theme';
import { buildLinkingConfig } from './navigation/linking';
import RootNavigator from './navigation/RootNavigator';

SplashScreen.preventAutoHideAsync();

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
    [fontFamily.notoSansKrRegular]: require('../../assets/fonts/NotoSansKR-Regular-subset.ttf'),
    [fontFamily.notoSansKrMedium]: require('../../assets/fonts/NotoSansKR-Medium-subset.ttf'),
  });
  const ready = fontsLoaded || fontError != null;

  // 폰트 로드에 실패해도 시스템 폰트로 계속 띄운다.
  useEffect(() => {
    if (ready) SplashScreen.hideAsync();
  }, [ready]);

  if (!ready) return null;

  return (
    <SafeAreaProvider>
      <View style={styles.root}>
        <StatusBar style="dark" />
        <NavigationContainer linking={linking} theme={navigationTheme}>
          <RootNavigator />
        </NavigationContainer>
      </View>
    </SafeAreaProvider>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: colors.bg.screen,
  },
});
