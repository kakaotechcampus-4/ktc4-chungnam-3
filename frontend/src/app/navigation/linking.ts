// 딥링크 URL 을 라우트로 매핑하는 linking 설정.
import type { LinkingOptions } from '@react-navigation/native';
import * as Linking from 'expo-linking';

import { ROUTES, type RootStackParamList } from './routes';

// prefix 는 scheme 을 하드코딩하지 않는다. Expo Go 에서는 exp://…/--/ 가 된다.
export function buildLinkingConfig(): LinkingOptions<RootStackParamList> {
  return {
    prefixes: [Linking.createURL('/')],
    config: {
      screens: {
        [ROUTES.Main]: {
          screens: {
            [ROUTES.Nearby]: 'nearby',
            [ROUTES.Archive]: 'archive',
          },
        },
        [ROUTES.ContentDetail]: 'detail/:placeId',
        [ROUTES.SaveResult]: 'save-result/:resultId',
      },
    },
  };
}
