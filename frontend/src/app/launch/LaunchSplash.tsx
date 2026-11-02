// 실행 모션 오버레이. docs/design/c-launch-motion.md §3 · §5.
// 네이티브 스플래시(자두색 + 글리프 완성 상태)와 같은 화면으로 시작해, 네이티브 스플래시를 숨기고 그 위에서 3000ms 1회 재생한다.
// 도착 화면은 이 아래에 이미 그려져 있다. 분기(세션 유무)는 모르고 앞장이 내려앉을 자리(target)만 받는다.
// 끝날 때까지 화면 전체를 덮어 아래 화면은 눌리지 않는다(00a 시작하기 포함).
import * as SplashScreen from "expo-splash-screen";
import { useEffect, useRef, useState } from "react";
import { type LayoutChangeEvent, StyleSheet, View } from "react-native";
import Animated, {
    Easing,
    useAnimatedStyle,
    useReducedMotion,
    useSharedValue,
    withDelay,
    withSequence,
    withTiming,
} from "react-native-reanimated";

import type { LaunchRect } from "../../shared/launch/launchTarget";
import { colors, metrics } from "../../shared/ui/theme";

const {
    glyphSize,
    viewBox,
    backPlates,
    frontPlate,
    dot,
    frontStep,
    pileShift,
} = metrics.launch;
const unit = glyphSize / viewBox;

// §2.2. 구간마다 따로 건다.
const EASE = Easing.bezier(0.22, 0.7, 0.2, 1);

type Segment = readonly [start: number, end: number];

// §3.4 타임라인(ms). 주석의 # 은 표의 행 번호. 표에 없는 구간은 값이 그대로다.
const TIMELINE = {
    pileOut: [450, 1140], // #2 · #3 뒷장 묶음이 흐려지며 왼쪽 아래로
    frontStep: [450, 960], // #5 앞장 S0 → S1
    dotOut: [660, 1140], // #6 점 사라짐
    frontLand: [960, 1920], // #7 앞장 S1 → S2
    backgroundOut: [1500, 2100], // #8 자두색 배경 걷힘
    screenIn: [1860, 2460], // #9 도착 화면 드러남
    frontOut: [2520, 2880], // #11 앞장 사라짐
} as const satisfies Record<string, Segment>;
const END_MS = 3000; // #12 언마운트
// §5.4 애니메이션 줄이기(와 종료 상태 알림 누름)에서는 모션 없이 크로스페이드만.
const CROSSFADE_MS = 200;

// 한 구간 = withDelay + withTiming 하나. after 는 withSequence 에서 앞 구간이 끝난 시각.
function segment([start, end]: Segment, to: number, after = 0) {
    return withDelay(
        start - after,
        withTiming(to, { duration: end - start, easing: EASE }),
    );
}

type Props = {
    // undefined 면 보고를 기다리며 시작 상태로 멈춰 있다(네이티브 스플래시가 덮고 있다).
    target: LaunchRect | null | undefined;
    // 도착 화면이 근처 · 00a 가 아닐 때(종료 상태에서 누른 알림으로 08 이 열림).
    skipMotion: boolean;
    onFinish: () => void;
};

// 오버레이의 크기와 창 안 위치. 도착 화면은 창 좌표로 보고하므로 이 위치를 빼서 오버레이 좌표로 바꾼다.
type Screen = { width: number; height: number; x: number; y: number };

export default function LaunchSplash(props: Props) {
    const root = useRef<View>(null);
    const [screen, setScreen] = useState<Screen | null>(null);

    const handleLayout = ({ nativeEvent }: LayoutChangeEvent) => {
        const { width, height } = nativeEvent.layout;
        root.current?.measureInWindow((x, y) =>
            setScreen((prev) => prev ?? { width, height, x, y }),
        );
    };

    return (
        <View
            ref={root}
            style={StyleSheet.absoluteFill}
            onLayout={handleLayout}
        >
            {screen != null && <LaunchMotion {...props} screen={screen} />}
        </View>
    );
}

// 화면 크기를 안 뒤에 마운트해 첫 프레임부터 S0 에 그린다.
function LaunchMotion({
    target: windowTarget,
    skipMotion,
    onFinish,
    screen,
}: Props & { screen: Screen }) {
    const target =
        windowTarget == null
            ? windowTarget
            : {
                  ...windowTarget,
                  left: windowTarget.left - screen.x,
                  top: windowTarget.top - screen.y,
              };
    const reducedMotion = useReducedMotion();
    const crossfadeOnly = skipMotion || reducedMotion;

    // §3.3 글리프 박스는 화면 정중앙. 네이티브 스플래시도 창 정중앙에 그린다.
    const glyphLeft = (screen.width - glyphSize) / 2;
    const glyphTop = (screen.height - glyphSize) / 2;
    const s0 = {
        left: glyphLeft + frontPlate.x * unit,
        top: glyphTop + frontPlate.y * unit,
        size: frontPlate.size * unit,
        radius: frontPlate.radius * unit,
    };

    const overlayOpacity = useSharedValue(1);
    const screenVeil = useSharedValue(1);
    const backgroundOpacity = useSharedValue(1);
    const pileOpacity = useSharedValue(1);
    const pileX = useSharedValue(0);
    const pileY = useSharedValue(0);
    const frontLeft = useSharedValue(s0.left);
    const frontTop = useSharedValue(s0.top);
    const frontWidth = useSharedValue(s0.size);
    const frontHeight = useSharedValue(s0.size);
    const frontRadius = useSharedValue(s0.radius);
    const frontRotate = useSharedValue(0);
    const frontOpacity = useSharedValue(1);
    const dotOpacity = useSharedValue(1);

    // 시작은 한 번. 시작한 뒤 target 이 바뀌어도(대기 시간이 지나 null 로 시작한 뒤 늦게 온 보고) 무시하고,
    // 프레임 · 종료 타이머는 언마운트 때만 거둔다(deps 가 바뀔 때 거두면 끝나지 않는다).
    const started = useRef(false);
    const frame = useRef<number | undefined>(undefined);
    const finishTimer = useRef<ReturnType<typeof setTimeout> | undefined>(
        undefined,
    );

    useEffect(
        () => () => {
            if (frame.current != null) cancelAnimationFrame(frame.current);
            if (finishTimer.current != null) clearTimeout(finishTimer.current);
        },
        [],
    );

    useEffect(() => {
        if (started.current) return;
        if (!crossfadeOnly && target === undefined) return;
        started.current = true;

        // 크로스페이드는 이 오버레이가 맡으므로 네이티브 스플래시는 바로 걷는다.
        // 모션은 기본 페이드(400ms)를 둔다. 0–450ms 정지 구간 안에서 끝나고, 두 글리프의 작은 어긋남을 가린다.
        if (crossfadeOnly) SplashScreen.setOptions({ duration: 0 });

        // 시작 상태가 화면에 그려진 다음 프레임에 숨기고, 같은 프레임에 타임라인을 시작한다.
        frame.current = requestAnimationFrame(() => {
            SplashScreen.hide();
            if (crossfadeOnly) {
                overlayOpacity.value = segment([0, CROSSFADE_MS], 0);
                finishTimer.current = setTimeout(onFinish, CROSSFADE_MS);
                return;
            }

            const t = TIMELINE;
            const s1 = {
                left: s0.left + frontStep.x,
                top: s0.top + frontStep.y,
            };

            pileOpacity.value = segment(t.pileOut, 0);
            pileX.value = segment(t.pileOut, pileShift.x);
            pileY.value = segment(t.pileOut, pileShift.y);
            dotOpacity.value = segment(t.dotOut, 0);
            backgroundOpacity.value = segment(t.backgroundOut, 0);
            screenVeil.value = segment(t.screenIn, 0);
            frontOpacity.value = segment(t.frontOut, 0);

            // 도착 대상이 없으면 S1 에 머물다 #11 에 사라진다(§6).
            if (target == null) {
                frontLeft.value = segment(t.frontStep, s1.left);
                frontTop.value = segment(t.frontStep, s1.top);
            } else {
                const after = t.frontStep[1];
                frontLeft.value = withSequence(
                    segment(t.frontStep, s1.left),
                    segment(t.frontLand, target.left, after),
                );
                frontTop.value = withSequence(
                    segment(t.frontStep, s1.top),
                    segment(t.frontLand, target.top, after),
                );
                frontWidth.value = segment(t.frontLand, target.width);
                frontHeight.value = segment(t.frontLand, target.height);
                frontRadius.value = segment(t.frontLand, target.radius);
                frontRotate.value = segment(t.frontLand, target.rotate);
            }
            finishTimer.current = setTimeout(onFinish, END_MS);
        });
    }, [target, crossfadeOnly]);

    const overlayStyle = useAnimatedStyle(() => ({
        opacity: overlayOpacity.value,
    }));
    const veilStyle = useAnimatedStyle(() => ({ opacity: screenVeil.value }));
    const backgroundStyle = useAnimatedStyle(() => ({
        opacity: backgroundOpacity.value,
    }));
    const pileStyle = useAnimatedStyle(() => ({
        opacity: pileOpacity.value,
        transform: [{ translateX: pileX.value }, { translateY: pileY.value }],
    }));
    const frontStyle = useAnimatedStyle(() => ({
        left: frontLeft.value,
        top: frontTop.value,
        width: frontWidth.value,
        height: frontHeight.value,
        borderRadius: frontRadius.value,
        opacity: frontOpacity.value,
        transform: [{ rotate: `${frontRotate.value}deg` }],
    }));
    const dotStyle = useAnimatedStyle(() => ({ opacity: dotOpacity.value }));

    const dotSize = dot.radius * 2 * unit;

    return (
        <Animated.View style={[StyleSheet.absoluteFill, overlayStyle]}>
            {/* #9 도착 화면 0 → 1 은 그 위를 bg/screen 으로 덮었다 걷는 것과 같다. */}
            <Animated.View
                style={[StyleSheet.absoluteFill, styles.veil, veilStyle]}
            />
            <Animated.View
                style={[
                    StyleSheet.absoluteFill,
                    styles.background,
                    backgroundStyle,
                ]}
            />
            <Animated.View
                style={[
                    styles.glyph,
                    { left: glyphLeft, top: glyphTop },
                    pileStyle,
                ]}
            >
                {backPlates.map((plate) => (
                    <View
                        key={plate.x}
                        style={[
                            styles.plate,
                            {
                                left: plate.x * unit,
                                top: plate.y * unit,
                                width: plate.size * unit,
                                height: plate.size * unit,
                                borderRadius: plate.radius * unit,
                                opacity: plate.opacity,
                            },
                        ]}
                    />
                ))}
            </Animated.View>
            <Animated.View style={[styles.plate, frontStyle]}>
                <Animated.View
                    style={[
                        styles.dot,
                        {
                            width: dotSize,
                            height: dotSize,
                            borderRadius: dotSize / 2,
                            marginLeft: -dotSize / 2,
                            marginTop: -dotSize / 2,
                        },
                        dotStyle,
                    ]}
                />
            </Animated.View>
        </Animated.View>
    );
}

const styles = StyleSheet.create({
    veil: {
        backgroundColor: colors.bg.screen,
    },
    background: {
        backgroundColor: colors.brand.primary,
    },
    glyph: {
        position: "absolute",
        width: glyphSize,
        height: glyphSize,
    },
    plate: {
        position: "absolute",
        backgroundColor: colors.bg.surface,
    },
    dot: {
        position: "absolute",
        left: `${dot.x * 100}%`,
        top: `${dot.y * 100}%`,
        backgroundColor: colors.brand.primary,
    },
});
