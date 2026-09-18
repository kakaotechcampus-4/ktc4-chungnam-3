// RN 엔트리. 앱 등록과 백그라운드 태스크 등록만 한다.
import 'react-native-gesture-handler';
import { registerRootComponent } from 'expo';

import App from './src/app/App';

registerRootComponent(App);
