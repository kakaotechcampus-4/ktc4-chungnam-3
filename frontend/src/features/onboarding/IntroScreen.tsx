// 00a 소개 2175:676. 바랜 사진과 되살아난 사진 일러스트 · "시작하기" → 00b.
import { useNavigation } from "@react-navigation/native";

import { onboardingMock } from "../../shared/api/mock";
import Button from "../../shared/ui/Button";
import { metrics } from "../../shared/ui/theme";
import OnboardingLayout, {
    OnboardingActions,
} from "./components/OnboardingLayout";
import PhotoStack from "./components/PhotoStack";

export default function IntroScreen() {
    const navigation = useNavigation();
    return (
        <OnboardingLayout
            header={
                <PhotoStack
                    layout={metrics.onboarding.intro}
                    photos={onboardingMock.introPhotos}
                    raiseFront
                />
            }
            messageTop={metrics.onboarding.introMessageTop}
            heading="저장만 해두고 잊은 곳, 근처에 가면 다시 꺼내드릴게요"
            body="Shorts에서 저장한 장소를 기억해뒀다가, 그 곳 가까이 갔을 때 조용히 알려줘요."
            footer={
                <OnboardingActions>
                    <Button
                        kind="primary"
                        label="시작하기"
                        onPress={() => navigation.navigate("Login")}
                    />
                </OnboardingActions>
            }
        />
    );
}
