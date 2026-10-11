// 앱 모델. 동의 상태. 서버 응답(shared/api/consents)에서 mappers/consentMapper 가 계산한다.
// never: 한 번도 동의하지 않음. agreed: 동의 중. withdrawn: 동의했다가 철회함.

export type ConsentState = "never" | "agreed" | "withdrawn";
