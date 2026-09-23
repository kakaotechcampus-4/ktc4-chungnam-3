// 라우트 이름과 파라미터 타입 정의.
import type { NavigatorScreenParams } from '@react-navigation/native';

export const ROUTES = {
  Main: 'Main',
  Nearby: 'Nearby',
  Archive: 'Archive',
  ContentDetail: 'ContentDetail',
  SaveResult: 'SaveResult',
} as const;

export type MainTabParamList = {
  [ROUTES.Nearby]: undefined;
  [ROUTES.Archive]: undefined;
};

export type RootStackParamList = {
  [ROUTES.Main]: NavigatorScreenParams<MainTabParamList>;
  [ROUTES.ContentDetail]: { placeId: string };
  [ROUTES.SaveResult]: { resultId: string };
};

// features 는 app 을 import 하지 않는다. useNavigation() 을 제네릭 없이 써도 라우트가 타입 검사되게 한다.
declare global {
  namespace ReactNavigation {
    interface RootParamList extends RootStackParamList {}
  }
}
