// 00a 소개 2175:676 · 00a-err 2196:785. 바랜 사진과 되살아난 사진 일러스트 · "시작하기".
// MVP(게스트 세션): "시작하기"가 설치 id 로 게스트 세션을 조용히 받고 00c 로 간다(00b 는 건너뛴다).
// 실패하면 버튼 위에 오류 상자를 보인다. 버튼 제공자(카카오)가 켜져 있으면 00b 로 간다.
import { useNavigation } from "@react-navigation/native";
import { useRef, useState } from "react";
import { StyleSheet, View } from "react-native";

import { onboardingMock } from "../../shared/api/mock";
import { signIn } from "../../shared/auth/login";
import { BUTTON_PROVIDERS, getProvider } from "../../shared/auth/providers";
import Button from "../../shared/ui/Button";
import { metrics, spacing } from "../../shared/ui/theme";
import LoginError from "./components/LoginError";
import OnboardingLayout, {
    OnboardingActions,
} from "./components/OnboardingLayout";
import PhotoStack from "./components/PhotoStack";

export default function IntroScreen() {
    const navigation = useNavigation();
    // 요청 중에는 다시 누르지 못하게 한다. 진행 중 표시는 디자인이 정해지면 붙인다.
    const pending = useRef(false);
    const [failed, setFailed] = useState(false);

    const start = async () => {
        if (BUTTON_PROVIDERS.length > 0) {
            navigation.navigate("Login");
            return;
        }
        if (pending.current) return;
        pending.current = true;
        setFailed(false);
        try {
            // 성공하면 인증 상태가 onboarding 으로 바뀌어 00c 가 된다.
            await signIn(getProvider("guest"));
        } catch {
            setFailed(true);
        } finally {
            pending.current = false;
        }
    };

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
                    {/* 00a-err: 오류 상자와 버튼 사이 8. */}
                    <View style={styles.start}>
                        {failed && (
                            <LoginError message="시작하지 못했어요. 연결을 확인하고 다시 눌러주세요." />
                        )}
                        <Button
                            kind="primary"
                            label="시작하기"
                            onPress={start}
                        />
                    </View>
                </OnboardingActions>
            }
        />
    );
}

const styles = StyleSheet.create({
    start: {
        gap: spacing.sm,
    },
});
