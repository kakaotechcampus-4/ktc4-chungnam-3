// 저장 시각으로 정하는 화면 값. 순수 함수. 기억 탭 · 저장 결과 · 장소 상세가 같이 쓴다.
// 바램: 7일 미만 recent, 30일 미만 weeks, 그 이상 months (Figma 2061:445 · 2061:632).
// 지난 시간: 1분 미만 "방금", 1시간 미만 "N분 전", 1일 미만 "N시간 전", 7일 미만 "N일 전",
// 30일 미만 "N주 전", 365일 미만 "N개월 전"(30일 단위), 그 이상 "N년 전"(365일 단위).
import type { ThumbFade } from "./Thumb";

const MINUTE_MS = 60 * 1000;
const HOUR_MS = 60 * MINUTE_MS;
const DAY_MS = 24 * HOUR_MS;
const RECENT_DAYS = 7;
const WEEKS_DAYS = 30;
const YEAR_DAYS = 365;

export function fadeFor(savedAt: Date, now: Date): ThumbFade {
    const days = (now.getTime() - savedAt.getTime()) / DAY_MS;
    if (days < RECENT_DAYS) return "recent";
    if (days < WEEKS_DAYS) return "weeks";
    return "months";
}

// 저장 시각이 지금보다 뒤(기기 시계 차이)면 "방금"이다.
export function savedAgo(savedAt: Date, now: Date): string {
    const ms = now.getTime() - savedAt.getTime();
    const days = ms / DAY_MS;
    if (ms < MINUTE_MS) return "방금";
    if (ms < HOUR_MS) return `${Math.floor(ms / MINUTE_MS)}분 전`;
    if (ms < DAY_MS) return `${Math.floor(ms / HOUR_MS)}시간 전`;
    if (days < RECENT_DAYS) return `${Math.floor(days)}일 전`;
    if (days < WEEKS_DAYS) return `${Math.floor(days / 7)}주 전`;
    if (days < YEAR_DAYS) return `${Math.floor(days / WEEKS_DAYS)}개월 전`;
    return `${Math.floor(days / YEAR_DAYS)}년 전`;
}
