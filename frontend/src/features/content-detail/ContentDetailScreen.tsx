// 장소 상세. 지도 위에 끌 수 있는 시트. 지도는 아직 bg/subtle 자리표시다(8번 커밋에서 실제 지도).
// 시트 멈춤 지점: 기본(08, 메모 2줄 상태의 내용 높이를 처음 한 번 재서 고정) · 펼침(08c, 상태바 아래까지).
// 뒤로가기 버튼은 Figma 레이어 순서대로 시트 아래에 있어 펼치면 가려진다.
import BottomSheet, {
    type BottomSheetFooterProps,
    BottomSheetScrollView,
} from "@gorhom/bottom-sheet";
import {
    type RouteProp,
    StackActions,
    useFocusEffect,
    useNavigation,
    useRoute,
} from "@react-navigation/native";
import { useCallback, useRef, useState } from "react";
import {
    BackHandler,
    type LayoutChangeEvent,
    Pressable,
    StyleSheet,
    View,
} from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { MOCK_SCENARIO, placeDetailsMock } from "../../shared/api/mock";
import Icon from "../../shared/ui/Icon";
import NotFound from "../../shared/ui/NotFound";
import {
    colors,
    effects,
    metrics,
    radius,
    spacing,
} from "../../shared/ui/theme";
import DetailFooter, { footerHeight } from "./components/DetailFooter";
import DetailSheet, { type DetailSheetProps } from "./components/DetailSheet";
import SheetHandle from "./components/SheetHandle";

const details: Readonly<Record<string, DetailSheetProps>> = placeDetailsMock;

const EXPANDED = 1;
const HANDLE_HEIGHT =
    metrics.contentDetail.sheetPaddingTop + metrics.contentDetail.handleHeight;

export default function ContentDetailScreen() {
    const navigation = useNavigation();
    // RootParamList 는 interface 라 ParamListBase 제약을 못 맞춘다. Pick 으로 타입 별칭을 만든다.
    const route =
        useRoute<
            RouteProp<
                Pick<ReactNavigation.RootParamList, "ContentDetail">,
                "ContentDetail"
            >
        >();
    const insets = useSafeAreaInsets();
    const sheetRef = useRef<BottomSheet>(null);
    const sheetIndex = useRef(0);
    const [bodyHeight, setBodyHeight] = useState<number>();
    const [noteExpanded, setNoteExpanded] = useState(false);

    // 펼친 상태에서 시스템 뒤로 가기는 먼저 기본 지점으로 접는다. 기본 지점이면 화면을 나간다.
    useFocusEffect(
        useCallback(() => {
            const sub = BackHandler.addEventListener("hardwareBackPress", () => {
                if (sheetIndex.current !== EXPANDED) return false;
                sheetRef.current?.snapToIndex(0);
                return true;
            });
            return () => sub.remove();
        }, []),
    );

    const detail = details[route.params.placeId];
    // 삭제·만료된 장소. 12 를 재사용하고 push 화면이라 ← 를 쓴다.
    if (!detail) {
        return (
            <NotFound
                topIcon="arrowLeft"
                heading="이 장소를 찾을 수 없어요"
                body="삭제됐거나 기간이 지난 저장이에요."
                onClose={() => navigation.goBack()}
                onGoArchive={() =>
                    navigation.dispatch(
                        StackActions.popTo("Main", { screen: "Archive" }),
                    )
                }
            />
        );
    }

    const showWalk = MOCK_SCENARIO.locationPermission !== "denied";
    const footer = footerHeight(insets.bottom);
    // 본문 높이는 메모 2줄 상태로 한 번만 잰다. 이후 메모를 펼쳐도 기본 지점은 바뀌지 않는다.
    const measureBody = (event: LayoutChangeEvent) => {
        if (bodyHeight == null) setBodyHeight(event.nativeEvent.layout.height);
    };
    const renderFooter = (props: BottomSheetFooterProps) => (
        <DetailFooter {...props} bottomInset={insets.bottom} />
    );

    return (
        <View style={styles.screen}>
            {/* 기본 지점 높이를 재기 위한 본문. 불투명한 지도 영역 아래 레이어라 보이지 않는다. 한 번 재면 치운다. */}
            {bodyHeight == null && (
                <View
                    pointerEvents="none"
                    style={styles.measure}
                    onLayout={measureBody}
                >
                    <DetailSheet
                        {...detail}
                        showWalk={showWalk}
                        noteExpanded={false}
                    />
                </View>
            )}

            <View style={styles.map} />

            <Pressable
                accessibilityRole="button"
                accessibilityLabel="뒤로"
                hitSlop={metrics.contentDetail.backHitSlop}
                onPress={() => navigation.goBack()}
                style={[styles.back, { top: insets.top + spacing.md }]}
            >
                <Icon
                    name="arrowLeft"
                    size={metrics.contentDetail.backIconSize}
                />
            </Pressable>

            {bodyHeight != null && (
                <BottomSheet
                    ref={sheetRef}
                    index={0}
                    snapPoints={[
                        HANDLE_HEIGHT +
                            bodyHeight +
                            metrics.contentDetail.sheetGap +
                            footer,
                        "100%",
                    ]}
                    topInset={insets.top}
                    enableDynamicSizing={false}
                    enablePanDownToClose={false}
                    handleComponent={SheetHandle}
                    footerComponent={renderFooter}
                    backgroundStyle={styles.sheetBackground}
                    style={styles.sheet}
                    onAnimate={(_, toIndex) =>
                        setNoteExpanded(toIndex === EXPANDED)
                    }
                    onChange={(index) => {
                        sheetIndex.current = index;
                    }}
                >
                    <BottomSheetScrollView
                        contentContainerStyle={{
                            paddingBottom:
                                footer + metrics.contentDetail.sheetGap,
                        }}
                    >
                        <DetailSheet
                            {...detail}
                            showWalk={showWalk}
                            noteExpanded={noteExpanded}
                        />
                    </BottomSheetScrollView>
                </BottomSheet>
            )}
        </View>
    );
}

const styles = StyleSheet.create({
    screen: {
        flex: 1,
    },
    map: {
        ...StyleSheet.absoluteFill,
        backgroundColor: colors.bg.subtle,
    },
    measure: {
        position: "absolute",
        width: "100%",
    },
    sheet: {
        boxShadow: effects.elevationLow,
        borderTopLeftRadius: radius.lg,
        borderTopRightRadius: radius.lg,
    },
    sheetBackground: {
        backgroundColor: colors.bg.surface,
        borderTopLeftRadius: radius.lg,
        borderTopRightRadius: radius.lg,
    },
    back: {
        position: "absolute",
        left: metrics.contentDetail.backLeft,
        padding: spacing.sm,
        borderRadius: radius.full,
        backgroundColor: colors.bg.surface,
        boxShadow: effects.elevationLow,
    },
});
