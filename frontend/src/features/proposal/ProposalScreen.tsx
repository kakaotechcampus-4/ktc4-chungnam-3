// 근처 탭(01 · 06 · 06b). 지금 위치(useDeviceLocation) 근처의 내 장소(useSavedPlaces)를 꺼내 보여준다.
// 반경 · 묶음 규칙은 nearbyView. 헤더는 스크롤 영역 밖에 고정하고 지금 위치의 동네 이름(useAreaName)을 보인다. 하단 inset 은 하단 바가 맡는다.
// 위치 · 동의 · 장소를 읽는 동안은 헤더만, 장소를 처음 읽다가 실패하면 기억 탭과 같은 다시 불러오기 문구.
// 위치 정보 이용 동의가 없거나(agreed 가 아님), 위치 권한이 없거나(off), 위치를 받지 못하면(unknown) 06b(nearbyGate).
// 반경 안에 없으면 06. 필터 칩은 카테고리가 응답에 없어 숨긴다.
// 06b "위치 켜기": 동의가 없으면 동의 시트(outdated 면 약관이 바뀌었다는 제목). 동의를 저장하면 시트를 닫고
// 같은 누름 안에서 위치 단계(권한 요청 · 앱 설정 · 위치 설정 · 다시 읽기)로 이어 간다(00d 와 같다). "나중에" · 바깥 닫힘은 06b 에 머문다.
// 실행 모션: 첫 "지금" 카드가 앞장이 내려앉을 자리다. 06 · 06b · 실패면 도착 대상 없음(null), 읽는 동안은 보고하지 않는다.
import { useNavigation } from "@react-navigation/native";
import { useEffect, useRef, useState } from "react";
import {
    Linking,
    Pressable,
    ScrollView,
    StyleSheet,
    Text,
    View,
} from "react-native";

import { setLocationConsent } from "../../shared/api/consents";
import { useLocationConsent } from "../../shared/api/useLocationConsent";
import { useSavedPlaces } from "../../shared/api/useSavedPlaces";
import { reportLaunchTarget } from "../../shared/launch/launchTarget";
import {
    canAskForegroundLocation,
    isLocationServicesEnabled,
    openLocationSourceSettings,
    requestForegroundLocation,
} from "../../shared/permissions/location";
import { notifyLocationSettingsChanged } from "../../shared/permissions/locationSettings";
import { useAreaName } from "../../shared/permissions/useAreaName";
import { useDeviceLocation } from "../../shared/permissions/useDeviceLocation";
import LocationConsentSheet, {
    type LocationConsentSheetStatus,
} from "../../shared/ui/LocationConsentSheet";
import ScreenHeader from "../../shared/ui/ScreenHeader";
import { colors, metrics, spacing, typography } from "../../shared/ui/theme";
import MemoryCard from "./components/MemoryCard";
import NearbyCard from "./components/NearbyCard";
import NearbyEmpty from "./components/NearbyEmpty";
import NearbyOff from "./components/NearbyOff";
import {
    locationAction,
    nearbyGate,
    recentThumbUris,
    toNearbyView,
} from "./nearbyView";

export default function ProposalScreen() {
    const navigation = useNavigation();
    const location = useDeviceLocation();
    const consent = useLocationConsent();
    const { view, reload } = useSavedPlaces();
    const gate = nearbyGate(location.status, consent);
    const current =
        location.status === "located" ? location.coordinate : undefined;
    const area = useAreaName(current);

    const openDetail = (item: { personalSaveId: string; placeId: string }) =>
        navigation.navigate("ContentDetail", {
            personalSaveId: item.personalSaveId,
            placeId: item.placeId,
        });
    const openSettings = () => navigation.navigate("Settings");
    // 06 "{장소} 기억 보기". 지도 탭이 그 장소를 가운데에 두고 핀을 고른다.
    const openOnMap = (placeId: string) =>
        navigation.navigate("Main", { screen: "Map", params: { placeId } });

    // 06b "위치 켜기". 권한 창 · 설정 화면 · 동의 저장에서 돌아오면 위치 · 동의 hook 이 다시 읽는다
    // (권한 요청 · 동의 저장은 위치 설정 신호, 설정 화면은 앱이 앞으로 올 때).
    const [busy, setBusy] = useState(false);
    const pending = useRef(false);
    const [sheetVisible, setSheetVisible] = useState(false);
    const [sheetStatus, setSheetStatus] =
        useState<LocationConsentSheetStatus>("default");
    // 시트를 열 때의 값. 닫히는 동안 제목이 바뀌지 않게 유지한다.
    const [termsChanged, setTermsChanged] = useState(false);
    const saving = useRef(false);

    // 동의를 읽지 못했으면(failed) 게이트가 없으므로 동의 단계도 건너뛴다.
    const consentAgreed =
        consent.status !== "loaded" || consent.state === "agreed";
    const step = async (agreed: boolean) => {
        const status = location.status;
        const action = locationAction({
            consentAgreed: agreed,
            location:
                status === "located" || status === "unknown" ? status : "off",
            canAskAgain:
                status === "off" ? await canAskForegroundLocation() : false,
            servicesEnabled:
                status === "unknown"
                    ? await isLocationServicesEnabled()
                    : false,
        });
        if (action === "openConsentSheet") {
            setTermsChanged(
                consent.status === "loaded" && consent.state === "outdated",
            );
            setSheetStatus("default");
            setSheetVisible(true);
        } else if (action === "requestPermission") {
            await requestForegroundLocation();
        } else if (action === "openAppSettings") {
            await Linking.openSettings();
        } else if (action === "openLocationSettings") {
            await openLocationSourceSettings();
        } else if (action === "reread") {
            notifyLocationSettingsChanged();
        }
    };
    const runStep = async (agreed: boolean) => {
        if (pending.current) return;
        pending.current = true;
        setBusy(true);
        try {
            await step(agreed);
        } finally {
            pending.current = false;
            setBusy(false);
        }
    };
    // 저장이 실패하면(목 Error · ApiError 모두) 시트를 그대로 두고 오류를 보인다. 다시 누르면 다시 저장한다.
    const agree = async () => {
        if (saving.current) return;
        saving.current = true;
        setSheetStatus("saving");
        try {
            await setLocationConsent(
                true,
                consent.status === "loaded" ? consent.termsVersion : null,
            );
        } catch {
            setSheetStatus("error");
            return;
        } finally {
            saving.current = false;
        }
        setSheetVisible(false);
        await runStep(true);
    };
    const closeSheet = () => {
        setSheetVisible(false);
        setSheetStatus("default");
    };

    const nearby =
        gate === "on" && current != null && view.status === "loaded"
            ? toNearbyView(view.places, current, new Date())
            : undefined;
    // 첫 카드가 없다고 정해진 상태. 읽는 동안은 정하지 않는다.
    const noLaunchCard =
        gate === "off" ||
        (gate === "on" && view.status === "failed") ||
        nearby?.kind === "empty";
    useEffect(() => {
        if (noLaunchCard) reportLaunchTarget(null);
    }, [noLaunchCard]);

    if (gate === "off") {
        return (
            <View style={styles.screen}>
                <ScreenHeader
                    location="위치를 쓰지 않는 중"
                    onSettingsPress={openSettings}
                />
                <ScrollView contentContainerStyle={styles.content}>
                    <NearbyOff
                        stackThumbUris={
                            view.status === "loaded"
                                ? recentThumbUris(view.places)
                                : []
                        }
                        onTurnOn={() => runStep(consentAgreed)}
                        busy={busy}
                    />
                </ScrollView>
                <LocationConsentSheet
                    visible={sheetVisible}
                    status={sheetStatus}
                    onAgree={agree}
                    onLater={closeSheet}
                    onDismiss={closeSheet}
                    termsChanged={termsChanged}
                />
            </View>
        );
    }

    const header = (
        <ScreenHeader
            location={area == null ? null : `지금 ${area}`}
            onSettingsPress={openSettings}
        />
    );

    if (gate === "on" && view.status === "failed") {
        return (
            <View style={styles.screen}>
                {header}
                <Pressable
                    accessibilityRole="button"
                    onPress={reload}
                    style={styles.intro}
                >
                    <Text style={styles.caption}>
                        확인하지 못했어요. 눌러서 다시 불러와요
                    </Text>
                </Pressable>
            </View>
        );
    }

    if (nearby == null) {
        return <View style={styles.screen}>{header}</View>;
    }

    if (nearby.kind === "empty") {
        const { nearest } = nearby;
        return (
            <View style={styles.screen}>
                {header}
                <ScrollView contentContainerStyle={styles.content}>
                    <NearbyEmpty
                        nearest={nearest}
                        stackThumbUris={nearby.stackThumbUris}
                        onOpenNearest={() => {
                            if (nearest != null) openOnMap(nearest.placeId);
                        }}
                    />
                </ScrollView>
            </View>
        );
    }

    // 제목 숫자는 카드 수가 아니라 반경 안 장소 수(total)다.
    const { total, now, groups } = nearby;

    return (
        <View style={styles.screen}>
            {header}
            <ScrollView contentContainerStyle={styles.content}>
                <View style={styles.intro}>
                    <Text style={styles.heading}>
                        잊고 있던 곳 {total}개를 찾았어요
                    </Text>
                    <Text style={styles.caption}>
                        가까이 갈수록 사진에 색이 돌아와요
                    </Text>
                </View>

                <View style={styles.list}>
                    <NearbyCard
                        now={`지금 ${now.walk}`}
                        time={now.time}
                        place={now.place}
                        summary={now.summary}
                        thumbUri={now.thumbUri}
                        fade={now.fade}
                        onPress={() => openDetail(now)}
                        launchTarget
                    />
                    {groups.map((group) => (
                        <View key={group[0]?.fade} style={styles.group}>
                            {group.map((item) => (
                                <MemoryCard
                                    key={item.placeId}
                                    time={item.time}
                                    place={item.place}
                                    meta={item.walk}
                                    summary={item.summary}
                                    thumbUri={item.thumbUri}
                                    fade={item.fade}
                                    onPress={() => openDetail(item)}
                                />
                            ))}
                        </View>
                    ))}
                </View>
            </ScrollView>
        </View>
    );
}

const styles = StyleSheet.create({
    screen: {
        flex: 1,
    },
    // 목록 끝이 하단 바에 붙지 않게 한다. Figma 는 목록이 잘려 있어 값이 없다.
    content: {
        paddingBottom: spacing.lg,
    },
    intro: {
        paddingTop: metrics.proposal.introPaddingTop,
        paddingHorizontal: spacing.lg,
        gap: metrics.proposal.introGap,
    },
    caption: {
        ...typography.captionMeta,
        color: colors.text.secondary,
    },
    heading: {
        ...typography.headingScreen,
        color: colors.text.primary,
    },
    // 필터 칩이 빠져 소개 아래 간격이 칩 줄(위 lg · 아래 xs)을 대신한다.
    list: {
        gap: spacing.xl,
        paddingTop: spacing.lg + metrics.proposal.listPaddingTop,
        paddingHorizontal: spacing.lg,
    },
    group: {
        gap: spacing.md,
    },
});
