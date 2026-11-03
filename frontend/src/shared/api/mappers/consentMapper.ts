// 서버 동의 목록을 앱의 동의 상태로 변환.

import type { ConsentState } from "../../../domain/consent";
import type { ConsentResponse, ConsentType } from "../consents";

// 항목이 없거나 termsVersion 이 null 이면 한 번도 동의하지 않은 것이다.
// 동의 중인데 termsVersion 이 서버의 현재 버전과 다르면 outdated(다시 동의가 필요하다).
// 현재 버전을 모르면(필드가 없는 옛 서버) 비교할 수 없어 agreed 로 본다.
export function toConsentState(
    consents: readonly ConsentResponse[],
    type: ConsentType,
): ConsentState {
    const consent = consents.find((item) => item.consentType === type);
    if (consent == null || consent.termsVersion == null) return "never";
    if (!consent.agreed) return "withdrawn";
    const current = consent.currentTermsVersion ?? null;
    return current != null && consent.termsVersion !== current
        ? "outdated"
        : "agreed";
}

// 그 유형의 서버 현재 약관 버전. 동의(PUT agreed=true)할 때 보낸다. 모르면 null.
export function toCurrentTermsVersion(
    consents: readonly ConsentResponse[],
    type: ConsentType,
): string | null {
    const consent = consents.find((item) => item.consentType === type);
    return consent?.currentTermsVersion ?? null;
}

// 동의 중일 때 동의한 시각. 동의 중이 아니면 null.
export function toConsentAgreedAt(
    consents: readonly ConsentResponse[],
    type: ConsentType,
): Date | null {
    const consent = consents.find((item) => item.consentType === type);
    if (consent == null || !consent.agreed || consent.agreedAt == null) {
        return null;
    }
    return new Date(consent.agreedAt);
}
