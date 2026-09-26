// 장소 상세. 위는 지도, 아래는 겹쳐 올라온 시트. 지도는 아직 bg/subtle 자리표시다.
import { type RouteProp, useNavigation, useRoute } from '@react-navigation/native';
import { Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { placeDetailsMock } from '../../shared/api/mock';
import Icon from '../../shared/ui/Icon';
import { colors, effects, metrics, radius, spacing } from '../../shared/ui/theme';
import DetailSheet, { type DetailSheetProps } from './components/DetailSheet';

const details: Readonly<Record<string, DetailSheetProps>> = placeDetailsMock;

export default function ContentDetailScreen() {
  const navigation = useNavigation();
  // RootParamList 는 interface 라 ParamListBase 제약을 못 맞춘다. Pick 으로 타입 별칭을 만든다.
  const route = useRoute<RouteProp<Pick<ReactNavigation.RootParamList, 'ContentDetail'>, 'ContentDetail'>>();
  const insets = useSafeAreaInsets();

  const detail = details[route.params.placeId];
  if (!detail) return null;

  return (
    <View style={styles.screen}>
      <View style={styles.map} />

      <View style={styles.sheet}>
        <ScrollView
          contentContainerStyle={[
            styles.sheetContent,
            { paddingBottom: metrics.contentDetail.sheetPaddingBottom + insets.bottom },
          ]}
        >
          <DetailSheet {...detail} />
        </ScrollView>
      </View>

      <Pressable
        accessibilityRole="button"
        accessibilityLabel="뒤로"
        hitSlop={metrics.contentDetail.backHitSlop}
        onPress={() => navigation.goBack()}
        style={[styles.back, { top: insets.top + spacing.md }]}
      >
        <Icon name="arrowLeft" size={metrics.contentDetail.backIconSize} />
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
  },
  map: {
    height: metrics.contentDetail.mapHeight,
    backgroundColor: colors.bg.subtle,
  },
  sheet: {
    flex: 1,
    marginTop: -metrics.contentDetail.sheetOverlap,
    borderTopLeftRadius: radius.lg,
    borderTopRightRadius: radius.lg,
    backgroundColor: colors.bg.surface,
    boxShadow: effects.elevationLow,
  },
  sheetContent: {
    flexGrow: 1,
    gap: metrics.contentDetail.sheetGap,
    paddingTop: metrics.contentDetail.sheetPaddingTop,
    paddingHorizontal: spacing.lg,
  },
  back: {
    position: 'absolute',
    left: metrics.contentDetail.backLeft,
    padding: spacing.sm,
    borderRadius: radius.full,
    backgroundColor: colors.bg.surface,
    boxShadow: effects.elevationLow,
  },
});
