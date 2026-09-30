// 장소 상세. Google 지도 위에 끌 수 있는 시트. 지도에는 이 장소 핀 · 현재 위치 · 점선 경로를 그린다.
// 현재 위치 · 경로는 위치 권한이 "denied" 일 때만 숨긴다. 경로는 경로 API 전까지 직선 자리표시다.
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
    PixelRatio,
    Pressable,
    StyleSheet,
    View,
} from "react-native";
import MapView, {
    type LatLng,
    Polyline,
    PROVIDER_GOOGLE,
} from "react-native-maps";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import {
    MOCK_SCENARIO,
    mapViewMock,
    placeDetailsMock,
} from "../../shared/api/mock";
import CurrentLocation from "../../shared/ui/CurrentLocation";
import Icon from "../../shared/ui/Icon";
import NotFound from "../../shared/ui/NotFound";
import PhotoMarker from "../../shared/ui/PhotoMarker";
import SheetHandle from "../../shared/ui/SheetHandle";
import {
    colors,
    effects,
    mapStyle,
    metrics,
    radius,
    spacing,
} from "../../shared/ui/theme";
import DetailFooter, { footerHeight } from "./components/DetailFooter";
import DetailSheet, { type DetailSheetProps } from "./components/DetailSheet";

const details: Readonly<
    Record<string, DetailSheetProps & { coordinate: LatLng }>
> = placeDetailsMock;
const { current, initialRegion: mapRegion } = mapViewMock;

const EXPANDED = 1;
const HANDLE_HEIGHT =
    metrics.sheetHandle.paddingTop + metrics.sheetHandle.height;

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
    const mapRef = useRef<MapView>(null);
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

    const { coordinate, ...sheet } = detail;
    const showWalk = MOCK_SCENARIO.locationPermission !== "denied";
    const footer = footerHeight(insets.bottom);
    const sheetHeight =
        bodyHeight == null
            ? 0
            : HANDLE_HEIGHT +
              bodyHeight +
              metrics.contentDetail.sheetGap +
              footer;
    // 핀 · 경로가 뒤로가기와 시트에 가리지 않게 지도 영역을 줄인다. Google 로고도 시트 위로 올라온다.
    const mapPadding = {
        top:
            insets.top +
            spacing.md +
            metrics.contentDetail.backSize +
            spacing.md,
        right: insets.right,
        bottom: sheetHeight,
        left: insets.left,
    };
    // 현재 위치와 장소가 함께 보이게 맞춘다. 핀은 좌표 위로 솟으므로 위 여백은 핀 높이다.
    // Android 의 edgePadding 은 px 다(mapPadding 은 dp).
    const fitRoute = () => {
        if (!showWalk) return;
        const px = PixelRatio.getPixelSizeForLayoutSize;
        const fit = metrics.contentDetail.mapFitPadding;
        mapRef.current?.fitToCoordinates([current, coordinate], {
            edgePadding: {
                top: px(
                    metrics.photoMarker[detail.fade].width /
                        metrics.thumb.aspectRatio,
                ),
                right: px(fit),
                bottom: px(fit),
                left: px(fit),
            },
            animated: false,
        });
    };
    // 본문 높이는 메모 2줄 상태로 한 번만 잰다. 이후 메모를 펼쳐도 기본 지점은 바뀌지 않는다.
    const measureBody = (event: LayoutChangeEvent) => {
        if (bodyHeight == null) setBodyHeight(event.nativeEvent.layout.height);
    };
    // 바꾸기 모드 02c. 저장 결과 모달 안에 PlaceSearch 하나만 올린다.
    const openReplace = () =>
        navigation.navigate("SaveResult", {
            screen: "PlaceSearch",
            params: { placeId: route.params.placeId },
        });
    const renderFooter = (props: BottomSheetFooterProps) => (
        <DetailFooter
            {...props}
            bottomInset={insets.bottom}
            onNotThisPlace={openReplace}
        />
    );

    return (
        <View style={styles.screen}>
            {/* 기본 지점 높이를 재기 위한 본문. 한 번 재면 치운다. */}
            {bodyHeight == null && (
                <View
                    pointerEvents="none"
                    style={styles.measure}
                    onLayout={measureBody}
                >
                    <DetailSheet
                        {...sheet}
                        showWalk={showWalk}
                        noteExpanded={false}
                    />
                </View>
            )}

            {/* 시트 높이를 안 뒤에 띄워 처음부터 줄어든 지도 영역 가운데에 장소를 둔다. */}
            {bodyHeight != null && (
                <MapView
                    ref={mapRef}
                    provider={PROVIDER_GOOGLE}
                    style={StyleSheet.absoluteFill}
                    customMapStyle={mapStyle}
                    initialRegion={{
                        ...coordinate,
                        latitudeDelta: mapRegion.latitudeDelta,
                        longitudeDelta: mapRegion.longitudeDelta,
                    }}
                    mapPadding={mapPadding}
                    onMapReady={fitRoute}
                    moveOnMarkerPress={false}
                    toolbarEnabled={false}
                    showsCompass={false}
                >
                    {showWalk && (
                        <Polyline
                            coordinates={[current, coordinate]}
                            strokeColor={colors.text.secondary}
                            strokeWidth={metrics.contentDetail.routeWidth}
                            lineDashPattern={[
                                ...metrics.contentDetail.routeDashPattern,
                            ]}
                        />
                    )}
                    {showWalk && <CurrentLocation coordinate={current} />}
                    <PhotoMarker
                        coordinate={coordinate}
                        fade={detail.fade}
                        uri={detail.thumbUri}
                        label={detail.place}
                        selected
                    />
                </MapView>
            )}

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
                    snapPoints={[sheetHeight, "100%"]}
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
                            {...sheet}
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
    // 지도 타일이 뜨기 전에는 땅 색이 보인다.
    screen: {
        flex: 1,
        backgroundColor: colors.bg.screen,
    },
    // 화면 아래 바깥에 두어 보이지 않게 잰다.
    measure: {
        position: "absolute",
        top: "100%",
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
