// 저장물 목록(최신 저장순)을 기억 탭 앨범 섹션으로 나눈다. 순수 함수.
// 바램(shared/ui/savedAt 의 fadeFor) · 섹션은 저장 시각에서 지난 시간으로 정한다. 7일 미만 recent "이번 주", 30일 미만 weeks "지난 몇 주",
// 30일 이상 months 이고 섹션은 계절로 나눈다(3~5 봄, 6~8 여름, 9~11 가을, 12~2 겨울. 12월은 다음 해 겨울).
// 계절 섹션 제목의 해는 지금 해와 비교해 "올해" · "지난해" · "{YYYY}년" 이다. 날짜는 기기 시간대로 본다.
// 셀 이름: ready 는 목록에 장소명이 없어 영상 제목을 따옴표 없이 쓴다(임시). 미확정은 “제목”.
// 제목이 없으면 failed 는 "불러오지 못한 영상"(흐름 메모 2106:781), 그 밖은 "제목 없는 영상".
// 미확정은 저장 결과(02~05), ready 는 장소 상세(08)로 연다.
import type { SaveState } from "../../domain/saveState";
import { toSaveStateFromItem } from "../../shared/api/mappers/personalSaveMapper";
import type { PersonalSaveItemResponse } from "../../shared/api/personalSaves";
import { youtubeThumbnailUrl } from "../../shared/external-links/youtubeLinks";
import type { BadgeStatus } from "../../shared/ui/Badge";
import { fadeFor } from "../../shared/ui/savedAt";
import type { ThumbFade } from "../../shared/ui/Thumb";

export type AlbumItem = {
    personalSaveId: string;
    label: string;
    thumbUri: string;
    fade: ThumbFade;
    status?: BadgeStatus;
    opens: "result" | "detail";
};

export type AlbumSection = {
    key: string;
    title: string;
    items: AlbumItem[];
};

const SEASONS = ["겨울", "봄", "여름", "가을"] as const;

const BADGE: Record<SaveState["kind"], BadgeStatus | undefined> = {
    analyzing: "analyzing",
    failed: "failed",
    noPlace: "noPlace",
    ready: undefined,
};

export function toAlbumSections(
    items: readonly PersonalSaveItemResponse[],
    now: Date,
): AlbumSection[] {
    const sections: AlbumSection[] = [];
    for (const item of items) {
        const savedAt = new Date(item.savedAt);
        const fade = fadeFor(savedAt, now);
        const { key, title } =
            fade === "recent"
                ? { key: "recent", title: "이번 주" }
                : fade === "weeks"
                  ? { key: "weeks", title: "지난 몇 주" }
                  : seasonSection(savedAt, now);
        const state = toSaveStateFromItem(item);
        const albumItem: AlbumItem = {
            personalSaveId: item.personalSaveId,
            label: albumLabel(state, item.title),
            thumbUri: youtubeThumbnailUrl(item.videoId),
            fade,
            status: BADGE[state.kind],
            opens: state.kind === "ready" ? "detail" : "result",
        };
        // 최신 저장순이라 같은 섹션은 이어서 온다.
        const last = sections[sections.length - 1];
        if (last?.key === key) last.items.push(albumItem);
        else sections.push({ key, title, items: [albumItem] });
    }
    return sections;
}

export function seasonSection(
    savedAt: Date,
    now: Date,
): { key: string; title: string } {
    const month = savedAt.getMonth() + 1;
    const year =
        month === 12 ? savedAt.getFullYear() + 1 : savedAt.getFullYear();
    const season = SEASONS[Math.floor((month % 12) / 3)];
    const ago = now.getFullYear() - year;
    // 12월 저장물을 12월에 보면 해가 지금보다 크다. 올해로 본다.
    const yearLabel = ago <= 0 ? "올해" : ago === 1 ? "지난해" : `${year}년`;
    return { key: `${year}-${season}`, title: `${yearLabel} ${season}` };
}

// 빈 문자열 제목은 없는 것으로 본다.
export function albumLabel(state: SaveState, title: string | null): string {
    if (title == null || title === "") {
        return state.kind === "failed"
            ? "불러오지 못한 영상"
            : "제목 없는 영상";
    }
    return state.kind === "ready" ? title : `“${title}”`;
}
