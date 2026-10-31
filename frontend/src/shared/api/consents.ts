// 동의 API. GET /api/me/consents · PUT /api/me/consents/{consentType}. 응답은 백엔드 ConsentResponse 그대로.
// 앱이 바꾸는 것은 위치기반서비스 동의뿐이다. 공용 후보 동의(PUBLIC_CANDIDATE_CONTRIBUTION)는 타입에만 있다.
// 동의(agreed=true)할 때는 서버가 준 currentTermsVersion 을 보낸다. 서버는 현재 버전과 다르면 400 이다.
// 화면이 버전을 모르면(미리 읽기 실패 등) PUT 전에 GET 을 한 번 해서 얻고, 그래도 없으면 예외를 던진다.
// 실패는 클라이언트의 ApiError 를 그대로 던진다.
// 목 모드(API 주소 없음)에서는 서버를 부르지 않는다. 시작 상태는 MOCK_SCENARIO.locationConsent,
// 서버 현재 버전은 locationTermsVersion, 저장 결과는 consentSaveResult. 목 저장이 성공하면 이번 실행 동안 메모리에 남아 이후 목 조회에 보인다.
// 저장이 성공하면 위치 설정 신호를 낸다(지오펜스를 다시 등록하거나 해제한다).
import { ApiError, request } from "./client";
import { isApiConfigured } from "./config";
import { toCurrentTermsVersion } from "./mappers/consentMapper";
import { notifyLocationSettingsChanged } from "../permissions/locationSettings";
import { MOCK_SCENARIO } from "./mock";

export type ConsentType =
    | "LOCATION_BASED_SERVICE"
    | "PUBLIC_CANDIDATE_CONTRIBUTION";

// 날짜는 ISO-8601 UTC. 한 번도 동의하지 않았으면 termsVersion · agreedAt · withdrawnAt 이 null 이다.
// 철회했으면 agreed 는 false, 나머지는 마지막 동의 기록 그대로 withdrawnAt 이 채워진다.
// currentTermsVersion 은 그 유형의 서버 현재 약관 버전이다. 서버는 늘 채우고, 필드가 없는 옛 서버만 null(undefined)이다.
export type ConsentResponse = {
    consentType: ConsentType;
    agreed: boolean;
    termsVersion: string | null;
    currentTermsVersion: string | null;
    agreedAt: string | null;
    withdrawnAt: string | null;
};

// 철회할 때 서버는 termsVersion 을 보지 않는다.
type ConsentRequest =
    | { agreed: true; termsVersion: string }
    | { agreed: false };

const LOCATION: ConsentType = "LOCATION_BASED_SERVICE";
const MOCK_DELAY_MS = 600;
// 목 시작 상태(agreed · withdrawn)의 동의 기록 버전과 공용 후보 동의의 목 현재 버전.
const MOCK_AGREED_TERMS_VERSION = "v1";

export async function fetchConsents(): Promise<ConsentResponse[]> {
    if (!isApiConfigured) {
        await mockDelay();
        // 서버와 같은 순서(PUBLIC_CANDIDATE_CONTRIBUTION, LOCATION_BASED_SERVICE).
        return [
            neverConsent(
                "PUBLIC_CANDIDATE_CONTRIBUTION",
                MOCK_AGREED_TERMS_VERSION,
            ),
            getMockLocationConsent(),
        ];
    }
    return request<ConsentResponse[]>("/api/me/consents");
}

// currentTermsVersion: 화면이 읽어 둔 서버 현재 버전. 동의할 때만 쓰고, 없으면 여기서 다시 읽는다.
export async function setLocationConsent(
    agreed: boolean,
    currentTermsVersion?: string | null,
): Promise<ConsentResponse> {
    const body: ConsentRequest = agreed
        ? {
              agreed: true,
              termsVersion:
                  currentTermsVersion ?? (await readLocationTermsVersion()),
          }
        : { agreed: false };
    if (!isApiConfigured) {
        await mockDelay();
        if (MOCK_SCENARIO.consentSaveResult === "fail") {
            throw new Error("mock consent save failed");
        }
        mockLocationConsent = applyMockChange(getMockLocationConsent(), body);
        notifyLocationSettingsChanged();
        return mockLocationConsent;
    }
    const response = await request<ConsentResponse>(
        `/api/me/consents/${LOCATION}`,
        { method: "PUT", body },
    );
    notifyLocationSettingsChanged();
    return response;
}

// 서버 현재 버전을 다시 읽는다. 읽지 못하거나(예외 그대로) 값이 없으면 동의를 보낼 수 없다.
async function readLocationTermsVersion(): Promise<string> {
    const version = toCurrentTermsVersion(await fetchConsents(), LOCATION);
    if (version == null) {
        throw new Error("위치기반서비스 약관의 현재 버전을 알 수 없습니다.");
    }
    return version;
}

// 지오펜스 목이 쓴다. 서버처럼 철회되지 않은 위치 동의가 있는지만 보고 약관 버전은 보지 않는다.
export function isMockLocationConsentActive(): boolean {
    return getMockLocationConsent().agreed;
}

function mockDelay() {
    return new Promise((resolve) => setTimeout(resolve, MOCK_DELAY_MS));
}

function neverConsent(
    type: ConsentType,
    currentTermsVersion: string,
): ConsentResponse {
    return {
        consentType: type,
        agreed: false,
        termsVersion: null,
        currentTermsVersion,
        agreedAt: null,
        withdrawnAt: null,
    };
}

// 목 동의 기록. 현재 버전은 읽을 때마다 MOCK_SCENARIO 에서 붙인다.
let mockLocationConsent: ConsentResponse | undefined;

function getMockLocationConsent(): ConsentResponse {
    const currentTermsVersion = MOCK_SCENARIO.locationTermsVersion;
    if (mockLocationConsent == null) {
        const state = MOCK_SCENARIO.locationConsent;
        const now = new Date().toISOString();
        mockLocationConsent =
            state === "never"
                ? neverConsent(LOCATION, currentTermsVersion)
                : {
                      consentType: LOCATION,
                      agreed: state === "agreed",
                      termsVersion: MOCK_AGREED_TERMS_VERSION,
                      currentTermsVersion,
                      agreedAt: now,
                      withdrawnAt: state === "withdrawn" ? now : null,
                  };
    }
    return { ...mockLocationConsent, currentTermsVersion };
}

// 서버(MemberConsentService.change)와 같게 바꾼다. 현재 버전이 아닌 동의는 400,
// 같은 버전 재동의 · 동의 없는 철회는 그대로 둔다.
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
    if (body.termsVersion !== current.currentTermsVersion) {
        throw new ApiError(
            400,
            "INVALID_REQUEST",
            "요청 값이 올바르지 않습니다.",
        );
    }
    if (current.agreed && current.termsVersion === body.termsVersion) {
        return current;
    }
    return {
        consentType: current.consentType,
        agreed: true,
        termsVersion: body.termsVersion,
        currentTermsVersion: current.currentTermsVersion,
        agreedAt: now,
        withdrawnAt: null,
    };
}
