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
