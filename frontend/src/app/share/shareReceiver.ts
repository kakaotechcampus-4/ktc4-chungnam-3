// 공유 받은 텍스트의 처리 흐름과 공유 받음 시트(C/ShareReceivedSheet) 상태. 흐름 메모 2212:1256.
// 실제 공유(다음 작업)와 개발용 딥링크가 모두 receiveSharedText 를 부른다. 시트는 ShareReceiverHost 가 띄운다.
// signedIn 이 아니면(세션 없음 · 온보딩 중) 링크를 대기열에만 넣고 시트는 띄우지 않는다. 링크가 없으면 아무것도 하지 않는다.
// signedIn 이면: 링크 없음 → notShorts. 있으면 saving → 저장. 201 saved, 200 alreadySaved, 400 notShorts,
// 그 밖의 실패(응답 없음 · 401 · 5xx · ApiError 가 아닌 예외)는 대기열에 넣고 queued.
// 저장이 성공하면 대기열도 이어서 보낸다(흐름 메모 3 "다음 공유 때"). 기다리지 않는다.
// 시트가 떠 있는 동안 새 공유가 오면 새 결과로 바꾼다. 앞선 공유의 늦은 결과는 버린다.
import { useSyncExternalStore } from "react";

import { drain, enqueue } from "../../runtime/background/pendingQueue";
import {
    findSharedLink,
    getThumbnailUrl,
} from "../../runtime/share/supportedLinks";
import { ApiError } from "../../shared/api/client";
import { savePersonalSave } from "../../shared/api/personalSaves";
import { getAuthState } from "../../shared/auth/session";

// thumbnailUrl 은 링크를 찾은 상태에만 있다. 등록되지 않은 플랫폼이면 null(자리표시).
export type ShareSheetState =
    | {
          kind: "saving" | "saved" | "alreadySaved" | "queued";
          thumbnailUrl: string | null;
      }
    | { kind: "notShorts" };

let sheet: ShareSheetState | null = null;
// 마지막으로 받은 공유의 번호.
let latest = 0;
const listeners = new Set<() => void>();

function setSheet(next: ShareSheetState | null) {
    sheet = next;
    listeners.forEach((listener) => listener());
}

function subscribe(listener: () => void) {
    listeners.add(listener);
    return () => {
        listeners.delete(listener);
    };
}

// null 이면 시트가 없다.
export function useShareSheetState(): ShareSheetState | null {
    return useSyncExternalStore(subscribe, () => sheet);
}

export function closeShareSheet(): void {
    setSheet(null);
}

export async function receiveSharedText(text: string): Promise<void> {
    const id = ++latest;
    const link = findSharedLink(text);
    if (getAuthState().status !== "signedIn") {
        if (link != null) await enqueue(link);
        return;
    }
    if (link == null) {
        setSheet({ kind: "notShorts" });
        return;
    }

    const thumbnailUrl = getThumbnailUrl(link);
    setSheet({ kind: "saving", thumbnailUrl });
    let next: ShareSheetState;
    try {
        const { created } = await savePersonalSave(link.url);
        next = { kind: created ? "saved" : "alreadySaved", thumbnailUrl };
        drain();
    } catch (error) {
        // 서버가 링크를 거절했다(흐름 메모 4). 다시 보내도 같다.
        if (error instanceof ApiError && error.status === 400) {
            next = { kind: "notShorts" };
        } else {
            await enqueue(link);
            next = { kind: "queued", thumbnailUrl };
        }
    }
    if (id === latest) setSheet(next);
}
