// 00f 첫 저장 안내 2175:891. "시작하기" → 근처 탭(인증 상태 signedIn).
import { finishOnboarding } from "../../shared/auth/session";
import Button from "../../shared/ui/Button";
import { metrics } from "../../shared/ui/theme";
import GuideRow from "./components/GuideRow";
import OnboardingLayout, {
    OnboardingActions,
    OnboardingList,
} from "./components/OnboardingLayout";

export default function FirstSaveScreen() {
    return (
        <OnboardingLayout
            messageTop={metrics.onboarding.messageTop}
            heading="Shorts에서 공유하면 여기에 쌓여요"
            body="마음에 드는 장소 영상을 보면 이렇게 저장해요."
            footer={
                <OnboardingActions>
                    <Button
                        kind="primary"
                        label="시작하기"
                        onPress={finishOnboarding}
                    />
                </OnboardingActions>
            }
        >
            <OnboardingList>
                <GuideRow
                    icon="share"
                    title="영상에서 공유 누르기"
                    caption="Shorts 화면의 공유 버튼"
                />
                <GuideRow
                    icon="images"
                    title="REMEMBRALL 고르기"
                    caption="공유할 앱 목록에서 골라요"
                />
                <GuideRow
                    icon="pin"
                    title="장소를 찾아 저장"
                    caption="영상 속 장소를 알아서 찾아요"
                />
            </OnboardingList>
        </OnboardingLayout>
    );
}
