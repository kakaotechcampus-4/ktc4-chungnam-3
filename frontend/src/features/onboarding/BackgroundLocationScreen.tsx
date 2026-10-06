// 00e 위치 2단계 2175:849. 1단계는 허용한 상태로 들어온다. "설정 열기" / "앱 사용 중에만 쓸게요" → 00f.
// 설정 열기는 백그라운드 위치를 요청한다. 안드로이드 11+ 는 시스템이 이 앱의 위치 권한 화면을 열고,
// 사용자가 돌아오면 결과와 관계없이 00f 로 간다.
import { StackActions, useNavigation } from "@react-navigation/native";
import { useRef } from "react";

import { requestBackgroundLocation } from "../../shared/permissions/location";
import Button from "../../shared/ui/Button";
import { metrics } from "../../shared/ui/theme";
import OnboardingLayout, {
    OnboardingActions,
    OnboardingList,
} from "./components/OnboardingLayout";
import PermissionStep from "./components/PermissionStep";

export default function BackgroundLocationScreen() {
    const navigation = useNavigation();
    const next = () => navigation.dispatch(StackActions.replace("FirstSave"));
    // 요청 중에는 다시 누르지 못하게 한다.
    const pending = useRef(false);
    const openSettings = async () => {
        if (pending.current) return;
        pending.current = true;
        try {
            await requestBackgroundLocation();
        } finally {
            next();
        }
    };

    return (
        <OnboardingLayout
            messageTop={metrics.onboarding.messageTop}
            heading="마지막으로 ‘항상 허용’이 필요해요"
            body="설정 화면이 열리면 위치에서 ‘항상 허용’을 골라주세요. 앱을 닫아도 근처에 왔는지 알 수 있어요."
            footer={
                <OnboardingActions>
                    <Button
                        kind="primary"
                        label="설정 열기"
                        onPress={openSettings}
                    />
                    <Button
                        kind="text"
                        label="앱 사용 중에만 쓸게요"
                        onPress={next}
                    />
                </OnboardingActions>
            }
        >
            <OnboardingList>
                <PermissionStep
                    step={1}
                    done
                    title="앱 사용 중에만 허용"
                    caption="허용했어요"
                />
                <PermissionStep
                    step={2}
                    title="설정에서 항상 허용으로 바꾸기"
                    caption="다음 화면에서 바꿔주세요"
                />
            </OnboardingList>
        </OnboardingLayout>
    );
}
