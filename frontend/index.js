// RN 엔트리. 제스처 핸들러를 먼저 불러오고 앱을 등록한다.
import 'react-native-gesture-handler';
import { registerRootComponent } from 'expo';

import App from './src/app/App';

registerRootComponent(App);
