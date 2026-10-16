// API 기본 주소. EXPO_PUBLIC_API_BASE_URL(번들에 들어간다. 바꾸면 Metro 를 다시 시작한다).
// 비어 있으면 목 모드다. 서버를 부르지 않고 목 로그인 · 목 세션(MOCK_SCENARIO)으로 동작한다.
// session.ts 와 client.ts 가 같이 읽는다. session.ts 가 client.ts 를 import 하지 않게 따로 둔다.
export const API_BASE_URL =
    process.env.EXPO_PUBLIC_API_BASE_URL?.trim().replace(/\/+$/, "") ||
    undefined;

export const isApiConfigured = API_BASE_URL != null;
