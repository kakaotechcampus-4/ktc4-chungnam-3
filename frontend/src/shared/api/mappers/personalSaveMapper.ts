// 서버 저장물(상세 · 목록 항목)을 앱의 저장 상태로 변환.
// PENDING · ANALYZING → analyzing. FAILED → failed(실패 코드로 이유). SUCCESS · PARTIAL 은 끝난 것으로 보고 장소 수로 noPlace · ready 를 가른다.
// 끝난 저장물에 답하지 않은 되묻기가 남아 있으면 장소 수와 관계없이 needsConfirmation 이다(장소가 일부 확정됐어도).
// 상세는 confirmCandidates(답하지 않은 후보만) 수, 목록 항목은 pendingConfirmCount 로 센다. 둘은 같은 규칙이다.

import type { SaveFailure, SaveState } from "../../../domain/saveState";
import type {
    AnalysisFailureCode,
    AnalysisStatus,
    PersonalSaveDetailResponse,
    PersonalSaveItemResponse,
} from "../personalSaves";

export function toSaveState(detail: PersonalSaveDetailResponse): SaveState {
    // 목록 필드는 빈 배열 보장이 서버에서 강제되지 않는다(STRUCTURE "확정된 것").
    return toState(
        detail.analysisStatus,
        detail.places.length,
        toSaveFailure(detail.analysisFailureCode),
        detail.confirmCandidates?.length ?? 0,
    );
}

export function toSaveStateFromItem(item: PersonalSaveItemResponse): SaveState {
    return toState(
        item.analysisStatus,
        item.placeCount,
        toSaveFailure(item.analysisFailureCode),
        item.pendingConfirmCount,
    );
}

function toState(
    status: AnalysisStatus,
    placeCount: number,
    failure: SaveFailure | null,
    pendingConfirmCount: number,
): SaveState {
    const state = toAnalysisState(status, placeCount, failure);
    if (
        (state.kind === "ready" || state.kind === "noPlace") &&
        pendingConfirmCount > 0
    ) {
        return { kind: "needsConfirmation" };
    }
    return state;
}

// 분석 상태와 장소 수만 본 상태(되묻기 전).
function toAnalysisState(
    status: AnalysisStatus,
    placeCount: number,
    failure: SaveFailure | null,
): SaveState {
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
