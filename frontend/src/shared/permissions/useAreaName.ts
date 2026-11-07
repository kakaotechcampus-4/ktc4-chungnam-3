// 지금 위치의 동네 이름 hook. 근처 탭 헤더가 쓴다. 좌표가 바뀔 때마다 역지오코딩한다(reverseGeocodeArea).
// 다시 찾는 동안은 이전 이름을 둔다(헤더가 깜박이지 않게). 좌표가 없거나 찾지 못하면 null(헤더가 숨긴다).
// 목 모드(API 주소 없음)는 역지오코딩하지 않고 목 동네 이름(nearbyMock, MOCK_SCENARIO.nearbyEmpty 면 봉명동)이다.
import { useEffect, useState } from "react";

import { isApiConfigured } from "../api/config";
import { MOCK_SCENARIO, nearbyMock } from "../api/mock";
import type { LatLng } from "../geo/distanceMeters";
import { reverseGeocodeArea } from "./location";

export function useAreaName(coordinate: LatLng | undefined): string | null {
    const [name, setName] = useState<string | null>(null);
    const latitude = coordinate?.latitude;
    const longitude = coordinate?.longitude;

    useEffect(() => {
        if (!isApiConfigured) return;
        if (latitude == null || longitude == null) {
            setName(null);
            return;
        }
        let active = true;
        reverseGeocodeArea({ latitude, longitude }).then((found) => {
            if (active) setName(found);
        });
        return () => {
            active = false;
        };
    }, [latitude, longitude]);

    if (!isApiConfigured) {
        if (coordinate == null) return null;
        return MOCK_SCENARIO.nearbyEmpty
            ? nearbyMock.empty.area
            : nearbyMock.area;
    }
    return name;
}
