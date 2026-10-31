// 앱 모델. 동의 상태. 서버 응답(shared/api/consents)에서 mappers/consentMapper 가 계산한다.
// never: 한 번도 동의하지 않음. agreed: 현재 약관에 동의 중. withdrawn: 동의했다가 철회함.
// outdated: 동의 중이지만 예전 약관 버전이다. 다시 동의를 받기 전까지 동의하지 않은 것과 같이 다룬다.

export type ConsentState = "never" | "agreed" | "outdated" | "withdrawn";
