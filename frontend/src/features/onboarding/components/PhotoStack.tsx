// 기울여 겹친 사진 일러스트(00a 2175:683 · 00b 2175:1034). 바랜 사진 위에 되살아난 사진이 올라온다.
// 사진은 Thumb 를 쓰고, 위치 · 각도는 metrics.onboarding.{intro|login} 이다. 뒤에서 앞 순서로 그린다.
import { StyleSheet, View } from "react-native";

import Thumb, { type ThumbFade } from "../../../shared/ui/Thumb";
import { effects, metrics } from "../../../shared/ui/theme";

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
};

export default function PhotoStack({ layout, photos, raiseFront }: Props) {
    return (
        <View
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
