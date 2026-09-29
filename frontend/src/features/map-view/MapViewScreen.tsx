// 10 지도 탭 · 10b 핀 선택. Google 지도 위에 사진 핀 · 필터 · 내 위치 버튼 · 요약 시트를 올린다.
// 핀을 누르면 요약 시트가 뜬다. 요약을 누르거나 위로 끌면 08 로 간다.
// 지도 빈 곳 · 아래로 끌기 · 뒤로 가기는 시트를 닫는다.
// 06 링크는 area 파라미터로 들어온다. 가운데를 잡은 뒤 파라미터를 비워 같은 링크를 다시 눌러도 움직이게 한다.
import BottomSheet, { BottomSheetView } from "@gorhom/bottom-sheet";
import {
    CommonActions,
    type RouteProp,
    useFocusEffect,
    useNavigation,
    useRoute,
} from "@react-navigation/native";
import { useCallback, useRef, useState } from "react";
import { BackHandler, Pressable, StyleSheet, View } from "react-native";
import MapView, {
    type MapPressEvent,
    PROVIDER_GOOGLE,
    type Region,
} from "react-native-maps";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { MOCK_SCENARIO, mapViewMock } from "../../shared/api/mock";
import Chip from "../../shared/ui/Chip";
import Icon from "../../shared/ui/Icon";
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
import CurrentLocation from "./components/CurrentLocation";
import PinSummary from "./components/PinSummary";

// 카테고리 enum 확정 전까지 목 데이터 한정. 01 처럼 선택은 표시만 바꾸고 핀을 거르지 않는다.
const FILTERS = ["전체", "식당", "카페", "가볼 곳"] as const;

const areas: Readonly<Record<string, Region>> = mapViewMock.areas;
const { current, initialRegion: defaultRegion, pins } = mapViewMock;

const EXPANDED = 1;
const SUMMARY_HEIGHT =
    metrics.sheetHandle.paddingTop +
    metrics.sheetHandle.height +
    metrics.mapView.sheetGap +
    metrics.mapView.summaryThumbWidth / metrics.thumb.aspectRatio +
    metrics.mapView.sheetPaddingBottom;

const regionFor = (area?: string): Region =>
    (area != null && areas[area]) || defaultRegion;

export default function MapViewScreen() {
    const navigation = useNavigation();
    const route =
        useRoute<RouteProp<{ Map: { area?: string } | undefined }, "Map">>();
    const insets = useSafeAreaInsets();
    const mapRef = useRef<MapView>(null);
    const sheetRef = useRef<BottomSheet>(null);
    const [filter, setFilter] = useState<(typeof FILTERS)[number]>("전체");
    const [selectedId, setSelectedId] = useState<string>();
    // 처음 열릴 때(lazy 마운트) 06 에서 넘긴 동네가 있으면 그 자리에서 시작한다.
    const [initialRegion] = useState(() => regionFor(route.params?.area));

    // 이미 열려 있던 지도에 06 링크로 들어오면 그 동네로 옮긴다. 탭이 보인 뒤에 움직이도록 포커스 때 처리한다.
    const area = route.params?.area;
    useFocusEffect(
        useCallback(() => {
            if (area == null) return;
            mapRef.current?.animateToRegion(regionFor(area));
            navigation.dispatch(CommonActions.setParams({ area: undefined }));
        }, [area, navigation]),
    );

    // 시트가 열려 있으면 시스템 뒤로 가기는 시트만 닫는다.
    useFocusEffect(
        useCallback(() => {
            const sub = BackHandler.addEventListener("hardwareBackPress", () => {
                if (selectedId == null) return false;
                sheetRef.current?.close();
                return true;
            });
            return () => sub.remove();
        }, [selectedId]),
    );

    const selected = pins.find((pin) => pin.placeId === selectedId);
    const showLocation = MOCK_SCENARIO.locationPermission !== "denied";

    const openDetail = (placeId: string) =>
        navigation.navigate("ContentDetail", { placeId });
    // Android 는 핀을 눌러도 지도 onPress 가 함께 온다. 핀 누름은 무시한다.
    const pressMap = (event: MapPressEvent) => {
        if (event.nativeEvent.action === "marker-press") return;
        sheetRef.current?.close();
    };
    const locate = () =>
        mapRef.current?.animateToRegion({
            ...current,
            latitudeDelta: defaultRegion.latitudeDelta,
            longitudeDelta: defaultRegion.longitudeDelta,
        });

    return (
        <View style={styles.screen}>
            <MapView
                ref={mapRef}
                provider={PROVIDER_GOOGLE}
                style={StyleSheet.absoluteFill}
                customMapStyle={mapStyle}
                initialRegion={initialRegion}
                onPress={pressMap}
                moveOnMarkerPress={false}
                toolbarEnabled={false}
                showsCompass={false}
            >
                {showLocation && <CurrentLocation coordinate={current} />}
                {pins.map((pin) => (
                    <PhotoMarker
                        key={pin.placeId}
                        coordinate={pin.coordinate}
                        fade={pin.fade}
                        uri={pin.thumbUri}
                        label={pin.place}
                        selected={pin.placeId === selectedId}
                        onPress={() => setSelectedId(pin.placeId)}
                    />
                ))}
            </MapView>

            <View style={[styles.filters, { top: insets.top + spacing.sm }]}>
                {FILTERS.map((label) => (
                    <Chip
                        key={label}
                        label={label}
                        selected={label === filter}
                        onPress={() => setFilter(label)}
                    />
                ))}
            </View>

            {showLocation && (
                <Pressable
                    accessibilityRole="button"
                    accessibilityLabel="내 위치로"
                    hitSlop={metrics.mapView.locateHitSlop}
                    onPress={locate}
                    style={[
                        styles.locate,
                        {
                            bottom: selected
                                ? SUMMARY_HEIGHT +
                                  metrics.mapView.locateSheetGap
                                : metrics.mapView.locateBottom,
                        },
                    ]}
                >
                    <Icon
                        name="locateFixed"
                        size={metrics.mapView.locateIconSize}
                    />
                </Pressable>
            )}

            {/* 멈춤 지점은 요약 하나다. "100%" 는 위로 끄는 동작을 받기 위한 자리이고, 향하는 순간 08 로 간다. */}
            {selected && (
                <BottomSheet
                    ref={sheetRef}
                    index={0}
                    snapPoints={[SUMMARY_HEIGHT, "100%"]}
                    enableDynamicSizing={false}
                    enablePanDownToClose
                    handleComponent={SheetHandle}
                    backgroundStyle={styles.sheetBackground}
                    style={styles.sheet}
                    onAnimate={(_, toIndex) => {
                        if (toIndex !== EXPANDED) return;
                        sheetRef.current?.snapToIndex(0);
                        openDetail(selected.placeId);
                    }}
                    onClose={() => setSelectedId(undefined)}
                >
                    <BottomSheetView style={styles.sheetContent}>
                        <PinSummary
                            time={selected.time}
                            place={selected.place}
                            walk={selected.walk}
                            category={selected.category}
                            thumbUri={selected.thumbUri}
                            fade={selected.fade}
                            showWalk={showLocation}
                            onPress={() => openDetail(selected.placeId)}
                        />
                    </BottomSheetView>
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
    filters: {
        position: "absolute",
        flexDirection: "row",
        gap: spacing.sm,
        paddingHorizontal: metrics.mapView.filtersPaddingHorizontal,
        pointerEvents: "box-none",
    },
    locate: {
        position: "absolute",
        right: metrics.mapView.locateRight,
        padding: metrics.mapView.locatePadding,
        borderRadius: radius.full,
        backgroundColor: colors.bg.surface,
        boxShadow: effects.elevationLow,
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
    sheetContent: {
        paddingTop: metrics.mapView.sheetGap,
        paddingHorizontal: spacing.lg,
    },
});
