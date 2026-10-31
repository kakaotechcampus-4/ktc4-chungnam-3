// API 기본 주소. EXPO_PUBLIC_API_BASE_URL(번들에 들어간다. 바꾸면 Metro 를 다시 시작한다).
// 비어 있으면 목 모드다. 서버를 부르지 않고 목 로그인 · 목 세션(MOCK_SCENARIO)으로 동작한다.
// EXPO_PUBLIC_FORCE_MOCK 이 "1" 이면 주소가 있어도 목 모드다(시연 백업용 eas.json preview-mock 프로필. preview 환경의 주소를 무시한다).
// 두 값 모두 인라인되도록 process.env.이름 으로 정적으로 읽는다.
// session.ts 와 client.ts 가 같이 읽는다. session.ts 가 client.ts 를 import 하지 않게 따로 둔다.
const forceMock = process.env.EXPO_PUBLIC_FORCE_MOCK === "1";

// 시연 도구가 "목 모드(preview-mock)" 를 가르는 데 쓴다.
export const isMockForced = forceMock;

export const API_BASE_URL = forceMock
    ? undefined
    : process.env.EXPO_PUBLIC_API_BASE_URL?.trim().replace(/\/+$/, "") ||
      undefined;

export const isApiConfigured = API_BASE_URL != null;
