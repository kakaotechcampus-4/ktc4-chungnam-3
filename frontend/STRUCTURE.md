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
├── index.js                  RN 엔트리. 앱 등록 + (runtime 작업 때) 백그라운드 태스크 등록
├── assets/
│   └── fonts/                Noto Sans KR 서브셋 ttf + OFL.txt
├── scripts/
│   └── subset-fonts.py       폰트 서브셋 생성 (원본 출처 · 범위 · 실행 방법은 파일 상단)
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
    │   ├── archive/          전체 기억 탭. 시간순 앨범
    │   │   └── components/
    │   ├── proposal/         근처 탭. 지금 위치 근처의 저장물
    │   │   └── components/
    │   ├── save-result/      저장 결과 모달. 분석 중 / 확인 필요 / 장소 없음 / 실패 상태별 렌더
    │   │   └── components/
    │   ├── content-detail/   장소 상세 · 지도
    │   │   └── components/
    │   ├── execution-trace/  "왜 에이전트인가" 증명 화면
    │   └── map-view/         (08 지도와 역할 겹침, 미정)
    │
    └── shared/
        ├── api/
        │   ├── client.ts
        │   ├── mappers/      ★ 서버 응답 -> 앱 모델 변환. 백엔드 변경 흡수 지점
        │   └── mock/         UI 개발용 목 데이터. 화면 props 모양. 서버 계약 아님
        ├── storage/
        ├── external-links/   지도 딥링크 (단순 URL 빌더)
        └── ui/               두 개 이상 feature 가 쓰는 공통 컴포넌트
            └── theme/        Figma 토큰 + metrics
```

폰트:

- Noto Sans KR(400 · 500)은 한자를 뺀 서브셋을 `assets/fonts/` 에 직접 번들한다.
  한글 · 라틴 · 기호는 모두 남긴다. `scripts/subset-fonts.py` 로 다시 만든다.
- Gowun Dodum 은 `@expo-google-fonts` 패키지를 쓴다. 화면 제목이 확정되면 제목 글자만 남기는 서브셋으로 바꾼다.

## 컴포넌트 위치

- 두 개 이상의 feature 에서 쓰면 `shared/ui/`, 한 feature 에서만 쓰면 `features/*/components/`.
- `app/navigation` 이 탭바로 쓰는 TopTabs 는 `shared/ui/` 에 둔다.

## 테마 토큰

- 기준은 Figma 의 `C · Color`, `C · Dimension` 컬렉션과 `C 바랜기억/` 텍스트·effect 스타일이다.
  같은 이름을 가진 다른 컬렉션(`Color`, `A ·`, `B ·`)은 이전 산출물이라 쓰지 않는다.
- 이름은 그대로 쓴다. 슬래시는 객체 중첩, 하이픈은 camelCase 로만 바꾼다.
  (`bg/screen` → `colors.bg.screen`, `brand/primary-pressed` → `colors.brand.primaryPressed`, `space/md` → `spacing.md`)
- 스타일은 `C 바랜기억/` 접두어를 뗀 이름을 camelCase 로 쓴다. (`Heading/Screen` → `typography.headingScreen`)
- `fade/*` 는 퍼센트다. 100 으로 나눠 opacity 로 쓴다.
- headingScreen(Gowun Dodum)은 고정 문구와 숫자만 그린다. 장소명·영상 제목 같은 동적 텍스트에 쓰지 않는다.
  서브셋 이후 빠진 글자가 기본 글꼴로 섞인다.
- `metrics.ts` 는 **Figma 변수가 아니다.** 변수에 바인딩되지 않은 노드 실측값을 컴포넌트별 키로 둔다. hitSlop 도 여기 둔다.
- `features/`, `shared/ui/`, `app/` 에는 색·간격·radius·타이포를 리터럴로 쓰지 않는다.

## 내비게이션

```
RootStack
├── Main            상단 탭 (근처 | 전체 기억). 초기 탭은 근처
├── ContentDetail   push
└── SaveResult      modal
```

- linking prefix 는 `Linking.createURL('/')` 로 만든다. scheme 을 하드코딩하지 않는다.
- SaveResult(02~05)는 알림 탭 또는 전체 기억 항목 탭으로 진입한다. 실제 알림 연결은 runtime 작업 범위다.

## 목 데이터

- `shared/api/mock/` 은 화면 props 모양 그대로 둔다. 서버 응답 계약처럼 만들지 않는다.
- UI 단계 동안 features 는 mock 에서 직접 받는다. `client.ts` 와 `mappers/` 를 거치지 않는다.
- mock 은 features 의 타입을 import 하지 않는다(shared → features 금지). 구조적 타입으로 맞춘다.
- 목 이미지 URL(picsum 고정 id)은 이 폴더 안에만 둔다.

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
- `features/` 는 `app/` 을 import 하지 않는다(`import type` 포함).
  내비게이션 타입은 `routes.ts` 의 `ReactNavigation.RootParamList` 전역 선언으로 받고,
  라우트 이름은 문자열 리터럴로 쓴다.

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
| 분석 진행 상태 (ANALYZING / READY) | 백엔드에 없음. `ExtractionStatus` 는 완료 후 결과 등급이지 진행 상태가 아니다 | 대기 UI(03 분석 중) 의 근거 |
| 장소 판정 결과 (확정 / 확인필요 / 없음) | 백엔드에 없음. `uncertainties: string[]` 자유 문자열뿐 | 되묻기 화면(02 확인 필요 · 04 장소 없음) 의 근거 |
| 카테고리 | 팀 합의: 고정 enum 소수 + 분위기 태그 다수. enum 값은 미확정 | 01 필터 칩 라벨(전체/식당/카페/가볼 곳)은 enum 확정 전까지 목 데이터 한정 |
| 비공개·삭제 영상의 링크 보관 여부 | 서버 저장 정책 문제. 백엔드 합의 필요. 05 디자인은 "링크는 그대로 보관" 으로 안내한다 | (b) "다시 시도" 가 모든 실패에 똑같이 노출된다. 재시도가 의미 없는 비공개·삭제(`VIDEO_ACCESS`)에도 보인다. 재시도 가능 여부를 서버가 내려줘야 버튼 노출을 가를 수 있다. (a) 05 를 닫은 뒤 앱 안에서 다시 열 경로가 없다. 07 전체 기억에 "실패" 배지 셀이 없어, 알림을 놓치면 실패한 저장물을 확인할 수 없다. 디자인 보완이 필요하다 |
| PARTIAL 표현 | 디자인에 "부분 성공" 배지 상태만 있고 화면 없음 | 무엇이 빠졌는지 보여줄 곳이 없다 |
| 꺼내기 제안 유형 | 문서상 가설이 5개 (장소 1곳 / 코스 / 장소 없는 콘텐츠 재노출 / 공용 풀 보충 / 침묵) | **SINGLE·COURSE 2분기로 부족하다.** 열린 형태로 둔다 |
| 알림 발생원 (FCM / 로컬) | 백엔드에 FCM 의존성 없음 | `runtime/notifications/notificationListener.ts` 안에서만 갈린다 |
| 완료 통지 방식 (폴링 / SSE / 푸시) | 없음 | **가장 시급.** 이것 없이는 저장 플로우가 완성되지 않는다 |
| 인증 | 없음 (의존성조차 없음) | API 클라이언트 인터셉터 |
| 지오펜스 보고 엔드포인트 | 문서에만 서술. 시그니처 없음 | 서버가 조용히 무시할 수 있어 프론트가 결과를 알 방법이 필요 |

## 화면으로 만들 것 vs 상태로 표현할 것

공유 시 앱은 뜨지 않는다. 저장 결과는 알림이나 전체 기억 항목에서 여는
저장 결과 모달(02~05)로 본다.

| 경우 | 표현 |
|---|---|
| 지원하지 않는 링크 | 알림 문구. 저장하지 않음 |
| 비공개·삭제 영상 (`FAILED` / `VIDEO_ACCESS`) | 알림 문구. 저장 여부 미확정 (미확정 표 참조) |
| 분석 실패 | 05 저장 결과 모달 + 전체 기억 배지 |
| 부분 성공 (`PARTIAL`) | 디자인에는 배지 상태만 있고 화면 없음. 미정 |
| 장소 후보 없음 (`SUCCESS` + 빈 배열) | 04 저장 결과 모달 + 전체 기억 배지 |
| 분석 중 | 03 저장 결과 모달 + 전체 기억 배지 |
| 장소 후보 여러 개 | 02 저장 결과 모달 (기존 확인 화면을 흡수) |
| 저장물 0개 | 07b 전체 기억 빈 상태. 신규 사용자에게는 사실상 튜토리얼 |
| 근처에 저장물 없음 | 06 근처 빈 상태 |
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
