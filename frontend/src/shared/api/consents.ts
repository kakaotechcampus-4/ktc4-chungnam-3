// 동의 API. GET /api/me/consents · PUT /api/me/consents/{consentType}. 응답은 백엔드 ConsentResponse 그대로.
// 앱이 바꾸는 것은 위치기반서비스 동의뿐이다. 공용 후보 동의(PUBLIC_CANDIDATE_CONTRIBUTION)는 타입에만 있다.
// 실패는 클라이언트의 ApiError 를 그대로 던진다.
// 목 모드(API 주소 없음)에서는 서버를 부르지 않는다. 시작 상태는 MOCK_SCENARIO.locationConsent, 저장 결과는 consentSaveResult.
// 목 저장이 성공하면 이번 실행 동안 메모리에 남아 이후 목 조회에 보인다.
import { request } from "./client";
import { isApiConfigured } from "./config";
import { LOCATION_TERMS_VERSION } from "./consentTerms";
import { MOCK_SCENARIO } from "./mock";

export type ConsentType =
    | "LOCATION_BASED_SERVICE"
    | "PUBLIC_CANDIDATE_CONTRIBUTION";

// 날짜는 ISO-8601 UTC. 한 번도 동의하지 않았으면 termsVersion · agreedAt · withdrawnAt 이 null 이다.
// 철회했으면 agreed 는 false, 나머지는 마지막 동의 기록 그대로 withdrawnAt 이 채워진다.
export type ConsentResponse = {
    consentType: ConsentType;
    agreed: boolean;
    termsVersion: string | null;
    agreedAt: string | null;
    withdrawnAt: string | null;
};

// 철회할 때 서버는 termsVersion 을 보지 않는다.
type ConsentRequest =
    | { agreed: true; termsVersion: string }
    | { agreed: false };

const LOCATION: ConsentType = "LOCATION_BASED_SERVICE";
const MOCK_DELAY_MS = 600;

export async function fetchConsents(): Promise<ConsentResponse[]> {
    if (!isApiConfigured) {
        await mockDelay();
        // 서버와 같은 순서(PUBLIC_CANDIDATE_CONTRIBUTION, LOCATION_BASED_SERVICE).
        return [
            neverConsent("PUBLIC_CANDIDATE_CONTRIBUTION"),
            getMockLocationConsent(),
        ];
    }
    return request<ConsentResponse[]>("/api/me/consents");
}

export async function setLocationConsent(
    agreed: boolean,
): Promise<ConsentResponse> {
    const body: ConsentRequest = agreed
        ? { agreed: true, termsVersion: LOCATION_TERMS_VERSION }
        : { agreed: false };
    if (!isApiConfigured) {
        await mockDelay();
        if (MOCK_SCENARIO.consentSaveResult === "fail") {
            throw new Error("mock consent save failed");
        }
        mockLocationConsent = applyMockChange(getMockLocationConsent(), body);
        return mockLocationConsent;
    }
    return request<ConsentResponse>(`/api/me/consents/${LOCATION}`, {
        method: "PUT",
        body,
    });
}

function mockDelay() {
    return new Promise((resolve) => setTimeout(resolve, MOCK_DELAY_MS));
}

function neverConsent(type: ConsentType): ConsentResponse {
    return {
        consentType: type,
        agreed: false,
        termsVersion: null,
        agreedAt: null,
        withdrawnAt: null,
    };
}

let mockLocationConsent: ConsentResponse | undefined;

function getMockLocationConsent(): ConsentResponse {
    if (mockLocationConsent != null) return mockLocationConsent;
    const state = MOCK_SCENARIO.locationConsent;
    if (state === "never") {
        mockLocationConsent = neverConsent(LOCATION);
    } else {
        const now = new Date().toISOString();
        mockLocationConsent = {
            consentType: LOCATION,
            agreed: state === "agreed",
            termsVersion: LOCATION_TERMS_VERSION,
            agreedAt: now,
            withdrawnAt: state === "withdrawn" ? now : null,
        };
    }
    return mockLocationConsent;
}

// 서버(MemberConsentService.change)와 같게 바꾼다. 같은 버전 재동의 · 동의 없는 철회는 그대로 둔다.
function applyMockChange(
    current: ConsentResponse,
    body: ConsentRequest,
): ConsentResponse {
    const now = new Date().toISOString();
    if (!body.agreed) {
        return current.agreed
            ? { ...current, agreed: false, withdrawnAt: now }
            : current;
    }
    if (current.agreed && current.termsVersion === body.termsVersion) {
        return current;
    }
    return {
        consentType: current.consentType,
        agreed: true,
        termsVersion: body.termsVersion,
        agreedAt: now,
        withdrawnAt: null,
    };
}
