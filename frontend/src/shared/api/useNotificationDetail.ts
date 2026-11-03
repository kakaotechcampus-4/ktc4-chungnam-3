// 알림 상세를 읽는 hook. 알림으로 여는 장소 상세(08)가 쓴다.
// 화면이 처음 그려질 때 한 번 읽는다(읽으면 화면이 저장물 08 로 바뀐다). 404 는 notFound, 그 밖의 실패는 failed(reload 로 다시 읽기).
// 읽으면 열 화면(target)을 함께 정한다. 늦게 온 응답이 새 결과를 덮지 않게 마지막 읽기만 반영한다.
import { useCallback, useEffect, useRef, useState } from "react";

import { ApiError } from "./client";
import {
    fetchNotificationDetail,
    type NotificationDetailResponse,
} from "./notifications";

// saved: 저장물 08 로 연다(placeId 가 있으면 그 장소). notFound: 열 것이 없다(12).
export type NotificationTarget =
    | { kind: "saved"; personalSaveId: string; placeId?: string }
    | { kind: "notFound" };

export type NotificationDetailView =
    | { status: "loading" }
    | { status: "notFound" }
    | { status: "failed" }
    | {
          status: "loaded";
          detail: NotificationDetailResponse;
          target: NotificationTarget;
      };

// 첫 항목만 본다(여러 장소를 고르는 화면은 아직 없다). 항목이 없거나 PUBLIC(내 저장물이 아님)이면 notFound.
// 장소가 없으면(CONTENT) 저장물만 연다. 저장물 08 이 ready 가 아니면 저장 결과로 바꾼다.
export function toNotificationTarget(
    detail: NotificationDetailResponse,
): NotificationTarget {
    const item = detail.items[0];
    if (item == null || item.personalSaveId == null) {
        return { kind: "notFound" };
    }
    return item.place == null
        ? { kind: "saved", personalSaveId: item.personalSaveId }
        : {
              kind: "saved",
              personalSaveId: item.personalSaveId,
              placeId: item.place.placeId,
          };
}

export function useNotificationDetail(notificationId: string): {
    view: NotificationDetailView;
    reload(): void;
} {
    const [view, setView] = useState<NotificationDetailView>({
        status: "loading",
    });
    const readId = useRef(0);

    const reload = useCallback(() => {
        const id = ++readId.current;
        setView({ status: "loading" });
        fetchNotificationDetail(notificationId)
            .then((detail) => {
                if (id !== readId.current) return;
                setView({
                    status: "loaded",
                    detail,
                    target: toNotificationTarget(detail),
                });
            })
            .catch((error) => {
                if (id !== readId.current) return;
                setView({
                    status:
                        error instanceof ApiError && error.status === 404
                            ? "notFound"
                            : "failed",
                });
            });
    }, [notificationId]);

    useEffect(() => {
        reload();
    }, [reload]);

    return { view, reload };
}
