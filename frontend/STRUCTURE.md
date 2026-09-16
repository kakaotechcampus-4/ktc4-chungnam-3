# REMEMBRALL 프론트엔드 구조

모노레포의 `frontend/` 가 RN 프로젝트 루트다.
`package.json`, `metro.config.js`, `app.json`, `android/` 가 모두 이 아래에 생긴다.

## 전제: 백엔드 계약은 아직 없다

`develop` 기준으로 backend 에는 Controller · Entity · Repository · Service 가 하나도 없다.
존재하는 것은 Spring Boot 스켈레톤과 YouTube 추출 결과 DTO 7개, enum 3개뿐이다.
그 DTO 도 **모듈 간 내부 반환 타입**이지 HTTP 응답 바디라는 근거가 없다.

따라서 이 구조는 **백엔드 계약이 확정되기 전에도 화면을 만들 수 있고,
확정된 뒤에 고치는 범위가 좁도록** 짠다. 원칙은 하나다.

> 백엔드가 이름을 바꿔도 `features/` 는 건드리지 않는다.

이를 위해 타입을 두 겹으로 나누고 그 사이에 매핑을 둔다.

```
서버 JSON  ──> domain/extraction/  ──[mappers]──>  domain/  ──> features/
              (백엔드에 실재)                      (우리 앱 모델)
```

- `domain/extraction/` : 백엔드 코드에 **실제로 있는 것만** 옮긴다. 창작 금지.
- `domain/` : 화면이 쓰는 모델. 우리가 정한 이름이며 백엔드와 다를 수 있다.
- `shared/api/mappers/` : 둘 사이 변환. 백엔드가 바뀌면 **여기만 고친다.**

## 디렉토리

```
frontend/
├── index.js                  RN 엔트리. 앱 등록 + 백그라운드 태스크 등록만.
├── android/
│   └── app/src/main/java/com/remembrall/share/
│       └── ShareActivity.kt  투명 공유 수신 액티비티 (앱 UI 안 띄움)
└── src/
    ├── app/                  앱 셸
    │   ├── App.tsx
    │   └── navigation/       RootNavigator, linking, routes
    │
    ├── domain/
    │   ├── extraction/       ★ 백엔드에 실재하는 타입만 (확정)
    │   │   ├── status.ts         ExtractionStatus, EvidenceSource, FailureStage
    │   │   └── extractionResult.ts   DTO 7개 그대로
    │   │
    │   ├── content.ts        앱 모델. 저장물 + 분석 상태
    │   ├── place.ts          앱 모델. 장소
    │   ├── proposal.ts       앱 모델. 꺼내기 결과
    │   ├── notification.ts   알림 페이로드 (발생원 무관)
    │   └── executionTrace.ts 에이전트 궤적
    │
    ├── runtime/              React 밖
    │   ├── background/       headlessTask, pendingQueue(콜드스타트 큐)
    │   ├── geofence/         OS 펜스 등록 + ENTER 서버 보고만
    │   ├── notifications/    channels, listener(발생원 은닉), router
    │   ├── permissions/      권한 상시 감시 (자동 회수 대응)
    │   └── share/
    │       └── supportedLinks/   [확장 포인트] 플랫폼별 파서 레지스트리
    │
    ├── features/             화면
    │   ├── onboarding/
    │   ├── archive/          홈. 저장 결과 확인의 유일한 창구
    │   ├── content-detail/   상태별 렌더
    │   ├── confirmation/     장소 후보 선택
    │   ├── proposal/         꺼내기 결과 통합 화면
    │   ├── execution-trace/  "왜 에이전트인가" 증명 화면
    │   └── map-view/
    │
    └── shared/
        ├── api/
        │   ├── client.ts
        │   └── mappers/      ★ 서버 응답 -> 앱 모델 변환. 백엔드 변경 흡수 지점
        ├── storage/
        ├── external-links/   지도 딥링크 (단순 URL 빌더)
        └── ui/               EmptyState, ErrorState 등 공통
```

## 의존 방향

```
features/ ──┐
            ├──> domain/  <──── runtime/
shared/  ───┘
```

- `runtime/` 은 `features/` 와 `app/navigation` 을 **import 하지 않는다.**
  백그라운드 이벤트는 앱 프로세스가 죽은 상태에서도 들어오기 때문이다.
  화면을 여는 대신 부작용만 남기고 끝낸다.
- `features/` 끼리는 서로 import 하지 않는다. 공유가 필요하면 `domain/` 이나 `shared/` 로 올린다.
- `domain/` 은 아무것도 import 하지 않는다. 순수 타입만 둔다.
- `features/` 는 `domain/extraction/` 을 직접 import 하지 않는다. 항상 매핑을 거친다.

## 확정된 것 (백엔드 코드에 실재)

```ts
type ExtractionStatus = 'SUCCESS' | 'PARTIAL' | 'FAILED'
type EvidenceSource   = 'VIDEO_AUDIO' | 'VIDEO_TEXT' | 'VIDEO_VISUAL' | 'TITLE' | 'DESCRIPTION'
type FailureStage     = 'METADATA_FETCH' | 'VIDEO_ACCESS' | 'GEMINI_CALL' | 'RESPONSE_MAPPING'
```

여기서 따라오는 화면 요구사항:

- **`PARTIAL` 이 있다.** 성공 / 실패 2분기로 UI 를 짜면 안 된다. 부분 성공 화면이 필요하다.
- **분석 실패와 장소 후보 없음은 다른 화면이다.** 문서에 명시돼 있다.
  빈 `placeCandidates` + `SUCCESS` 와 `FAILED` 는 사용자가 할 행동이 다르다.
- **`candidateId` 는 전역 장소 키가 아니다.** 한 추출 결과 안에서만 유효한 임시 ID다.
  라우팅 파라미터나 캐시 키로 쓰면 안 된다. 전역 장소 엔티티는 아직 존재하지 않는다.
- **목록 필드는 빈 배열 보장**이 문서 규칙이나, validation 애노테이션이 코드에 0개라
  강제되지 않는다. 방어 코드를 둔다.

## 미확정 (앱 모델은 가설이다)

아래는 `domain/` 에 타입을 만들어 두되, **백엔드 합의 전까지 확정이 아니다.**
해당 파일 상단에 미확정임을 주석으로 남긴다.

| 항목 | 현재 상태 | 영향 |
|---|---|---|
| 분석 진행 상태 (ANALYZING / READY) | 백엔드에 없음. `ExtractionStatus` 는 완료 후 결과 등급이지 진행 상태가 아니다 | 대기 UI 의 근거 |
| 장소 판정 결과 (확정 / 확인필요 / 없음) | 백엔드에 없음. `uncertainties: string[]` 자유 문자열뿐 | 되묻기 화면의 근거 |
| 카테고리 | 백엔드에 없음. 검색은 pgvector 임베딩 기반으로 계획됨 | **필터 칩 UI 의 근거가 없다.** 아카이브를 카테고리 전제로 짜지 않는다 |
| 꺼내기 제안 유형 | 문서상 가설이 5개 (장소 1곳 / 코스 / 장소 없는 콘텐츠 재노출 / 공용 풀 보충 / 침묵) | **SINGLE·COURSE 2분기로 부족하다.** 열린 형태로 둔다 |
| 알림 발생원 (FCM / 로컬) | 백엔드에 FCM 의존성 없음 | `runtime/notifications/notificationListener.ts` 안에서만 갈린다 |
| 완료 통지 방식 (폴링 / SSE / 푸시) | 없음 | **가장 시급.** 이것 없이는 저장 플로우가 완성되지 않는다 |
| 인증 | 없음 (의존성조차 없음) | API 클라이언트 인터셉터 |
| 지오펜스 보고 엔드포인트 | 문서에만 서술. 시그니처 없음 | 서버가 조용히 무시할 수 있어 프론트가 결과를 알 방법이 필요 |

## 화면으로 만들 것 vs 상태로 표현할 것

공유 시 앱이 뜨지 않으므로, 실패 경로 대부분은 화면이 아니라
알림 문구 + 아카이브 항목 상태로 흡수된다.

| 경우 | 표현 |
|---|---|
| 지원하지 않는 링크 | 알림 문구. 저장하지 않음 |
| 비공개·삭제 영상 (`FAILED` / `VIDEO_ACCESS`) | 알림 문구. 저장하지 않음 |
| 부분 성공 (`PARTIAL`) | 아카이브 배지 + 상세에 무엇이 빠졌는지 표시 |
| 장소 후보 없음 (`SUCCESS` + 빈 배열) | 아카이브 배지 + 상세에서 직접 장소 지정 |
| 분석 중 | 아카이브 배지 + 상세의 대기 상태 |
| 장소 후보 여러 개 | **화면** (확인 화면) |
| 저장물 0개 | 아카이브의 빈 상태. 신규 사용자에게는 사실상 튜토리얼 |
| 제안 성립 안 함 | 알림이 오지 않는다. 화면 없음 |

## 설계 근거

**트리거 확장은 서버 몫이다.** 지오펜스 진입 후 사전 게이트, 에이전트 기동,
최종 검증이 모두 서버에서 일어난다. 앱은 펜스를 등록하고 진입을 보고할 뿐이다.
따라서 앱 쪽에 트리거 종류별 디렉토리를 두지 않는다.
앱에서 실제로 늘어나는 것은 **지원 플랫폼**과 **알림 종류** 두 가지뿐이다.

**지도 연동에 인터페이스를 얹지 않는다.** 즐겨찾기 자동등록 API 가
구글 · 카카오 · 네이버 모두 없으므로 "동기화" 개념 자체가 성립하지 않는다.
할 수 있는 일은 URL 을 만들어 여는 것뿐이라 순수 함수로 충분하다.

**영업 여부와 이동시간은 캐시하지 않는다.** 저장 시점에 확정하지 않고
꺼내기 시점에 조회하는 값이다. 화면 재진입 시 스냅샷을 재사용하지 않는다.

**알림 채널을 나눈다.** 하나로 묶으면 사용자가 저장 알림을 끄는 순간
제안 알림까지 같이 죽는다. 앱의 존재 이유가 부수 알림 때문에 꺼진다.
