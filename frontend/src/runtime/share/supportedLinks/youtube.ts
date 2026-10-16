// YouTube 링크 파서. MVP 는 Shorts 만 받는다(서버 파서보다 좁다).
// 허용 규칙은 이 파일 위쪽 상수에만 둔다. youtu.be · /watch · /live · /embed 는 실제 공유 형식을 기기에서 확인한 뒤 넓힌다.
// 전역 URL 은 RN · Expo 구현마다 동작이 달라 쓰지 않고 정규식으로 나눈다.

import type { LinkParser, SharedLink } from "./index";

// 정확히 일치해야 한다(접미사 일치 금지). 소문자로 비교한다.
const ALLOWED_HOSTS: ReadonlySet<string> = new Set([
    "youtube.com",
    "www.youtube.com",
    "m.youtube.com",
]);

// /shorts/{11자 id}, 끝 슬래시 허용.
const SHORTS_PATH = /^\/shorts\/([A-Za-z0-9_-]{11})\/?$/;

// 스킴 · host · 경로. 쿼리 · 해시는 버린다.
const URL_PARTS = /^https?:\/\/([^/?#]*)([^?#]*)/i;

export const youtubeParser: LinkParser = {
    platform: "YOUTUBE",
    parse(url: string): SharedLink | null {
        const parts = URL_PARTS.exec(url);
        if (parts == null || !ALLOWED_HOSTS.has(parts[1].toLowerCase())) {
            return null;
        }
        const shorts = SHORTS_PATH.exec(parts[2]);
        if (shorts == null) return null;
        const videoId = shorts[1];
        // 공유자 추적 파라미터(si · is · feature 등)는 서버로 보내지 않는다.
        return {
            platform: "YOUTUBE",
            videoId,
            url: `https://www.youtube.com/shorts/${videoId}`,
        };
    },
};
