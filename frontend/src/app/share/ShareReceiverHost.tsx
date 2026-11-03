// 공유 받음 시트를 지금 화면 위에 띄운다(별도 화면으로 이동하지 않는다, 흐름 메모 1). App 의 BottomSheetModalProvider 안에 하나만 둔다.
// "돌아가기"는 시트를 닫고 앱을 뒤로 보낸다. "기억 탭 보기"는 시트를 닫고 기억 탭으로 간다. 바깥 닫힘은 시트만 닫는다.
// signedIn 이 아니게 되면(세션 만료 등) 시트를 닫는다.
// 실제 공유(nativeShareIntent)를 받아 처리 흐름을 부른다. __DEV__ 에서는 개발용 공유 주소(devShareLink)도 받는다.
import * as Linking from "expo-linking";
import { useEffect, useRef } from "react";
import { BackHandler } from "react-native";

import ShareReceivedSheet from "../../features/share-received/ShareReceivedSheet";
import { useAuthState } from "../../shared/auth/session";
import { navigationRef } from "../navigation/navigationRef";
import { ROUTES } from "../navigation/routes";
import { parseDevShareUrl } from "./devShareLink";
import { startNativeShareIntent } from "./nativeShareIntent";
import {
    closeShareSheet,
    receiveSharedText,
    type ShareSheetState,
    useShareSheetState,
} from "./shareReceiver";

export default function ShareReceiverHost() {
    const state = useShareSheetState();
    const signedIn = useAuthState().status === "signedIn";
    // 닫히는 동안에도 마지막 내용을 보인다.
    const shown = useRef<ShareSheetState | null>(null);
    if (state != null) shown.current = state;

    useEffect(() => {
        if (!signedIn) closeShareSheet();
    }, [signedIn]);

    useEffect(
        () =>
            startNativeShareIntent((text) => {
                receiveSharedText(text);
            }),
        [],
    );

    useEffect(() => {
        if (!__DEV__) return;
        const handle = (url: string | null) => {
            const text = url == null ? null : parseDevShareUrl(url);
            if (text != null) receiveSharedText(text);
        };
        Linking.getInitialURL().then(handle);
        const subscription = Linking.addEventListener("url", ({ url }) =>
            handle(url),
        );
        return () => subscription.remove();
    }, []);

    if (shown.current == null) return null;

    const handleBack = () => {
        closeShareSheet();
        BackHandler.exitApp();
    };
    const handleOpenArchive = () => {
        closeShareSheet();
        if (navigationRef.isReady()) {
            navigationRef.navigate(ROUTES.Main, { screen: ROUTES.Archive });
        }
    };

    return (
        <ShareReceivedSheet
            visible={state != null && signedIn}
            content={shown.current}
            onBack={handleBack}
            onOpenArchive={handleOpenArchive}
            onDismiss={closeShareSheet}
        />
    );
}
