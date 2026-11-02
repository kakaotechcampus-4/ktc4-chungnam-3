// 실행 모션 오버레이. docs/design/c-launch-motion.md §3 · §5.
// 네이티브 스플래시(자두색 + 글리프 완성 상태)와 같은 화면으로 시작해 첫 프레임에 네이티브 스플래시를 숨긴다.
// 준비(폰트 · 세션 · 도착 자리 보고)를 기다리는 동안 세 장을 아래에서 위로 훑고(1단계, 반복),
// 준비되면 앞장이 "뾱" 튀어나와 도착 자리로 펼쳐진다(2단계, 1회). 분기(세션 유무)는 모르고 앞장이 펼쳐질 자리(target)만 받는다.
// 끝날 때까지 화면 전체를 덮어 아래 화면은 눌리지 않는다(00a 시작하기 포함).

// special thanks to @seolmango !!

import * as SplashScreen from "expo-splash-screen";
import { useEffect, useRef, useState } from "react";
import { type LayoutChangeEvent, StyleSheet, View } from "react-native";
import Animated, {
    cancelAnimation,
    Easing,
    type SharedValue,
    useAnimatedStyle,
    useReducedMotion,
    useSharedValue,
    withDelay,
    withRepeat,
    withSequence,
    withTiming,
    type WithTimingConfig,
} from "react-native-reanimated";

import type { LaunchRect } from "../../shared/launch/launchTarget";
import { colors, metrics } from "../../shared/ui/theme";

const {
    glyphSize,
    viewBox,
    backPlates,
    frontPlate,
    dot,
    liftY,
    sink: sinkTo,
    ring: ringStyle,
} = metrics.launch;
const unit = glyphSize / viewBox;

// §2.2. 자리를 옮기는 구간(링 · 가라앉기 · 펼치기)에 쓴다.
const EASE = Easing.bezier(0.22, 0.7, 0.2, 1);
// 훑기 · 뾱의 되튀기.
const IN_OUT = Easing.inOut(Easing.ease);
// 투명도만 바꾸는 구간(점 · 배경 · 앞장 사라짐). Reanimated withTiming 기본값과 같다.
const FADE = Easing.inOut(Easing.quad);

// §3.3 1단계 훑기(ms). 장마다 stagger 만큼 늦게 시작해 period 마다 rise 동안 들렸다가 fall 동안 내려온다.
const SCAN = { stagger: 160, rise: 264, fall: 312, period: 1200 } as const;
// 빠른 기기에서도 한 바퀴는 보인다. 이 시각에는 세 장이 모두 내려와 있다.
const SCAN_MIN_MS = 1200;

type Segment = readonly [start: number, end: number];

// §3.4 2단계 끝맺음(ms). 준비된 시각(또는 SCAN_MIN_MS)부터 잰다.
const FINISH = {
    settle: 120, // 훑기 멈추고 제자리로
    ring: [0, 396], // 앞장 둘레 링이 퍼지며 사라짐
    sink: [0, 540], // 뒷장 둘이 가라앉으며 사라짐
    dotOut: [540, 756], // 점 사라짐
    morph: [612, 1152], // 앞장 → 도착 자리
    backgroundOut: [864, 1224], // 자두색 배경 걷힘
    frontOut: [1512, 1728], // 앞장 사라짐(도착 자리에 있을 때)
    frontOutAlone: [864, 1224], // 앞장 사라짐(도착 대상 없음, 펼치지 않음)
} as const satisfies Record<string, number | Segment>;
// 뾱: [배율, ms] 를 차례로. 첫 구간만 빠르게 튀어나오고 나머지는 되튄다.
const POP = [
    [1.24, 144],
    [0.92, 108],
    [1.05, 108],
    [1, 108],
] as const;
// §5.4 애니메이션 줄이기(와 종료 상태 알림 누름)에서는 모션 없이 크로스페이드만.
const CROSSFADE_MS = 200;

function segment(
    [start, end]: Segment,
    to: number,
    easing: WithTimingConfig["easing"] = EASE,
) {
    return withDelay(start, withTiming(to, { duration: end - start, easing }));
}

type Props = {
    // undefined 면 아직 준비 전이라 훑기를 반복한다. null 이면 도착 대상 없음(펼치지 않고 사라짐).
    target: LaunchRect | null | undefined;
    // 도착 화면이 근처 · 00a 가 아닐 때(종료 상태에서 누른 알림으로 08 이 열림). 준비된 뒤에만 true 가 된다.
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

// 훑기에서 한 장이 들렸다 내려오는 반복.
function scanLoop(index: number) {
    return withDelay(
        index * SCAN.stagger,
        withRepeat(
            withSequence(
                withTiming(1, { duration: SCAN.rise, easing: IN_OUT }),
                withTiming(0, { duration: SCAN.fall, easing: IN_OUT }),
                withTiming(0, {
                    duration: SCAN.period - SCAN.rise - SCAN.fall,
                }),
            ),
            -1,
        ),
    );
}

// 뒷장 한 장. 훑기(lift)로 진해지며 들리고, 끝맺음(sink)에서 가라앉으며 사라진다.
function BackPlate({
    plate,
    left,
    top,
    lift,
    sink,
}: {
    plate: (typeof backPlates)[number];
    left: number;
    top: number;
    lift: SharedValue<number>;
    sink: SharedValue<number>;
}) {
    const style = useAnimatedStyle(() => ({
        opacity:
            (plate.opacity + (1 - plate.opacity) * lift.value) *
            (1 - sink.value),
        transform: [
            { translateY: (liftY * lift.value + sinkTo.y * sink.value) * unit },
            {
                scale:
                    (1 + (plate.bump - 1) * lift.value) *
                    (1 - (1 - sinkTo.scale) * sink.value),
            },
        ],
    }));
    return (
        <Animated.View
            style={[
                styles.plate,
                {
                    left,
                    top,
                    width: plate.size * unit,
                    height: plate.size * unit,
                    borderRadius: plate.radius * unit,
                },
                style,
            ]}
        />
    );
}

// 화면 크기를 안 뒤에 마운트해 첫 프레임부터 시작 상태로 그린다.
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

    // §3.1 글리프 박스는 화면 정중앙. 네이티브 스플래시도 창 정중앙에 그린다.
    const glyphLeft = (screen.width - glyphSize) / 2;
    const glyphTop = (screen.height - glyphSize) / 2;
    const s0 = {
        left: glyphLeft + frontPlate.x * unit,
        top: glyphTop + frontPlate.y * unit,
        size: frontPlate.size * unit,
        radius: frontPlate.radius * unit,
    };

    const overlayOpacity = useSharedValue(1);
    const backgroundOpacity = useSharedValue(1);
    const backLift0 = useSharedValue(0);
    const backLift1 = useSharedValue(0);
    const frontLift = useSharedValue(0);
    const sink = useSharedValue(0);
    const pop = useSharedValue(1);
    const ring = useSharedValue(0);
    const morph = useSharedValue(0);
    const land = useSharedValue<LaunchRect | null>(null);
    const frontOpacity = useSharedValue(1);
    const dotOpacity = useSharedValue(1);
    const lifts = [backLift0, backLift1, frontLift];

    const scanStartedAt = useRef(0);
    const finishing = useRef(false);
    const frame = useRef<number | undefined>(undefined);
    const timers = useRef<ReturnType<typeof setTimeout>[]>([]);

    // 첫 프레임: 네이티브 스플래시를 페이드 없이 걷고 훑기를 시작한다.
    // 이 오버레이가 같은 화면이라 바로 바꿔도 티가 나지 않는다. 페이드를 두면 정지된 글리프가 들리는 장 위에 겹쳐 보인다.
    useEffect(() => {
        SplashScreen.setOptions({ duration: 0 });
        scanStartedAt.current = Date.now();
        frame.current = requestAnimationFrame(() => {
            SplashScreen.hide();
            if (reducedMotion) return;
            lifts.forEach((lift, index) => {
                lift.value = scanLoop(index);
            });
        });
        return () => {
            if (frame.current != null) cancelAnimationFrame(frame.current);
            timers.current.forEach(clearTimeout);
        };
    }, []);

    // 준비되면 끝맺음을 한 번. 시작한 뒤 target 이 바뀌어도(대기 시간이 지나 null 로 시작한 뒤 늦게 온 보고) 무시한다.
    useEffect(() => {
        if (finishing.current) return;
        if (!skipMotion && target === undefined) return;
        finishing.current = true;
        const later = (run: () => void, ms: number) => {
            timers.current.push(setTimeout(run, ms));
        };

        if (skipMotion || reducedMotion) {
            lifts.forEach((lift) => cancelAnimation(lift));
            overlayOpacity.value = withTiming(0, {
                duration: CROSSFADE_MS,
                easing: EASE,
            });
            later(onFinish, CROSSFADE_MS);
            return;
        }

        const landing = target ?? null;
        const elapsed = Date.now() - scanStartedAt.current;
        later(
            () => {
                const f = FINISH;
                lifts.forEach((lift) => {
                    cancelAnimation(lift);
                    lift.value = withTiming(0, {
                        duration: f.settle,
                        easing: IN_OUT,
                    });
                });
                pop.value = withSequence(
                    ...POP.map(([scale, duration], index) =>
                        withTiming(scale, {
                            duration,
                            easing:
                                index === 0 ? Easing.out(Easing.quad) : IN_OUT,
                        }),
                    ),
                );
                ring.value = segment(f.ring, 1);
                sink.value = segment(f.sink, 1);
                dotOpacity.value = segment(f.dotOut, 0, FADE);
                backgroundOpacity.value = segment(f.backgroundOut, 0, FADE);
                if (landing != null) {
                    land.value = landing;
                    morph.value = segment(f.morph, 1);
                    frontOpacity.value = segment(f.frontOut, 0, FADE);
                    later(onFinish, f.frontOut[1]);
                } else {
                    frontOpacity.value = segment(f.frontOutAlone, 0, FADE);
                    later(onFinish, f.frontOutAlone[1]);
                }
            },
            Math.max(0, SCAN_MIN_MS - elapsed),
        );
    }, [target, skipMotion, reducedMotion]);

    const overlayStyle = useAnimatedStyle(() => ({
        opacity: overlayOpacity.value,
    }));
    const backgroundStyle = useAnimatedStyle(() => ({
        opacity: backgroundOpacity.value,
    }));
    const ringAnimated = useAnimatedStyle(() => ({
        opacity: ring.value > 0 ? ringStyle.opacity * (1 - ring.value) : 0,
        transform: [{ scale: ringStyle.scaleFrom + ring.value }],
    }));
    // 앞장: 훑기 · 뾱은 scale, 펼치기는 자리 · 크기 · 라운드 · 기울기를 직접 바꾼다(scale 이면 라운드가 찌그러진다).
    const frontStyle = useAnimatedStyle(() => {
        const m = morph.value;
        const to = land.value;
        const lerp = (from: number, end: number | undefined) =>
            end == null ? from : from + (end - from) * m;
        return {
            left: lerp(s0.left, to?.left),
            top: lerp(s0.top, to?.top),
            width: lerp(s0.size, to?.width),
            height: lerp(s0.size, to?.height),
            borderRadius: lerp(s0.radius, to?.radius),
            opacity: frontOpacity.value,
            transform: [
                { translateY: liftY * frontLift.value * unit },
                {
                    scale:
                        pop.value *
                        (1 + (frontPlate.bump - 1) * frontLift.value),
                },
                { rotate: `${lerp(0, to?.rotate)}deg` },
            ],
        };
    });
    const dotStyle = useAnimatedStyle(() => ({ opacity: dotOpacity.value }));

    const dotSize = dot.radius * 2 * unit;

    return (
        <Animated.View style={[StyleSheet.absoluteFill, overlayStyle]}>
            <Animated.View
                style={[
                    StyleSheet.absoluteFill,
                    styles.background,
                    backgroundStyle,
                ]}
            />
            {backPlates.map((plate, index) => (
                <BackPlate
                    key={plate.x}
                    plate={plate}
                    left={glyphLeft + plate.x * unit}
                    top={glyphTop + plate.y * unit}
                    lift={lifts[index]}
                    sink={sink}
                />
            ))}
            <Animated.View
                style={[
                    styles.ring,
                    {
                        left: s0.left,
                        top: s0.top,
                        width: s0.size,
                        height: s0.size,
                        borderRadius: s0.radius,
                    },
                    ringAnimated,
                ]}
            />
            <Animated.View style={[styles.plate, styles.front, frontStyle]}>
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
    background: {
        backgroundColor: colors.brand.primary,
    },
    plate: {
        position: "absolute",
        backgroundColor: colors.bg.surface,
    },
    front: {
        overflow: "hidden",
    },
    ring: {
        position: "absolute",
        borderWidth: ringStyle.width,
        borderColor: colors.bg.surface,
    },
    dot: {
        position: "absolute",
        left: `${dot.x * 100}%`,
        top: `${dot.y * 100}%`,
        backgroundColor: colors.brand.primary,
    },
});
