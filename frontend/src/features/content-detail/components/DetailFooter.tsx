// 08 시트 하단 버튼. 기본 · 펼침 두 지점 모두 시트 아래에 고정한다(BottomSheetFooter).
// "길 안내 시작" · "이 장소가 아니에요" 는 아직 동작하지 않는다.
import {
    BottomSheetFooter,
    type BottomSheetFooterProps,
} from "@gorhom/bottom-sheet";
import { StyleSheet, View } from "react-native";

import Button from "../../../shared/ui/Button";
import { colors, metrics, size, spacing } from "../../../shared/ui/theme";

// 버튼 두 개 + 간격 + 아래 여백. 시트 높이 계산과 본문 스크롤 여백에 쓴다.
export function footerHeight(bottomInset: number): number {
    return (
        size.button * 2 +
        spacing.xs +
        metrics.contentDetail.sheetPaddingBottom +
        bottomInset
    );
}

type Props = BottomSheetFooterProps & {
    bottomInset: number;
};

export default function DetailFooter({ bottomInset, ...footerProps }: Props) {
    return (
        <BottomSheetFooter {...footerProps}>
            <View
                style={[
                    styles.actions,
                    {
                        paddingBottom:
                            metrics.contentDetail.sheetPaddingBottom +
                            bottomInset,
                    },
                ]}
            >
                <Button kind="primary" label="길 안내 시작" />
                <Button kind="text" label="이 장소가 아니에요" />
            </View>
        </BottomSheetFooter>
    );
}

const styles = StyleSheet.create({
    actions: {
        gap: spacing.xs,
        paddingHorizontal: spacing.lg,
        backgroundColor: colors.bg.surface,
    },
});
