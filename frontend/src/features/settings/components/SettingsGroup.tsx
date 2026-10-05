// 설정 묶음. 제목 + 카드(줄 사이 구분선) + 아래 안내(선택).
import { Children, Fragment, type ReactNode } from "react";
import { StyleSheet, Text, View } from "react-native";

import {
    colors,
    radius,
    spacing,
    stroke,
    typography,
} from "../../../shared/ui/theme";

type Props = {
    title: string;
    note?: string;
    children: ReactNode;
};

export default function SettingsGroup({ title, note, children }: Props) {
    const rows = Children.toArray(children);
    return (
        <View style={styles.group}>
            <Text style={styles.caption}>{title}</Text>
            <View style={styles.card}>
                {rows.map((row, index) => (
                    <Fragment key={index}>
                        {index > 0 && <View style={styles.divider} />}
                        {row}
                    </Fragment>
                ))}
            </View>
            {note != null && <Text style={styles.caption}>{note}</Text>}
        </View>
    );
}

const styles = StyleSheet.create({
    group: {
        gap: spacing.sm,
        paddingTop: spacing.lg,
        paddingHorizontal: spacing.lg,
    },
    caption: {
        ...typography.captionMeta,
        color: colors.text.secondary,
    },
    card: {
        overflow: "hidden",
        borderRadius: radius.lg,
        backgroundColor: colors.bg.surface,
    },
    divider: {
        height: stroke.thin,
        backgroundColor: colors.border.default,
    },
});
