// 00b 로그인 2175:722 · 00b-err 2175:742. 제공자 목록(shared/auth/providers)을 순회해 버튼을 그린다.
// 사용자가 제공자 창을 닫으면 오류 없이 머물고, 통신 · 서버 오류일 때만 버튼 위에 문구를 보인다.
// 로그인에 성공하면 인증 상태가 onboarding 으로 바뀌어 RootNavigator 가 00c 를 보인다(shared/auth/login).
import { useState } from "react";
import { StyleSheet, Text, View } from "react-native";

import { onboardingMock } from "../../shared/api/mock";
import { signIn } from "../../shared/auth/login";
import {
    type LoginProvider,
    LOGIN_PROVIDERS,
} from "../../shared/auth/providers";
import { colors, metrics, spacing, typography } from "../../shared/ui/theme";
import LoginError from "./components/LoginError";
import OnboardingLayout from "./components/OnboardingLayout";
import PhotoStack from "./components/PhotoStack";
import SocialLoginButton from "./components/SocialLoginButton";

export default function LoginScreen() {
    // 진행 중에는 버튼을 막는다. 진행 중 표시는 디자인이 정해지면 붙인다.
    const [pending, setPending] = useState(false);
    const [failed, setFailed] = useState<LoginProvider>();

    const start = async (provider: LoginProvider) => {
        if (pending) return;
        setPending(true);
        setFailed(undefined);
        try {
            // 성공하면 화면이 00c 로 바뀐다. 창을 닫았으면(cancelled) 그대로 머문다.
            await signIn(provider);
        } catch {
            setFailed(provider);
        } finally {
            setPending(false);
        }
    };

    return (
        <OnboardingLayout
            messageTop={metrics.onboarding.messageTop}
            heading="어떤 계정으로 시작할까요?"
            body="저장한 장소와 기억은 이 계정에 보관돼요. 폰을 바꿔도 이어서 볼 수 있어요."
            footer={
                <>
                    <View style={styles.providers}>
                        {failed && (
                            <LoginError
                                message={`${failed.name} 로그인을 마치지 못했어요. 연결을 확인하고 다시 눌러주세요.`}
                            />
                        )}
                        {LOGIN_PROVIDERS.map((provider) => (
                            <SocialLoginButton
                                key={provider.id}
                                variant={provider.variant}
                                label={provider.label}
                                disabled={pending}
                                onPress={() => start(provider)}
                            />
                        ))}
                    </View>
                    {/* 이용약관 · 개인정보 처리방침 링크는 페이지가 생기면 연결한다. */}
                    <Text style={styles.legal}>
                        계속하면 이용약관과 개인정보 처리방침에 동의하게 돼요.
                    </Text>
                </>
            }
        >
            <PhotoStack
                layout={metrics.onboarding.login}
                photos={onboardingMock.loginPhotos}
            />
        </OnboardingLayout>
    );
}

const styles = StyleSheet.create({
    // 오류 문구와 버튼, 버튼끼리 8. 제공자가 늘면 아래로 쌓인다.
    providers: {
        gap: spacing.sm,
        paddingHorizontal: spacing.lg,
    },
    legal: {
        ...typography.captionMeta,
        paddingTop: metrics.onboarding.legalPaddingTop,
        paddingBottom: metrics.onboarding.legalPaddingBottom,
        paddingHorizontal: spacing.lg,
        textAlign: "center",
        color: colors.text.secondary,
    },
});
