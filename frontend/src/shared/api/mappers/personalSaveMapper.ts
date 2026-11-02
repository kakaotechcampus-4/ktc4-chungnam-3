// 서버 저장물(상세 · 목록 항목)을 앱의 저장 상태로 변환.
// PENDING · ANALYZING → analyzing. FAILED → failed. SUCCESS · PARTIAL 은 끝난 것으로 보고 장소 수로 noPlace · ready 를 가른다.

import type { SaveFailure, SaveState } from "../../../domain/saveState";
import type {
    AnalysisFailureCode,
    AnalysisStatus,
    PersonalSaveDetailResponse,
    PersonalSaveItemResponse,
} from "../personalSaves";

export function toSaveState(detail: PersonalSaveDetailResponse): SaveState {
    return toState(
        detail.analysisStatus,
        detail.places.length,
        toSaveFailure(detail.analysisFailureCode),
    );
}

// 목록 항목에는 실패 코드가 없어 failed 의 failure 는 항상 null 이다.
export function toSaveStateFromItem(item: PersonalSaveItemResponse): SaveState {
    return toState(item.analysisStatus, item.placeCount, null);
}

function toState(
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
