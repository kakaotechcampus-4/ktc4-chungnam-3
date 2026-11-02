// 되묻기(02 · 02b) 화면 값. 순수 함수.
// 답하지 않은 후보를 하나씩 보인다(pendingCandidate). 선택지 1줄은 "{이름} {지점}"(이름에 지점이 이미 있으면 그대로),
// 2줄은 짧게 다듬은 주소(shortAddress, 비면 숨김). 이름이 빈 선택지는 빼고, 다듬은 뒤 두 줄이 같은 선택지는 첫 번째만 남긴다.
// 다 답한 뒤 08 로 갈 때는 고른 선택지 좌표에서 PLACE_MATCH_METERS 안의 가장 가까운 장소를 연다(placeIdFor).
// 서버 답변은 장소 id 를 주지 않고, 같은 카카오 장소면 이미 있는 장소를 다시 쓰기도 해서 좌표로 찾는다.
import type {
    ConfirmCandidateResponse,
    PersonalSaveDetailResponse,
} from "../../shared/api/personalSaves";
import { distanceMeters } from "../../shared/geo/distanceMeters";

const PLACE_MATCH_METERS = 100;
const LOCATIONIQ_COUNTRY = "대한민국";
const POSTCODE = /^\d+$/;

export type ConfirmOptionView = {
    id: string;
    name: string;
    address?: string;
    latitude: number;
    longitude: number;
};

// 답한 후보(answered)를 빼고 선택지가 남는 첫 후보.
export function pendingCandidate(
    detail: PersonalSaveDetailResponse,
    answered: ReadonlySet<string>,
): ConfirmCandidateResponse | undefined {
    return (detail.confirmCandidates ?? []).find(
        (candidate) =>
            !answered.has(candidate.candidateId) &&
            toOptionViews(candidate).length > 0,
    );
}

export function toOptionViews(
    candidate: ConfirmCandidateResponse,
): ConfirmOptionView[] {
    const views: ConfirmOptionView[] = [];
    for (const option of candidate.options) {
        const name = present(option.name);
        if (name == null) continue;
        const branch = present(option.branchName);
        const view: ConfirmOptionView = {
            id: option.candidateId,
            name:
                branch != null && !name.includes(branch)
                    ? `${name} ${branch}`
                    : name,
            address: shortAddress(option.address, name),
            latitude: option.lat,
            longitude: option.lng,
        };
        const duplicate = views.some(
            (kept) => kept.name === view.name && kept.address === view.address,
        );
        if (!duplicate) views.push(view);
    }
    return views;
}

// LocationIQ 형식(쉼표로 나뉘고 "대한민국"으로 끝남)이면 장소명과 같은 조각 · 우편번호(숫자만) · "대한민국"을 빼고
// 순서를 뒤집어 띄어쓰기로 잇는다("성심당, 대종로480번길, 은행동, 중구, 대전, 34917, 대한민국" → "대전 중구 은행동 대종로480번길").
// 그 밖의 형식은 원문 그대로. 비면 undefined.
export function shortAddress(
    address: string | null,
    name: string,
): string | undefined {
    const raw = present(address?.trim() ?? null);
    if (raw == null) return undefined;
    const parts = raw
        .split(",")
        .map((part) => part.trim())
        .filter((part) => part !== "");
    if (parts.length < 2 || parts[parts.length - 1] !== LOCATIONIQ_COUNTRY) {
        return raw;
    }
    const kept = parts.filter(
        (part) =>
            part !== LOCATIONIQ_COUNTRY &&
            !POSTCODE.test(part) &&
            part !== name,
    );
    return present(kept.reverse().join(" "));
}

export function placeIdFor(
    detail: PersonalSaveDetailResponse,
    option: ConfirmOptionView,
): string | undefined {
    let nearest: { placeId: string; meters: number } | undefined;
    for (const place of detail.places) {
        const meters = distanceMeters(option, place);
        if (meters > PLACE_MATCH_METERS) continue;
        if (nearest == null || meters < nearest.meters) {
            nearest = { placeId: place.placeId, meters };
        }
    }
    return nearest?.placeId;
}

// null · 빈 문자열은 없는 것으로 본다.
function present(value: string | null): string | undefined {
    return value == null || value === "" ? undefined : value;
}
