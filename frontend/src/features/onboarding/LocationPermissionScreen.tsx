// 00d 위치 1단계 2175:808. "위치 허용하기" → 00e, "나중에 할게요" → 00f.
// '앱 사용 중' 권한 없이는 '항상 허용'을 받을 수 없어 미루면 00e 를 건너뛴다.
import { StackActions, useNavigation } from "@react-navigation/native";

import Button from "../../shared/ui/Button";
import { metrics } from "../../shared/ui/theme";
import OnboardingLayout, {
    OnboardingActions,
    OnboardingList,
} from "./components/OnboardingLayout";
import PermissionStep from "./components/PermissionStep";

export default function LocationPermissionScreen() {
    const navigation = useNavigation();
    // 4번 커밋: 앱 사용 중 위치 권한을 요청하고, 허용하면 00e · 거절하면 00f 로 간다.
    const allow = () =>
        navigation.dispatch(StackActions.replace("BackgroundLocation"));
    const later = () => navigation.dispatch(StackActions.replace("FirstSave"));

    return (
        <OnboardingLayout
            messageTop={metrics.onboarding.messageTop}
            heading="근처인지 알려면 위치가 필요해요"
            body="위치는 저장한 곳 근처에 왔는지 확인할 때만 써요. 두 단계로 허용해요."
            footer={
                <OnboardingActions>
                    <Button
                        kind="primary"
                        label="위치 허용하기"
                        onPress={allow}
                    />
                    <Button kind="text" label="나중에 할게요" onPress={later} />
                </OnboardingActions>
            }
        >
            <OnboardingList>
                <PermissionStep
                    step={1}
                    title="앱 사용 중에만 허용"
                    caption="곧 뜨는 창에서 이걸 골라주세요"
                />
                <PermissionStep
                    step={2}
                    title="설정에서 항상 허용으로 바꾸기"
                    caption="앱을 닫아도 근처에 왔는지 알 수 있어요"
                />
            </OnboardingList>
        </OnboardingLayout>
    );
}
