// 서버 동의 목록을 앱의 동의 상태로 변환.

import type { ConsentState } from "../../../domain/consent";
import type { ConsentResponse, ConsentType } from "../consents";

// 항목이 없거나 termsVersion 이 null 이면 한 번도 동의하지 않은 것이다.
export function toConsentState(
    consents: readonly ConsentResponse[],
    type: ConsentType,
): ConsentState {
    const consent = consents.find((item) => item.consentType === type);
    if (consent == null || consent.termsVersion == null) return "never";
    return consent.agreed ? "agreed" : "withdrawn";
}
