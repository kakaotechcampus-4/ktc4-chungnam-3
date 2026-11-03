// 저장물 API 의 목. 서버 계약 모양이라 화면 props 모양인 mock/ 폴더가 아니라 API 모듈 옆에 둔다(consents.ts 의 목과 같은 자리).
// 서버(PersonalSaveService · ContentRepository)와 같은 규칙을 따른다. 이번 실행 동안 메모리에 남는다.
// 시작 상태: MOCK_SCENARIO.archiveEmpty 면 빈 저장소, 아니면 샘플 10개(seedSaves). 공유로 저장한 것도 같은 저장소에 쌓인다.
// 저장 결과는 MOCK_SCENARIO.saveResult, 분석 결과는 analysisResult, 되묻기 답변 결과는 confirmResult 로 바꾼다.
// 되묻기 후보는 답하지 않은 것만 저장물에 두고(서버 PENDING), 답하면 고른 선택지로 장소를 붙이고 후보를 지운다.
// 새로 저장하면 ANALYZING 이고, 분석을 시작하고 ANALYSIS_MS 가 지난 뒤 상세 · 목록을 읽으면 그때의 analysisResult 로 끝난다.
import { ApiError } from "./client";
import { MOCK_SCENARIO } from "./mock";
import type {
    AnalysisFailureCode,
    AnalysisStatus,
    ConfirmCandidateResponse,
    ConfirmOptionResponse,
    PersonalSaveDetailResponse,
    PersonalSaveItemResponse,
    PersonalSavePageResponse,
    PersonalSavePlaceResponse,
    PersonalSaveResponse,
    SavedPlacePinResponse,
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
    confirmCandidates: ConfirmCandidateResponse[];
};

const saves: MockSave[] = MOCK_SCENARIO.archiveEmpty ? [] : seedSaves();

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
        confirmCandidates: [],
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
        confirmCandidates: save.confirmCandidates.map((candidate) => ({
            ...candidate,
            options: candidate.options.map((option) => ({ ...option })),
        })),
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

// 서버(findAllPlaces)와 같은 규칙. 분석 상태로 거르지 않고 장소와 저장물 한 쌍마다 한 항목, 최신 저장순(같으면 id 순).
export async function mockFetchSavedPlaces(): Promise<SavedPlacePinResponse[]> {
    await mockDelay();
    return [...saves].sort(compareSaves).flatMap((save) => {
        finishIfDue(save);
        return save.places.map(
            ({ placeId, name, address, latitude, longitude }) => ({
                placeId,
                name,
                address,
                latitude,
                longitude,
                personalSaveId: save.personalSaveId,
                videoId: save.videoId,
                savedAt: save.savedAt,
            }),
        );
    });
}

// 서버(ConfirmAnswerService)와 같은 규칙. 내 저장물이 아니거나 후보가 없으면(이미 답함) 404, 그 후보의 선택지가 아니면 400.
// 성공하면 고른 선택지로 장소를 붙이고(좌표 · 이름 · 주소 그대로) 후보를 지운다. 카카오 확인은 흉내 내지 않는다.
// MOCK_SCENARIO.confirmResult: notFound 면 404(후보는 그대로 남는다, 서버의 카카오 확인 실패와 같은 모양), networkError 면 응답 없음.
export async function mockConfirmPlace(
    personalSaveId: string,
    candidateId: string,
    selectedOptionId: string,
): Promise<void> {
    await mockDelay();
    if (MOCK_SCENARIO.confirmResult === "networkError") {
        throw new ApiError(
            null,
            "NETWORK_ERROR",
            "서버에 연결하지 못했습니다.",
        );
    }
    const save = findSave(personalSaveId);
    const candidate = save.confirmCandidates.find(
        (item) => item.candidateId === candidateId,
    );
    if (candidate == null || MOCK_SCENARIO.confirmResult === "notFound") {
        throw notFound();
    }
    const option = candidate.options.find(
        (item) => item.candidateId === selectedOptionId,
    );
    if (option == null) throw invalidRequest();
    save.places.push({
        placeId: mockUuid(),
        name: option.name ?? "",
        address: option.address,
        latitude: option.lat,
        longitude: option.lng,
        description: null,
    });
    save.confirmCandidates = save.confirmCandidates.filter(
        (item) => item !== candidate,
    );
}

export async function mockDeletePersonalSave(
    personalSaveId: string,
): Promise<void> {
    await mockDelay();
    const save = findSave(personalSaveId);
    saves.splice(saves.indexOf(save), 1);
}

// 지오펜스 목이 쓴다. 서버의 지오펜스 대상(분석 실패 · 볼 수 없는 영상이 아닌 저장물의 장소)과 같다.
// 목에서는 볼 수 없는 영상도 FAILED 라 FAILED 만 뺀다. 분석 시간이 지난 저장물은 먼저 끝낸다. 지운 저장물은 이미 빠져 있다.
// 서버 트리거는 (회원, 장소)마다 하나라 여러 저장물에 있는 장소도 한 번만 낸다.
export function mockGeofencePlaces(): {
    placeId: string;
    latitude: number;
    longitude: number;
}[] {
    const seen = new Set<string>();
    return saves.flatMap((save) => {
        finishIfDue(save);
        if (save.analysisStatus === "FAILED") return [];
        return save.places
            .filter(({ placeId }) => {
                if (seen.has(placeId)) return false;
                seen.add(placeId);
                return true;
            })
            .map(({ placeId, latitude, longitude }) => ({
                placeId,
                latitude,
                longitude,
            }));
    });
}

// 알림 목이 쓴다. 이 장소를 가진 저장물(분석 실패 제외)과 그 장소. 없으면(지웠거나 분석 실패) null.
export function mockSavedPlace(placeId: string): {
    personalSaveId: string;
    contentId: string;
    savedAt: string;
    title: string | null;
    summary: string | null;
    place: PersonalSavePlaceResponse;
} | null {
    for (const save of saves) {
        finishIfDue(save);
        if (save.analysisStatus === "FAILED") continue;
        const place = save.places.find((item) => item.placeId === placeId);
        if (place == null) continue;
        return {
            personalSaveId: save.personalSaveId,
            contentId: save.contentId,
            savedAt: save.savedAt,
            title: save.title,
            summary: save.summary,
            place: { ...place },
        };
    }
    return null;
}

function mockDelay() {
    return new Promise((resolve) => setTimeout(resolve, MOCK_DELAY_MS));
}

function invalidRequest(): ApiError {
    return new ApiError(400, "INVALID_REQUEST", "요청 값이 올바르지 않습니다.");
}

function notFound(): ApiError {
    return new ApiError(404, "NOT_FOUND", "요청한 경로를 찾을 수 없습니다.");
}

function findSave(personalSaveId: string): MockSave {
    const save = saves.find((item) => item.personalSaveId === personalSaveId);
    if (save == null) throw notFound();
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
    if (result !== "success" && result !== "noPlace") {
        save.analysisStatus = "FAILED";
        save.analysisFailureCode = FAILURE_CODE[result];
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

const FAILURE_CODE = {
    failed: "EXTRACTION_API_ERROR",
    videoUnavailable: "VIDEO_UNAVAILABLE",
    analysisError: "EXTRACTION_RESULT_ERROR",
} as const satisfies Record<string, AnalysisFailureCode>;

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

// 기억 탭 · 지도 · 근처 샘플. 저장 시각을 오늘 · 2주 전 · 3주 전 · 2달 전 · 5달 전으로 나눠 바램 세 단계가 보이게 한다.
// 장소는 지도 목(mock/mapView)과 같은 대전 7곳 · 좌표다. 성심당 본점은 두 저장물에 있다(장소 목록에 두 항목, 가장 최근은 오늘).
// 되묻기(확인 필요) 샘플 둘: 선택지 2곳(02, 같은 이름 · LocationIQ 형식 주소만 다름)과 선택지 1곳(02b, 도로명 주소). 답하기 전에는 장소가 없다.
// 영상 id 는 가짜(i.ytimg.com 404)라 썸네일은 자리표시다. 같은 id 를 공유하면 이미 저장한 영상이 된다.
// 분석 중 샘플은 앱을 켜고 ANALYSIS_MS 뒤에 analysisResult 로 끝난다.
function seedSaves(): MockSave[] {
    // 같은 날 샘플도 순서가 정해지게 시간까지 나눈다.
    const daysAgo = (days: number, hours = 0) =>
        new Date(
            Date.now() - (days * 24 + hours) * 60 * 60 * 1000,
        ).toISOString();
    const monthsAgo = (months: number) => {
        const date = new Date();
        date.setMonth(date.getMonth() - months);
        return date.toISOString();
    };
    const seed = (
        videoId: string,
        savedAt: string,
        analysisStatus: AnalysisStatus,
        analysisFailureCode: AnalysisFailureCode | null,
        title: string | null,
        places: PersonalSavePlaceResponse[],
        confirmCandidates: ConfirmCandidateResponse[] = [],
    ): MockSave => ({
        personalSaveId: mockUuid(),
        contentId: mockUuid(),
        videoId,
        savedAt,
        analysisStatus,
        analysisFailureCode,
        analysisStartedAt: Date.now(),
        title,
        summary: title == null ? null : `${title} 영상이에요.`,
        category: title == null ? null : "여행",
        places,
        confirmCandidates,
    });
    // 서버처럼 선택지 id 는 "{후보 id}-{순번}" 이다.
    const candidate = (
        options: Omit<ConfirmOptionResponse, "candidateId">[],
    ): ConfirmCandidateResponse => ({
        candidateId: "p1",
        question: "어느 장소가 맞을까요?",
        options: options.map((option, index) => ({
            ...option,
            candidateId: `p1-${index + 1}`,
        })),
    });
    const option = (
        name: string,
        address: string,
        lat: number,
        lng: number,
    ): Omit<ConfirmOptionResponse, "candidateId"> => ({
        name,
        branchName: null,
        address,
        lat,
        lng,
        geocodingProvider: "LOCATIONIQ",
        geocodingPlaceId: mockUuid(),
        verificationPlaceId: null,
    });
    // 샘플 장소 placeId 는 고정이다(실행마다 대표 장소명이 바뀌지 않게). 서버 대표 장소는 placeId 가 가장 작은 장소라서,
    // 각 저장물의 첫 장소가 가장 작게 골라 대표 장소 = 첫 장소(08 이 그리는 장소)가 되게 한다. 끝자리 순서가 그 순서다.
    const place = (
        placeId: string,
        name: string,
        address: string | null,
        latitude: number,
        longitude: number,
    ): PersonalSavePlaceResponse => ({
        placeId,
        name,
        address,
        latitude,
        longitude,
        description: null,
    });
    const sungsimdang = place(
        "00000000-0000-4000-8000-000000000004",
        "성심당 본점",
        "대전 중구 은행동",
        36.3277,
        127.4274,
    );
    return [
        seed(
            "mockSave001",
            daysAgo(0, 2),
            "SUCCESS",
            null,
            "성심당 튀김소보로 줄 짧은 시간",
            [sungsimdang],
        ),
        seed("mockSave002", daysAgo(0), "ANALYZING", null, null, []),
        seed(
            "mockSave003",
            daysAgo(1),
            "FAILED",
            "EXTRACTION_API_ERROR",
            null,
            [],
        ),
        seed(
            "mockSave009",
            daysAgo(3),
            "SUCCESS",
            null,
            "대전역 앞 칼국수 한 그릇",
            [],
            [
                candidate([
                    option(
                        "역전칼국수",
                        "역전칼국수, 중앙로, 정동, 동구, 대전, 34573, 대한민국",
                        36.3318,
                        127.4335,
                    ),
                    option(
                        "역전칼국수",
                        "역전칼국수, 둔산로, 둔산동, 서구, 대전, 35233, 대한민국",
                        36.3515,
                        127.378,
                    ),
                ]),
            ],
        ),
        seed(
            "mockSave010",
            daysAgo(5),
            "SUCCESS",
            null,
            "유성 온천 족욕 산책",
            [],
            [
                candidate([
                    option(
                        "유성온천 족욕체험장",
                        "대전 유성구 봉명동 574",
                        36.3553,
                        127.3443,
                    ),
                ]),
            ],
        ),
        seed("mockSave004", daysAgo(14), "SUCCESS", null, "대흥동 골목 산책", [
            place(
                "00000000-0000-4000-8000-000000000002",
                "오후 네시",
                "대전 중구 대흥동",
                36.3244,
                127.4246,
            ),
            // 주소가 없는 장소(08b 빈 필드).
            place(
                "00000000-0000-4000-8000-000000000005",
                "대흥동 성당",
                null,
                36.3256,
                127.4222,
            ),
        ]),
        seed(
            "mockSave005",
            daysAgo(21),
            "FAILED",
            "VIDEO_UNAVAILABLE",
            null,
            [],
        ),
        seed(
            "mockSave006",
            daysAgo(21, 2),
            "SUCCESS",
            null,
            "대전 사람만 아는 사투리 5가지",
            [],
        ),
        seed("mockSave007", monthsAgo(2), "SUCCESS", null, "원도심 밤 산책", [
            place(
                "00000000-0000-4000-8000-000000000003",
                "으능정이 스카이로드",
                "대전 중구 은행동",
                36.3292,
                127.4282,
            ),
            place(
                "00000000-0000-4000-8000-000000000006",
                "옛 충남도청",
                "대전 중구 선화동",
                36.3275,
                127.4204,
            ),
        ]),
        seed(
            "mockSave008",
            monthsAgo(5),
            "SUCCESS",
            null,
            "중앙시장 노포 점심",
            [
                place(
                    "00000000-0000-4000-8000-000000000001",
                    "한밭 손칼국수",
                    "대전 중구 대흥동",
                    36.3229,
                    127.4263,
                ),
                place(
                    "00000000-0000-4000-8000-000000000007",
                    "대전 중앙시장",
                    "대전 동구 원동",
                    36.3289,
                    127.4331,
                ),
                sungsimdang,
            ],
        ),
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
        analysisFailureCode: hasFailureCode(save.analysisStatus)
            ? save.analysisFailureCode
            : null,
        placeCount: save.places.length,
        representativePlaceName: representativePlace(save)?.name ?? null,
        pendingConfirmCount: save.confirmCandidates.length,
        savedAt: save.savedAt,
    };
}

// 서버처럼 장소 중 placeId 가 가장 작은 것.
function representativePlace(
    save: MockSave,
): PersonalSavePlaceResponse | undefined {
    return [...save.places].sort((a, b) =>
        a.placeId < b.placeId ? -1 : a.placeId > b.placeId ? 1 : 0,
    )[0];
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

// 목 id. 서버처럼 UUID 모양이지만 보안 난수가 아니다. 알림 목도 쓴다.
export function mockUuid(): string {
    return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (char) => {
        const random = Math.floor(Math.random() * 16);
        const value = char === "x" ? random : (random % 4) + 8;
        return value.toString(16);
    });
}
