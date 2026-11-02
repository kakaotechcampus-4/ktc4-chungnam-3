// 서버 저장물(상세 · 목록 항목)을 앱의 저장 상태로 변환.
// PENDING · ANALYZING → analyzing. FAILED → failed(실패 코드로 이유). SUCCESS · PARTIAL 은 끝난 것으로 보고 장소 수로 noPlace · ready 를 가른다.
// 목록 항목은 끝난 저장물에 답하지 않은 되묻기(pendingConfirmCount)가 남아 있으면 장소 수와 관계없이 needsConfirmation 이다.
// 상세는 되묻기 후보(confirmCandidates)를 아직 읽지 않아 needsConfirmation 을 만들지 않는다(되묻기 02 연결 전).

import type { SaveFailure, SaveState } from "../../../domain/saveState";
import type {
    AnalysisFailureCode,
    AnalysisStatus,
    PersonalSaveDetailResponse,
    PersonalSaveItemResponse,
} from "../personalSaves";

// 상세에서 계산한 상태. 되묻기 02 를 연결하기 전까지 확인 필요가 없다.
export type DetailSaveState = Exclude<SaveState, { kind: "needsConfirmation" }>;

export function toSaveState(
    detail: PersonalSaveDetailResponse,
): DetailSaveState {
    return toState(
        detail.analysisStatus,
        detail.places.length,
        toSaveFailure(detail.analysisFailureCode),
    );
}

export function toSaveStateFromItem(item: PersonalSaveItemResponse): SaveState {
    const state = toState(
        item.analysisStatus,
        item.placeCount,
        toSaveFailure(item.analysisFailureCode),
    );
    if (
        (state.kind === "ready" || state.kind === "noPlace") &&
        item.pendingConfirmCount > 0
    ) {
        return { kind: "needsConfirmation" };
    }
    return state;
}

function toState(
    status: AnalysisStatus,
    placeCount: number,
    failure: SaveFailure | null,
): DetailSaveState {
    switch (status) {
        case "PENDING":
        case "ANALYZING":
            return { kind: "analyzing" };
        case "FAILED":
            return { kind: "failed", failure };
        case "SUCCESS":
        case "PARTIAL":
            return placeCount > 0 ? { kind: "ready" } : { kind: "noPlace" };
    }
}

// 서버가 같은 링크 재저장 때 다시 분석하는 실패는 EXTRACTION_API_ERROR 뿐이다(ContentRepository.claimAnalysis).
function toSaveFailure(code: AnalysisFailureCode | null): SaveFailure | null {
    switch (code) {
        case null:
            return null;
        case "VIDEO_UNAVAILABLE":
            return "videoUnavailable";
        case "EXTRACTION_API_ERROR":
            return "retryable";
        case "EXTRACTION_RESULT_ERROR":
        case "PLACE_SEARCH_API_ERROR":
        case "PLACE_VERIFICATION_API_ERROR":
            return "analysisError";
    }
}
