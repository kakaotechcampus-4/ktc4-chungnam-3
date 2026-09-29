// Google 지도 커스텀 스타일. Figma 10 지도(2103:428)의 색을 토큰으로 옮긴다.
// 땅 bg/screen, 도로 bg/surface, 물 status/info-bg, 동네 · 물 이름 text/secondary.
// 가게 · 교통 · 도로 이름은 사진 핀과 겹치지 않게 끈다.
import type { MapStyleElement } from "react-native-maps";

import { colors } from "./colors";

const OFF = [{ visibility: "off" }];

export const mapStyle: MapStyleElement[] = [
    { elementType: "geometry", stylers: [{ color: colors.bg.screen }] },
    { elementType: "labels.icon", stylers: OFF },
    {
        elementType: "labels.text.fill",
        stylers: [{ color: colors.text.secondary }],
    },
    {
        elementType: "labels.text.stroke",
        stylers: [{ color: colors.bg.screen }],
    },
    { featureType: "administrative", elementType: "geometry", stylers: OFF },
    { featureType: "poi", stylers: OFF },
    { featureType: "transit", stylers: OFF },
    {
        featureType: "road",
        elementType: "geometry",
        stylers: [{ color: colors.bg.surface }],
    },
    { featureType: "road", elementType: "labels", stylers: OFF },
    {
        featureType: "water",
        elementType: "geometry",
        stylers: [{ color: colors.status.infoBg }],
    },
];
