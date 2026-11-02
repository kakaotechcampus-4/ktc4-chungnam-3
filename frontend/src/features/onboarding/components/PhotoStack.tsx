// 기울여 겹친 사진 일러스트(00a 2175:683 · 00b 2175:1034). 바랜 사진 위에 되살아난 사진이 올라온다.
// 사진은 Thumb 를 쓰고, 위치 · 각도는 metrics.onboarding.{intro|login} 이다. 뒤에서 앞 순서로 그린다.
import { StyleSheet, View } from "react-native";

import { useLaunchTargetMeasure } from "../../../shared/launch/launchTarget";
import Thumb, { type ThumbFade } from "../../../shared/ui/Thumb";
import { effects, metrics, radius } from "../../../shared/ui/theme";

type Layout = {
    width: number;
    height: number;
    photos: readonly {
        cx: number;
        cy: number;
        width: number;
        rotate: string;
    }[];
};

type Props = {
    layout: Layout;
    photos: readonly { uri: string; fade: ThumbFade }[];
    // 맨 앞 사진에 그림자와 Shorts 표식을 둔다(00a).
    raiseFront?: boolean;
    // 맨 앞 사진이 실행 모션의 앞장이 내려앉을 자리다(00a).
    launchTarget?: boolean;
};

export default function PhotoStack({
    layout,
    photos,
    raiseFront,
    launchTarget,
}: Props) {
    // 사진은 기울어 있어 직접 재면 회전된 꼭짓점이 나온다. 기울지 않은 틀을 재고, 그릴 때와 같은 식으로 맨 앞 사진 자리를 구한다.
    const front = layout.photos[photos.length - 1];
    const frontHeight = front.width / metrics.thumb.aspectRatio;
    const measure = useLaunchTargetMeasure(({ x, y }) => ({
        left: x + front.cx - front.width / 2,
        top: y + front.cy - frontHeight / 2,
        width: front.width,
        height: frontHeight,
        // Thumb 의 라운드와 같다.
        radius: radius.md,
        rotate: parseFloat(front.rotate),
    }));

    return (
        <View
            ref={launchTarget ? measure.ref : undefined}
            onLayout={launchTarget ? measure.onLayout : undefined}
            style={[
                styles.frame,
                { width: layout.width, height: layout.height },
            ]}
        >
            {photos.map((photo, index) => {
                const place = layout.photos[index];
                const height = place.width / metrics.thumb.aspectRatio;
                const front = raiseFront && index === photos.length - 1;
                return (
                    <Thumb
                        key={photo.uri + index}
                        uri={photo.uri}
                        fade={photo.fade}
                        showSource={front}
                        style={[
                            styles.photo,
                            front && styles.raised,
                            {
                                left: place.cx - place.width / 2,
                                top: place.cy - height / 2,
                                width: place.width,
                                transform: [{ rotate: place.rotate }],
                            },
                        ]}
                    />
                );
            })}
        </View>
    );
}

const styles = StyleSheet.create({
    frame: {
        alignSelf: "center",
        overflow: "hidden",
    },
    photo: {
        position: "absolute",
    },
    raised: {
        boxShadow: effects.elevationLow,
    },
});
