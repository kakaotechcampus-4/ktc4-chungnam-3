// 기억 탭 저장물 목록. 첫 페이지(20개)를 읽고, loadMore 로 nextCursor 다음 페이지를 이어 붙인다.
// 탭에 포커스될 때와, 포커스된 동안 앱이 다시 앞으로 올 때 다시 읽는다(백엔드 합의: 보관함 진입 · 앱 복귀 시 재조회).
// 다시 읽을 때는 이미 읽은 개수만큼(20~100) 한 번에 읽어 통째로 바꾼다. 보이던 목록은 응답이 올 때까지 그대로 둔다.
// 처음 읽기가 실패하면 failed, 다시 읽기 · 다음 페이지가 실패하면 목록을 그대로 둔다.
// 늦게 온 응답이 새 결과를 덮지 않게 마지막 읽기만 반영한다. 다음 페이지는 그 사이 다시 읽었으면 버린다.
import { useFocusEffect } from "@react-navigation/native";
import { useCallback, useRef, useState } from "react";
import { AppState } from "react-native";

import {
    fetchPersonalSaves,
    type PersonalSaveItemResponse,
} from "../../shared/api/personalSaves";

const PAGE_SIZE = 20;
const MAX_PAGE_SIZE = 100;

type Pages = {
    status: "loading" | "failed" | "ready";
    items: PersonalSaveItemResponse[];
    nextCursor: string | null;
};

export type ArchivePages = {
    status: Pages["status"];
    items: readonly PersonalSaveItemResponse[];
    hasMore: boolean;
    loadMore(): void;
    reload(): void;
};

export function useArchivePages(): ArchivePages {
    const [pages, setPages] = useState<Pages>({
        status: "loading",
        items: [],
        nextCursor: null,
    });
    const current = useRef(pages);
    current.current = pages;
    const readId = useRef(0);
    const loadingMore = useRef(false);

    const reload = useCallback(() => {
        const id = ++readId.current;
        const { status, items } = current.current;
        if (status === "failed")
            setPages({ ...current.current, status: "loading" });
        const size = Math.min(Math.max(items.length, PAGE_SIZE), MAX_PAGE_SIZE);
        fetchPersonalSaves({ size })
            .then((page) => {
                if (id !== readId.current) return;
                setPages({
                    status: "ready",
                    items: page.items,
                    nextCursor: page.nextCursor,
                });
            })
            .catch(() => {
                if (id !== readId.current) return;
                if (current.current.status !== "ready") {
                    setPages({ status: "failed", items: [], nextCursor: null });
                }
            });
    }, []);

    const loadMore = useCallback(() => {
        const { status, nextCursor } = current.current;
        if (status !== "ready" || nextCursor == null || loadingMore.current) {
            return;
        }
        const id = readId.current;
        loadingMore.current = true;
        fetchPersonalSaves({ size: PAGE_SIZE, cursor: nextCursor })
            .then((page) => {
                if (id !== readId.current) return;
                setPages((prev) => ({
                    status: "ready",
                    items: [...prev.items, ...page.items],
                    nextCursor: page.nextCursor,
                }));
            })
            .catch(() => {})
            .finally(() => {
                loadingMore.current = false;
            });
    }, []);

    useFocusEffect(
        useCallback(() => {
            reload();
            const subscription = AppState.addEventListener(
                "change",
                (state) => {
                    if (state === "active") reload();
                },
            );
            return () => subscription.remove();
        }, [reload]),
    );

    return {
        status: pages.status,
        items: pages.items,
        hasMore: pages.nextCursor != null,
        loadMore,
        reload,
    };
}
