// 저장물 API 의 목. 서버 계약 모양이라 화면 props 모양인 mock/ 폴더가 아니라 API 모듈 옆에 둔다(consents.ts 의 목과 같은 자리).
// 서버(PersonalSaveService · ContentRepository)와 같은 규칙을 따른다. 시작 상태는 빈 저장소이고, 이번 실행 동안 메모리에 남는다.
// 저장 결과는 MOCK_SCENARIO.saveResult, 분석 결과는 analysisResult 로 바꾼다.
// 새로 저장하면 ANALYZING 이고, 분석을 시작하고 ANALYSIS_MS 가 지난 뒤 상세 · 목록을 읽으면 그때의 analysisResult 로 끝난다.
import { ApiError } from "./client";
import { MOCK_SCENARIO } from "./mock";
import type {
    AnalysisFailureCode,
    AnalysisStatus,
    PersonalSaveDetailResponse,
    PersonalSaveItemResponse,
    PersonalSavePageResponse,
    PersonalSavePlaceResponse,
    PersonalSaveResponse,
} from "./personalSaves";

const MOCK_DELAY_MS = 600;
const ANALYSIS_MS = 5_000;
const DEFAULT_PAGE_SIZE = 20;
const MAX_PAGE_SIZE = 100;
const MAX_SUMMARY_LENGTH = 200;
const VIDEO_ID = /^[A-Za-z0-9_-]{1,32}$/;

type MockSave = {
    personalSaveId: string;
    contentId: string;
    videoId: string;
    savedAt: string;
    analysisStatus: AnalysisStatus;
    analysisFailureCode: AnalysisFailureCode | null;
    analysisStartedAt: number;
    title: string | null;
    summary: string | null;
    category: string | null;
    places: PersonalSavePlaceResponse[];
};

const saves: MockSave[] = [];

export async function mockSavePersonalSave(
    url: string,
): Promise<{ save: PersonalSaveResponse; created: boolean }> {
    await mockDelay();
    if (MOCK_SCENARIO.saveResult === "networkError") {
        throw new ApiError(
            null,
            "NETWORK_ERROR",
            "서버에 연결하지 못했습니다.",
        );
    }
    const videoId =
        MOCK_SCENARIO.saveResult === "invalid" ? null : extractVideoId(url);
    if (videoId == null) throw invalidRequest();

    const existing = saves.find((save) => save.videoId === videoId);
    if (existing != null) {
        // 서버처럼 일시적인 추출 실패만 다시 분석한다.
        if (
            existing.analysisStatus === "FAILED" &&
            existing.analysisFailureCode === "EXTRACTION_API_ERROR"
        ) {
            startAnalysis(existing);
        }
        return { save: toResponse(existing), created: false };
    }

    const save: MockSave = {
        personalSaveId: mockUuid(),
        contentId: mockUuid(),
        videoId,
        savedAt: new Date().toISOString(),
        analysisStatus: "ANALYZING",
        analysisFailureCode: null,
        analysisStartedAt: Date.now(),
        title: null,
        summary: null,
        category: null,
        places: [],
    };
    saves.push(save);
    return { save: toResponse(save), created: true };
}

export async function mockFetchPersonalSave(
    personalSaveId: string,
): Promise<PersonalSaveDetailResponse> {
    await mockDelay();
    const save = findSave(personalSaveId);
    finishIfDue(save);
    return {
        personalSaveId: save.personalSaveId,
        contentId: save.contentId,
        videoId: save.videoId,
        sourceUrl: `https://www.youtube.com/watch?v=${save.videoId}`,
        title: save.title,
        summary: save.summary,
        category: save.category,
        analysisStatus: save.analysisStatus,
        analysisFailureCode: hasFailureCode(save.analysisStatus)
            ? save.analysisFailureCode
            : null,
        savedAt: save.savedAt,
        places: save.places.map((place) => ({ ...place })),
    };
}

export async function mockFetchPersonalSaves(params: {
    size?: number;
    cursor?: string;
}): Promise<PersonalSavePageResponse> {
    await mockDelay();
    const size = params.size ?? DEFAULT_PAGE_SIZE;
    if (!Number.isInteger(size) || size < 1 || size > MAX_PAGE_SIZE) {
        throw invalidRequest();
    }
    // 서버와 같은 순서(saved_at DESC, id ASC). 커서는 마지막 항목의 (savedAt, id) 다음부터.
    const sorted = [...saves].sort(compareSaves);
    let rest = sorted;
    if (params.cursor != null) {
        const after = decodeCursor(params.cursor);
        if (after == null) throw invalidRequest();
        rest = sorted.filter((save) => compareSaves(save, after) > 0);
    }
    const page = rest.slice(0, size);
    const last = page[page.length - 1];
    return {
        items: page.map((save) => {
            finishIfDue(save);
            return toItem(save);
        }),
        nextCursor:
            rest.length > size && last != null ? encodeCursor(last) : null,
    };
}

export async function mockDeletePersonalSave(
    personalSaveId: string,
): Promise<void> {
    await mockDelay();
    const save = findSave(personalSaveId);
    saves.splice(saves.indexOf(save), 1);
}

function mockDelay() {
    return new Promise((resolve) => setTimeout(resolve, MOCK_DELAY_MS));
}

function invalidRequest(): ApiError {
    return new ApiError(400, "INVALID_REQUEST", "요청 값이 올바르지 않습니다.");
}

function findSave(personalSaveId: string): MockSave {
    const save = saves.find((item) => item.personalSaveId === personalSaveId);
    if (save == null) {
        throw new ApiError(404, "NOT_FOUND", "요청한 경로를 찾을 수 없습니다.");
    }
    return save;
}

function startAnalysis(save: MockSave): void {
    save.analysisStatus = "ANALYZING";
    save.analysisFailureCode = null;
    save.analysisStartedAt = Date.now();
}

// 분석 시간이 지났으면 지금의 analysisResult 로 끝낸다.
function finishIfDue(save: MockSave): void {
    if (save.analysisStatus !== "ANALYZING") return;
    if (Date.now() - save.analysisStartedAt < ANALYSIS_MS) return;
    const result = MOCK_SCENARIO.analysisResult;
    if (result === "failed" || result === "videoUnavailable") {
        save.analysisStatus = "FAILED";
        save.analysisFailureCode =
            result === "failed" ? "EXTRACTION_API_ERROR" : "VIDEO_UNAVAILABLE";
        return;
    }
    save.analysisStatus = "SUCCESS";
    save.analysisFailureCode = null;
    save.title = "대전 은행동 골목 맛집 두 곳";
    save.summary =
        "은행동 골목에서 칼국수집과 디저트 카페를 차례로 들러요. 점심 전에 가면 줄이 짧아요.";
    save.category = "맛집";
    save.places = result === "noPlace" ? [] : mockPlaces();
}

function mockPlaces(): PersonalSavePlaceResponse[] {
    return [
        {
            placeId: mockUuid(),
            name: "은행동 칼국수",
            address: "대전 중구 은행동",
            latitude: 36.3288,
            longitude: 127.4276,
            description: "영상 초반에 나온 칼국수집이에요.",
        },
        {
            placeId: mockUuid(),
            name: "대흥동 디저트 카페",
            address: null,
            latitude: 36.3262,
            longitude: 127.4244,
            description: null,
        },
    ];
}

function hasFailureCode(status: AnalysisStatus): boolean {
    return status === "PARTIAL" || status === "FAILED";
}

function toResponse(save: MockSave): PersonalSaveResponse {
    return {
        personalSaveId: save.personalSaveId,
        contentId: save.contentId,
        analysisStatus: save.analysisStatus,
        savedAt: save.savedAt,
    };
}

function toItem(save: MockSave): PersonalSaveItemResponse {
    return {
        personalSaveId: save.personalSaveId,
        contentId: save.contentId,
        videoId: save.videoId,
        title: save.title,
        summary:
            save.summary == null
                ? null
                : Array.from(save.summary)
                      .slice(0, MAX_SUMMARY_LENGTH)
                      .join(""),
        analysisStatus: save.analysisStatus,
        placeCount: save.places.length,
        savedAt: save.savedAt,
    };
}

type CursorKey = { savedAt: string; personalSaveId: string };

function compareSaves(a: CursorKey, b: CursorKey): number {
    if (a.savedAt !== b.savedAt) return a.savedAt < b.savedAt ? 1 : -1;
    if (a.personalSaveId === b.personalSaveId) return 0;
    return a.personalSaveId < b.personalSaveId ? -1 : 1;
}

// 서버 커서처럼 앱은 내용을 보지 않는다. 목에서는 읽기 쉬운 문자열로 둔다.
function encodeCursor(save: CursorKey): string {
    return `mock|${save.savedAt}|${save.personalSaveId}`;
}

function decodeCursor(cursor: string): CursorKey | null {
    const [prefix, savedAt, personalSaveId, ...extra] = cursor.split("|");
    if (prefix !== "mock" || savedAt == null || personalSaveId == null) {
        return null;
    }
    if (extra.length > 0 || Number.isNaN(Date.parse(savedAt))) return null;
    return { savedAt, personalSaveId };
}

// 서버 YoutubeUrlParser 와 같은 규칙. youtu.be/{id}, youtube.com 계열의 /watch?v={id} · /shorts|embed|live/{id}.
// 뽑은 id 가 [A-Za-z0-9_-]{1,32} 가 아니면 null(서버는 400).
function extractVideoId(url: string): string | null {
    const match =
        /^[A-Za-z][A-Za-z0-9+.-]*:\/\/([^/?#]*)([^?#]*)(?:\?([^#]*))?/.exec(
            url.trim(),
        );
    if (match == null) return null;
    const host = match[1]
        .replace(/^.*@/, "")
        .replace(/:\d*$/, "")
        .toLowerCase();
    const path = match[2];
    const query = match[3];
    let id: string | undefined;
    if (host === "youtu.be") {
        id = path.replace(/^\//, "").split("/")[0];
    } else if (host.endsWith("youtube.com")) {
        if (path === "/watch" && query != null) {
            id = /(?:^|&)v=([^&]+)/.exec(query)?.[1];
        }
        id ??= /^\/(shorts|embed|live)\/([^/?]+)/.exec(path)?.[2];
    }
    return id != null && VIDEO_ID.test(id) ? id : null;
}

// 목 id. 서버처럼 UUID 모양이지만 보안 난수가 아니다.
function mockUuid(): string {
    return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (char) => {
        const random = Math.floor(Math.random() * 16);
        const value = char === "x" ? random : (random % 4) + 8;
        return value.toString(16);
    });
}
