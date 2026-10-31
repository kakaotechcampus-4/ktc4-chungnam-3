// RN 엔트리. 제스처 핸들러를 먼저 불러오고 앱을 등록한다.
// 백그라운드 태스크(지오펜스)는 앱보다 먼저, 번들 최상위에서 정의한다(백그라운드로 깨어나면 앱이 마운트되지 않는다).
import "react-native-gesture-handler";
import { registerRootComponent } from "expo";

import App from "./src/app/App";
import { defineGeofenceTask } from "./src/runtime/geofence/geofenceTask";

defineGeofenceTask();
registerRootComponent(App);
