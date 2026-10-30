// 장소 상세. Google 지도 위에 끌 수 있는 시트. 지도에는 이 장소 핀 · 현재 위치 · 점선 경로를 그린다.
// 현재 위치 · 경로는 위치 권한이 "denied" 일 때만 숨긴다. 경로는 경로 API 전까지 직선 자리표시다.
// 시트 멈춤 지점: 기본(08, 메모 2줄 상태의 내용 높이를 처음 한 번 재서 고정) · 펼침(08c, 상태바 아래까지).
// 뒤로가기 버튼은 Figma 레이어 순서대로 시트 아래에 있어 펼치면 가려진다.
// 두 경로가 있다. placeId 로 열면(지도 · 근처) 장소 목을 그리고 다른 저장 장소를 미니 핀으로 그린다.
// personalSaveId 로 열면(기억 탭 · 저장 결과) 저장물 상세 API 를 읽어 첫 장소를 그린다(savedPlaceSheet). 미니 핀은 없다.
// 저장물이 ready 가 아니면(장소가 없어졌거나 다시 분석 중) 저장 결과(02~05)로 바꾼다. 404 는 12.
// 읽는 동안은 바탕과 뒤로가기만, 읽기 실패는 그 위에 다시 불러오기 문구를 보인다.
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
import { useCallback, useEffect, useRef, useState } from "react";
import {
    BackHandler,
    type LayoutChangeEvent,
    PixelRatio,
    Pressable,
    StyleSheet,
    Text,
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
import { usePersonalSaveDetail } from "../../shared/api/usePersonalSaveDetail";
import { openExternalUrl } from "../../shared/external-links/openExternalUrl";
import { youtubeShortsUrl } from "../../shared/external-links/youtubeLinks";
import CurrentLocation from "../../shared/ui/CurrentLocation";
import Icon from "../../shared/ui/Icon";
import NotFound from "../../shared/ui/NotFound";
import PhotoMarker, { photoMarkerSize } from "../../shared/ui/PhotoMarker";
import PinLabel, { PIN_LABEL_EXTENT } from "../../shared/ui/PinLabel";
import SheetHandle from "../../shared/ui/SheetHandle";
import type { ThumbFade } from "../../shared/ui/Thumb";
import {
    colors,
    effects,
    mapStyle,
    metrics,
    radius,
    spacing,
    typography,
} from "../../shared/ui/theme";
import DetailFooter, { footerHeight } from "./components/DetailFooter";
import DetailSheet, { type DetailSheetProps } from "./components/DetailSheet";
import { toSavedPlaceSheet } from "./savedPlaceSheet";

type Detail = DetailSheetProps & { coordinate: LatLng };
type MiniPlace = {
    key: string;
    coordinate: LatLng;
    fade: ThumbFade;
    thumbUri?: string;
};

const details: Readonly<Record<string, Detail>> = placeDetailsMock;
const { current, initialRegion: mapRegion } = mapViewMock;

const EXPANDED = 1;
const HANDLE_HEIGHT =
    metrics.sheetHandle.paddingTop + metrics.sheetHandle.height;

export default function ContentDetailScreen() {
    // RootParamList 는 interface 라 ParamListBase 제약을 못 맞춘다. Pick 으로 타입 별칭을 만든다.
    const route =
        useRoute<
            RouteProp<
                Pick<ReactNavigation.RootParamList, "ContentDetail">,
                "ContentDetail"
            >
        >();
    const params = route.params;
    return "placeId" in params ? (
        <MockPlaceDetail placeId={params.placeId} />
    ) : (
        <SavedPlaceDetail personalSaveId={params.personalSaveId} />
    );
}

// 지도 · 근처에서 여는 08. 장소 목을 그린다.
function MockPlaceDetail({ placeId }: { placeId: string }) {
    const navigation = useNavigation();
    const detail = details[placeId];
    if (!detail) return <PlaceNotFound />;
    // 08 지도의 다른 저장 장소(미니 핀). 상세가 있는 곳이 곧 장소 확정 저장물이다.
    const otherPlaces = Object.entries(details)
        .filter(([id]) => id !== placeId)
        .map(([id, place]) => ({ key: id, ...place }));
    return (
        <ContentDetailView
            detail={detail}
            otherPlaces={otherPlaces}
            onReplace={() =>
                navigation.navigate("SaveResult", {
                    screen: "PlaceSearch",
                    params: { placeId },
                })
            }
        />
    );
}

// 기억 탭 · 저장 결과에서 여는 08. 저장물 상세 API 를 읽는다.
function SavedPlaceDetail({ personalSaveId }: { personalSaveId: string }) {
    const navigation = useNavigation();
    const { view, reload } = usePersonalSaveDetail(personalSaveId, {
        pollWhileAnalyzing: false,
    });
    const notReady = view.status === "loaded" && view.state.kind !== "ready";

    useEffect(() => {
        if (!notReady) return;
        navigation.dispatch(
            StackActions.replace("SaveResult", {
                screen: "Result",
                params: { personalSaveId },
            }),
        );
    }, [notReady, navigation, personalSaveId]);

    if (view.status === "notFound") return <PlaceNotFound />;
    if (view.status === "failed") return <DetailPending onRetry={reload} />;
    const sheet =
        view.status === "loaded"
            ? toSavedPlaceSheet(view.detail, new Date())
            : null;
    if (view.status !== "loaded" || sheet == null) return <DetailPending />;

    const { detail } = view;
    return (
        <ContentDetailView
            detail={sheet}
            otherPlaces={[]}
            onReplace={() =>
                navigation.navigate("SaveResult", {
                    screen: "PlaceSearch",
                    params: {
                        placeId: detail.places[0].placeId,
                        sourceTitle: detail.title,
                    },
                })
            }
            onOpenSource={() =>
                openExternalUrl(youtubeShortsUrl(detail.videoId))
            }
        />
    );
}

// 삭제·만료된 장소. 12 를 재사용하고 push 화면이라 ← 를 쓴다.
function PlaceNotFound() {
    const navigation = useNavigation();
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

// 읽는 중(onRetry 없음)이면 바탕과 뒤로가기만, 읽기 실패면 다시 불러오기 문구를 보인다.
function DetailPending({ onRetry }: { onRetry?: () => void }) {
    const navigation = useNavigation();
    const insets = useSafeAreaInsets();
    return (
        <View style={styles.screen}>
            {onRetry != null && (
                <Pressable
                    accessibilityRole="button"
                    onPress={onRetry}
                    style={styles.pending}
                >
                    <Text style={styles.pendingCaption}>
                        확인하지 못했어요. 눌러서 다시 불러와요
                    </Text>
                </Pressable>
            )}
            <BackButton
                top={insets.top + spacing.md}
                onPress={() => navigation.goBack()}
            />
        </View>
    );
}

function BackButton({ top, onPress }: { top: number; onPress: () => void }) {
    return (
        <Pressable
            accessibilityRole="button"
            accessibilityLabel="뒤로"
            hitSlop={metrics.contentDetail.backHitSlop}
            onPress={onPress}
            style={[styles.back, { top }]}
        >
            <Icon name="arrowLeft" size={metrics.contentDetail.backIconSize} />
        </Pressable>
    );
}

function ContentDetailView({
    detail,
    otherPlaces,
    onReplace,
    onOpenSource,
}: {
    detail: Detail;
    otherPlaces: readonly MiniPlace[];
    onReplace: () => void;
    onOpenSource?: () => void;
}) {
    const navigation = useNavigation();
    const insets = useSafeAreaInsets();
    const mapRef = useRef<MapView>(null);
    const sheetRef = useRef<BottomSheet>(null);
    const sheetIndex = useRef(0);
    const [bodyHeight, setBodyHeight] = useState<number>();
    const [noteExpanded, setNoteExpanded] = useState(false);

    // 펼친 상태에서 시스템 뒤로 가기는 먼저 기본 지점으로 접는다. 기본 지점이면 화면을 나간다.
    useFocusEffect(
        useCallback(() => {
            const sub = BackHandler.addEventListener(
                "hardwareBackPress",
                () => {
                    if (sheetIndex.current !== EXPANDED) return false;
                    sheetRef.current?.snapToIndex(0);
                    return true;
                },
            );
            return () => sub.remove();
        }, []),
    );

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
    // 이름표는 좌표 아래로 붙으므로 아래 여백에 이름표 영역을 더해 시트에 가려지지 않게 한다.
    // Android 의 edgePadding 은 px 다(mapPadding 은 dp).
    const fitRoute = () => {
        if (!showWalk) return;
        const px = PixelRatio.getPixelSizeForLayoutSize;
        const fit = metrics.contentDetail.mapFitPadding;
        mapRef.current?.fitToCoordinates([current, coordinate], {
            edgePadding: {
                top: px(
                    photoMarkerSize(detail.fade, "detail").width /
                        metrics.thumb.aspectRatio,
                ),
                right: px(fit),
                bottom: px(fit + PIN_LABEL_EXTENT),
                left: px(fit),
            },
            animated: false,
        });
    };
    // 본문 높이는 메모 2줄 상태로 한 번만 잰다. 이후 메모를 펼쳐도 기본 지점은 바뀌지 않는다.
    const measureBody = (event: LayoutChangeEvent) => {
        if (bodyHeight == null) setBodyHeight(event.nativeEvent.layout.height);
    };
    // "이 장소가 아니에요" → 바꾸기 모드 02c. 저장 결과 모달 안에 PlaceSearch 하나만 올린다.
    const renderFooter = (props: BottomSheetFooterProps) => (
        <DetailFooter
            {...props}
            bottomInset={insets.bottom}
            onNotThisPlace={onReplace}
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
                    {/* 08 핀은 흰 테두리 그대로 두고 이름표만 붙인다. */}
                    {/* 다른 저장 장소는 먼저 넣어 이 장소 핀 아래에 그려지게 한다(zIndex 는 쓰지 않는다). 누르는 동작은 없다. */}
                    {otherPlaces.map((place) => (
                        <PhotoMarker
                            key={place.key}
                            coordinate={place.coordinate}
                            fade={place.fade}
                            uri={place.thumbUri}
                            variant="mini"
                        />
                    ))}
                    <PhotoMarker
                        coordinate={coordinate}
                        fade={detail.fade}
                        uri={detail.thumbUri}
                        variant="detail"
                    />
                    <PinLabel coordinate={coordinate} label={detail.place} />
                </MapView>
            )}

            <BackButton
                top={insets.top + spacing.md}
                onPress={() => navigation.goBack()}
            />

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
                            onOpenSource={onOpenSource}
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
    // 읽기 실패 문구는 화면 가운데에 둔다(Figma 에 08 로딩 · 실패 표현이 없다).
    pending: {
        flex: 1,
        alignItems: "center",
        justifyContent: "center",
        paddingHorizontal: spacing.lg,
    },
    pendingCaption: {
        ...typography.captionMeta,
        color: colors.text.secondary,
    },
});
