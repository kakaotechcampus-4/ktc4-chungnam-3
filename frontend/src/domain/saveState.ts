// 앱 모델. 저장물 하나가 화면에서 어떤 상태인가. 서버 응답(shared/api/personalSaves)에서 mappers/personalSaveMapper 가 계산한다.
// analyzing: 분석 대기 · 진행 중. failed: 분석 실패. noPlace: 분석은 끝났지만 연결된 장소가 없음. ready: 장소가 하나 이상 있음.
// failed 의 failure 는 videoUnavailable(영상이 없어져 다시 분석해도 소용없음) · retryable(일시적인 추출 실패) · analysisError(그 밖의 분석 오류).
// 05 실패 화면은 retryable 일 때만 "다시 시도"를 보인다. 같은 링크를 다시 저장하면 서버가 다시 분석하는 실패는 이것뿐이다.
// 실패 이유를 모르면(목록 항목에는 실패 코드가 없다) null 이다.

export type SaveFailure = "videoUnavailable" | "retryable" | "analysisError";

export type SaveState =
    | { kind: "analyzing" }
    | { kind: "failed"; failure: SaveFailure | null }
    | { kind: "noPlace" }
    | { kind: "ready" };
