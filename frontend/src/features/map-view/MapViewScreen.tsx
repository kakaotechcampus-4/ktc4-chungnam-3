// 10 지도 탭 · 10b 핀 선택. Google 지도 위에 내 장소 사진 핀 · 내 위치 버튼 · 요약 시트를 올린다.
// 핀은 내 장소(useSavedPlaces, 장소마다 가장 최근 저장물)다. 필터 칩은 카테고리가 응답에 없어 거를 수 없으므로 숨긴다.
// 처음 영역: 06 링크의 동네(area) > 지금 위치 가운데 > 핀 전체 > 대한민국 전체. 지금 위치를 읽는 동안은 지도를 그리지 않고 바탕만 둔다.
// 지금 위치를 모르면 장소를 다 읽을 때까지(실패 포함) 기다려 정한다. 한 번 정한 뒤에는 다시 맞추지 않는다.
// 핀을 누르면 요약 시트가 뜬다. 요약을 누르거나 위로 끌면 저장물 08(personalSaveId · placeId)로 간다.
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
import {
    BackHandler,
    PixelRatio,
    Pressable,
    StyleSheet,
    View,
} from "react-native";
import MapView, {
    type MapPressEvent,
    PROVIDER_GOOGLE,
    type Region,
} from "react-native-maps";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { mapViewMock } from "../../shared/api/mock";
import {
    type SavedPlacesView,
    useSavedPlaces,
} from "../../shared/api/useSavedPlaces";
import {
    type DeviceLocation,
    useDeviceLocation,
} from "../../shared/permissions/useDeviceLocation";
import CurrentLocation from "../../shared/ui/CurrentLocation";
import Icon from "../../shared/ui/Icon";
import PhotoMarker, { photoMarkerSize } from "../../shared/ui/PhotoMarker";
import PinLabel from "../../shared/ui/PinLabel";
import SheetHandle from "../../shared/ui/SheetHandle";
import {
    colors,
    effects,
    mapStyle,
    metrics,
    radius,
    spacing,
} from "../../shared/ui/theme";
import PinSummary from "./components/PinSummary";
import { toMapPin } from "./mapPins";

// 06 링크의 동네 영역. 근처 탭이 목인 동안 목 동네 이름으로 찾는다.
const areas: Readonly<Record<string, Region>> = mapViewMock.areas;

// 지금 위치 · 핀 하나를 가운데에 둘 때의 확대 수준(동네 하나 정도).
const NEARBY_DELTA = { latitudeDelta: 0.014, longitudeDelta: 0.012 };
// 지금 위치도 핀도 없을 때. 제주 · 강원까지 들어오는 대한민국 전체.
const KOREA_REGION: Region = {
    latitude: 36.0,
    longitude: 127.8,
    latitudeDelta: 6.0,
    longitudeDelta: 4.5,
};

const EXPANDED = 1;
const SUMMARY_HEIGHT =
    metrics.sheetHandle.paddingTop +
    metrics.sheetHandle.height +
    metrics.mapView.sheetGap +
    metrics.mapView.summaryThumbWidth / metrics.thumb.aspectRatio +
    metrics.mapView.sheetPaddingBottom;

type Start = { region: Region; fitPins: boolean };

// 처음 영역. 아직 정할 수 없으면(위치 · 장소를 읽는 중) undefined.
function startFor(
    area: string | undefined,
    location: DeviceLocation,
    view: SavedPlacesView,
): Start | undefined {
    const areaRegion = area == null ? undefined : areas[area];
    if (areaRegion != null) return { region: areaRegion, fitPins: false };
    if (location.status === "loading") return undefined;
    if (location.status === "located") {
        return {
            region: { ...location.coordinate, ...NEARBY_DELTA },
            fitPins: false,
        };
    }
    if (view.status === "loading") return undefined;
    const places = view.status === "loaded" ? view.places : [];
    const [only] = places;
    if (places.length === 1 && only != null) {
        return {
            region: {
                latitude: only.latitude,
                longitude: only.longitude,
                ...NEARBY_DELTA,
            },
            fitPins: false,
        };
    }
    return { region: KOREA_REGION, fitPins: places.length > 1 };
}

export default function MapViewScreen() {
    const navigation = useNavigation();
    const route =
        useRoute<RouteProp<{ Map: { area?: string } | undefined }, "Map">>();
    const insets = useSafeAreaInsets();
    const mapRef = useRef<MapView>(null);
    const sheetRef = useRef<BottomSheet>(null);
    const [selectedId, setSelectedId] = useState<string>();
    // 이름표는 처음 선택할 때 하나 만들고 계속 둔다. 선택이 풀리면 마지막 핀 자리에서 숨긴다.
    const [labelPinId, setLabelPinId] = useState<string>();
    const location = useDeviceLocation();
    const { view } = useSavedPlaces();

    // 처음 영역은 정할 수 있게 된 첫 렌더에 한 번 정한다(MapView 는 그때 그린다).
    const [start, setStart] = useState<Start>();
    if (start == null) {
        const next = startFor(route.params?.area, location, view);
        if (next != null) setStart(next);
    }

    // 이미 열려 있던 지도에 06 링크로 들어오면 그 동네로 옮긴다. 탭이 보인 뒤에 움직이도록 포커스 때 처리한다.
    const area = route.params?.area;
    useFocusEffect(
        useCallback(() => {
            if (area == null) return;
            const region = areas[area];
            if (region != null) mapRef.current?.animateToRegion(region);
            navigation.dispatch(CommonActions.setParams({ area: undefined }));
        }, [area, navigation]),
    );

    // 시트가 열려 있으면 시스템 뒤로 가기는 시트만 닫는다.
    useFocusEffect(
        useCallback(() => {
            const sub = BackHandler.addEventListener(
                "hardwareBackPress",
                () => {
                    if (selectedId == null) return false;
                    sheetRef.current?.close();
                    return true;
                },
            );
            return () => sub.remove();
        }, [selectedId]),
    );

    // 다른 탭으로 옮기면 선택을 푼다. 요약에서 08 로 갔다가 돌아오면 보던 핀을 그대로 둔다.
    // 화면이 떨어질 때 시트가 닫혔을 수 있어 돌아오면 기본 지점으로 다시 연다(선택이 없으면 시트가 없다).
    const keepSelectionOnBlur = useRef(false);
    useFocusEffect(
        useCallback(() => {
            keepSelectionOnBlur.current = false;
            sheetRef.current?.snapToIndex(0);
            return () => {
                if (!keepSelectionOnBlur.current) setSelectedId(undefined);
            };
        }, []),
    );

    const current =
        location.status === "located" ? location.coordinate : undefined;
    const now = new Date();
    const pins =
        view.status === "loaded"
            ? view.places.map((place) => toMapPin(place, now, current))
            : [];
    const selected = pins.find((pin) => pin.placeId === selectedId);
    const labelPin = pins.find((pin) => pin.placeId === labelPinId);

    const selectPin = (placeId: string) => {
        setSelectedId(placeId);
        setLabelPinId(placeId);
    };
    const openDetail = (pin: { personalSaveId: string; placeId: string }) => {
        keepSelectionOnBlur.current = true;
        navigation.navigate("ContentDetail", {
            personalSaveId: pin.personalSaveId,
            placeId: pin.placeId,
        });
    };
    // Android 는 핀을 눌러도 지도 onPress 가 함께 온다. 핀 누름은 무시한다.
    const pressMap = (event: MapPressEvent) => {
        if (event.nativeEvent.action === "marker-press") return;
        sheetRef.current?.close();
    };
    const locate = () => {
        if (current == null) return;
        mapRef.current?.animateToRegion({ ...current, ...NEARBY_DELTA });
    };
    // 핀 전체에 맞춘다. 핀은 좌표 위로 솟으므로 위 여백은 상태바 + 가장 큰 핀 높이다.
    // Android 의 edgePadding 은 px 다.
    const fitPins = () => {
        if (!start?.fitPins) return;
        const px = PixelRatio.getPixelSizeForLayoutSize;
        const pinHeight =
            photoMarkerSize("recent").width / metrics.thumb.aspectRatio;
        mapRef.current?.fitToCoordinates(
            pins.map((pin) => pin.coordinate),
            {
                edgePadding: {
                    top: px(insets.top + pinHeight),
                    right: px(spacing.xl),
                    bottom: px(spacing.xl),
                    left: px(spacing.xl),
                },
                animated: false,
            },
        );
    };

    return (
        <View style={styles.screen}>
            {start != null && (
                <MapView
                    ref={mapRef}
                    provider={PROVIDER_GOOGLE}
                    style={StyleSheet.absoluteFill}
                    customMapStyle={mapStyle}
                    initialRegion={start.region}
                    onMapReady={fitPins}
                    onPress={pressMap}
                    moveOnMarkerPress={false}
                    toolbarEnabled={false}
                    showsCompass={false}
                >
                    {current != null && (
                        <CurrentLocation coordinate={current} />
                    )}
                    {pins.map((pin) => (
                        <PhotoMarker
                            key={pin.placeId}
                            coordinate={pin.coordinate}
                            fade={pin.fade}
                            uri={pin.thumbUri}
                            selected={pin.placeId === selectedId}
                            onPress={() => selectPin(pin.placeId)}
                        />
                    ))}
                    {/* 10b: 선택한 핀 아래 이름표. 지도가 숨겨진 동안 마커가 빠지지 않게 opacity 로만 숨긴다. */}
                    {labelPin && (
                        <PinLabel
                            coordinate={labelPin.coordinate}
                            label={labelPin.place}
                            visible={labelPin.placeId === selectedId}
                        />
                    )}
                </MapView>
            )}

            {current != null && (
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
                        openDetail(selected);
                    }}
                    // 화면이 떨어질 때(08 push) 오는 닫힘은 무시한다. 선택은 돌아와서도 유지한다.
                    onClose={() => {
                        if (navigation.isFocused()) setSelectedId(undefined);
                    }}
                >
                    <BottomSheetView style={styles.sheetContent}>
                        <PinSummary
                            time={selected.time}
                            place={selected.place}
                            walk={selected.walk}
                            thumbUri={selected.thumbUri}
                            fade={selected.fade}
                            onPress={() => openDetail(selected)}
                        />
                    </BottomSheetView>
                </BottomSheet>
            )}
        </View>
    );
}

const styles = StyleSheet.create({
    // 지도 타일이 뜨기 전 · 지금 위치를 읽는 동안에는 땅 색이 보인다.
    screen: {
        flex: 1,
        backgroundColor: colors.bg.screen,
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
