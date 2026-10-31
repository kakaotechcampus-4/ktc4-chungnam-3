// 05 분석 실패 문구. 순수 함수. 실패 종류(SaveFailure)로 가른다(디자인 세션 결정).
// retryable 만 "다시 시도"로 다시 저장한다. 같은 링크를 다시 저장하면 서버가 다시 분석하는 실패는 이것뿐이다.
// 코드를 모르면(null) Figma 05 문구 그대로 보이고 다시 저장을 해본다. 서버가 다시 분석하지 않으면 05 에 머문다.
// 상세를 읽지 못했을 때(readFailed)도 Figma 05 문구이고, "다시 시도"는 다시 읽기다.
import type { SaveFailure } from "../../domain/saveState";

export type FailedMessage = {
    heading: string;
    body: string;
    retry: "save" | "read" | null;
};

const FIGMA_05 = {
    heading: "영상을 불러오지 못했어요",
    body: "비공개로 바뀌었거나 연결이 잠깐 불안정했어요. 링크는 그대로 보관해둘게요.",
};

export function failedMessage(
    failure: SaveFailure | null | "readFailed",
): FailedMessage {
    switch (failure) {
        case "retryable":
            return {
                heading: "영상을 불러오지 못했어요",
                body: "분석하는 중에 연결이 끊겼어요. 다시 시도하면 처음부터 찾아볼게요.",
                retry: "save",
            };
        case "videoUnavailable":
            return {
                heading: "볼 수 없는 영상이에요",
                body: "비공개로 바뀌었거나 삭제된 영상이라 다시 분석할 수 없어요.",
                retry: null,
            };
        case "analysisError":
            return {
                heading: "장소를 알아내지 못했어요",
                body: "이 영상은 다시 분석해도 같은 결과가 나와요. 링크는 그대로 보관해둘게요.",
                retry: null,
            };
        case null:
            return { ...FIGMA_05, retry: "save" };
        case "readFailed":
            return { ...FIGMA_05, retry: "read" };
    }
}
