// 네비게이터 밖(공유 받음 시트 등)에서 화면을 옮길 때 쓰는 ref. App 의 NavigationContainer 에 연결한다.
import { createNavigationContainerRef } from "@react-navigation/native";

import type { RootStackParamList } from "./routes";

export const navigationRef = createNavigationContainerRef<RootStackParamList>();
