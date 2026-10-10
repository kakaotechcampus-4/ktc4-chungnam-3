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
├── app.json                  정적 앱 설정. EAS projectId · owner 포함
├── app.config.ts             app.json 을 받아 빌드 시점 비밀값(Google Maps 키)을 플러그인에 넣는다
├── eas.json                  EAS 빌드 프로필. development = 개발 빌드(dev client) APK
├── .prettierrc               prettier 설정(tabWidth 4, LF). 버전은 package.json 에 고정
├── .prettierignore           포맷하지 않는 생성물(package-lock.json, android/ 등)
├── .editorconfig             prettier 를 쓰지 않는 에디터용. .prettierrc 와 같은 값
├── .gitattributes            frontend 텍스트 파일을 LF 로 받는다
├── assets/
│   ├── fonts/                Noto Sans KR 서브셋 ttf + OFL.txt
│   └── images/providers/     로그인 제공자 공식 심볼. 공식 에셋에서 심볼 경로만 남긴 파일(직접 그리지 않는다)
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
    │   ├── consent.ts        앱 모델. 동의 상태(never · agreed · withdrawn)
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
    │   ├── onboarding/       첫 실행 흐름. 소개 · 로그인 · 권한(알림 · 위치 2단계) · 첫 저장 안내(00a~00f)
    │   │   └── components/
    │   ├── archive/          기억 탭. 시간순 앨범
    │   │   └── components/
    │   ├── proposal/         근처 탭. 지금 위치 근처의 저장물
    │   │   └── components/
    │   ├── save-result/      저장 결과 모달. 분석 중 / 확인 필요 / 장소 없음 / 실패 상태별 렌더 + 장소 직접 찾기(02c · 02d)
    │   │   └── components/
    │   ├── content-detail/   장소 상세 · 지도. 끌 수 있는 시트(기본 · 펼침)
    │   │   └── components/
    │   ├── settings/         설정(11). 알림 · 위치 · 계정 · 앱 정보
    │   │   └── components/
    │   ├── execution-trace/  "왜 에이전트인가" 증명 화면
    │   └── map-view/         지도 탭(10). 사진 핀 · 요약 시트
    │       └── components/
    │
    └── shared/
        ├── auth/             로그인 · 세션
        │   ├── session.ts        세션 저장(안전한 저장소) · 메모리 캐시 · 구독(useSession)
        │   ├── providers.ts      [확장 포인트] 로그인 제공자 목록(켜짐 · 버튼 · 엔드포인트 · 요청 바디)
        │   ├── login.ts          로그인 흐름 · 게스트 세션 조용히 다시 받기
        │   ├── guest.ts          게스트 로그인(MVP). 설치 id 만들기 · 보관
        │   └── kakao.ts          카카오 로그인(MVP 이후. 지금은 목)
        ├── permissions/      알림 · 위치 권한 조회와 요청(온보딩 · 설정이 쓴다)
        │   ├── notifications.ts  알림 권한 조회 · 요청 · 시스템 알림 설정 열기
        │   ├── location.ts       위치 권한 조회(always · whileInUse · denied) · 앱 사용 중 · 백그라운드 요청
        │   └── usePermissionStatus.ts  진입 · 앞으로 돌아올 때 다시 읽는 hook
        ├── api/
        │   ├── config.ts     API 기본 주소(EXPO_PUBLIC_API_BASE_URL). 비면 목 모드
        │   ├── client.ts     기본 주소 · Bearer 헤더 · ApiError · 401 처리
        │   ├── consents.ts   동의 API(조회 · 위치 동의 변경) · 목 분기
        │   ├── consentTerms.ts   약관 버전 · 약관 URL 상수
        │   ├── mappers/      ★ 서버 응답 -> 앱 모델 변환. 백엔드 변경 흡수 지점
        │   └── mock/         UI 개발용 목 데이터. 화면 props 모양. 서버 계약 아님
        ├── storage/
        │   └── secureStore.ts    안전한 저장소(expo-secure-store) 지연 로더. 세션 · 설치 id
        ├── external-links/   지도 딥링크 (단순 URL 빌더)
        │   └── openExternalUrl.ts  웹 페이지(약관) 열기. expo-web-browser 지연 로드, 실패하면 Linking
        └── ui/               두 개 이상 feature 가 쓰는 공통 컴포넌트
            └── theme/        Figma 토큰 + metrics
```

폰트:

- Noto Sans KR(400 · 500)은 한자를 뺀 서브셋을 `assets/fonts/` 에 직접 번들한다.
  한글 · 라틴 · 기호는 모두 남긴다. `scripts/subset-fonts.py` 로 다시 만든다.
- Gowun Dodum 은 `@expo-google-fonts` 패키지를 쓴다. 화면 제목이 확정되면 제목 글자만 남기는 서브셋으로 바꾼다.

## 컴포넌트 위치

- 두 개 이상의 feature 에서 쓰면 `shared/ui/`, 한 feature 에서만 쓰면 `features/*/components/`.
- `app/navigation` 이 하단 바로 쓰는 BottomNav 는 `shared/ui/` 에 둔다.
- 온보딩 전용: OnboardingLayout(제목 · 본문 · 버튼 영역 틀), PhotoStack(00a · 00b 일러스트), SocialLoginButton(00b, Provider 변형), LoginError(00b-err), NotificationPreview(00c), PermissionStep(00d · 00e), GuideRow(00f). 모두 `features/onboarding/components/`.
- 두 feature 이상이 쓰는 것: ScreenHeader(근처 · 기억 헤더), Chip(근처 · 지도 필터), PhotoMarker · PinLabel · CurrentLocation(지도 탭 · 08 지도), SheetHandle(08 · 10b 시트), NotFound(저장 결과 · 장소 상세), LocationConsentSheet(00d · 06b · 설정 11 의 위치 정보 이용 동의 시트. 앱 루트 BottomSheetModalProvider 위에 뜬다).
- PhotoMarker(핀)와 PinLabel(이름표)은 별도 마커다. Android 는 마커 뷰를 그 크기의 비트맵으로 찍으므로 핀 비트맵 크기는 선택과 무관하게 고정한다(그림자 있는 핀은 그림자 여백 포함). 다시 찍기는 `redraw()` 로 요청하고 화면에 다시 포커스가 오면 다시 찍는다. `tracksViewChanges` 는 자식 스타일 변화를 다시 찍는다는 보장이 없다.
- 지도 자식(마커)은 화면이 숨겨진 동안 추가 · 제거 · 재배치되지 않게 한다. react-native-maps(Android)는 지도가 화면에서 떨어지면 마커 목록을 비웠다가 다시 붙을 때 복원하므로, 그 사이의 구조 변경이 어긋난다. 그래서 마커 zIndex 는 바꾸지 않고(Fabric 이 재배치한다), 이름표는 지도마다 하나를 계속 두고 opacity 로 숨긴다.
- 지도 스타일은 Figma 색 토큰으로 만든 값이라 `shared/ui/theme/mapStyle.ts` 에 둔다.

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
- 테두리는 크기에 포함하지 않는다. Figma padding 에서 테두리 두께를 빼서 둔다(Chip · PlaceOption · SearchField · 08 원본 영상 행).

## 내비게이션

```
RootStack           인증 상태에 따라 Onboarding 또는 아래 화면들 중 하나만 등록한다
├── Onboarding      로그인 전 · 첫 실행 흐름. 하단 바 없음
│   ├── Intro                   00a 소개
│   ├── Login                   00b 로그인 (00b-err 은 화면 상태)
│   ├── NotificationPermission  00c 알림 권한
│   ├── LocationPermission      00d 위치 1단계(앱 사용 중)
│   ├── BackgroundLocation      00e 위치 2단계(설정에서 항상 허용)
│   └── FirstSave               00f 첫 저장 안내
├── Main            하단 탭 (근처 | 지도 | 기억). 초기 탭은 근처
├── Settings        push. 하단 바 없음. 근처 · 기억 헤더의 톱니로 진입
├── ContentDetail   push
└── SaveResult      modal. 안에 중첩 스택
    ├── Result          02~05 · 02b
    └── PlaceSearch     push (02c · 02d)
```

- 진입: 저장된 세션이 있으면 바로 근처 탭, 없으면 00a 부터. 로그인 없이 쓰는 모드는 없다.
  세션을 읽는 동안은 스플래시를 유지한다(폰트와 같은 방식). 00a 가 잠깐 비치지 않게 한다.
- MVP(게스트 세션) 흐름: 00a → 00c → 00d → 00e → 00f → 근처 탭. 00b 는 건너뛴다(등록하지 않는다).
  00a "시작하기"가 설치 id 로 게스트 세션을 조용히 받고 00c 로 간다. 실패하면 00a-err(버튼 위 오류 상자, 상자와 버튼 사이 8).
  설정의 "계정" 묶음은 숨긴다. (Figma 메모 2175:1066 MVP 줄)
- 카카오를 다시 켜면 흐름: 00a → 00b → 00c → 00d → 00e → 00f → 근처 탭. 권한 화면의 "나중에 할게요"는 다음 단계로 넘어간다.
- 00d 에서 "나중에 할게요"를 누르면 00e 를 건너뛴다. '앱 사용 중' 권한 없이는 '항상 허용'을 받을 수 없다.
- 로그인 전(00a · 00b)은 일반 스택이고, 로그인 뒤 권한 단계(00c~00f)는 화면을 교체한다. 권한 단계에서 시스템 뒤로 가기는 앱을 닫는다.
- 로그인에 성공하면 세션은 바로 저장하고, 화면은 00f 를 마칠 때 로그인 상태(Main)로 바꾼다.
  권한 화면 도중 앱이 꺼지면 다음 실행은 세션이 있으므로 근처 탭으로 간다. 권한은 설정(11)에서 다시 바꿀 수 있다.
- 게스트 세션의 401 은 조용히 다시 받는다("API 클라이언트" 절). 다시 받기가 거절되면 세션을 지우고 00a 부터, 카카오 세션의 401 은 00b 부터 보여준다. 로그아웃 상태에서는 Main 쪽 딥링크가 열리지 않는다.
- linking prefix 는 `Linking.createURL('/')` 로 만든다. scheme 을 하드코딩하지 않는다.
- SaveResult(02~05)는 알림 탭 또는 기억 항목 탭으로 진입한다. 실제 알림 연결은 runtime 작업 범위다.
- 02c 에서 저장하면 SaveResult 모달 하나만 닫아 흐름을 끝낸다. 뒤로 가기는 02c → 02 로 간다.
- 08 "이 장소가 아니에요"는 SaveResult 모달에 PlaceSearch 하나만 올린 바꾸기 모드(placeId)다. 바꾸기 · 뒤로 가기 모두 모달을 닫고 08 로 돌아간다. 딥링크는 없다.
- 08 "길 안내 시작"은 동작하지 않는다. 백엔드 협의 이슈 대기.
- 하단 바의 선택 표시는 `bg/subtle` pill 이다. 브랜드 색을 쓰지 않는다.
- 없는 저장 결과 id · 장소 id 로 들어오면 NotFound(12)를 보여준다. "기억 목록으로" → 기억 탭.
- 08 시트 멈춤 지점은 기본 · 펼침이다. 지도 탭에서만 요약(10b) 지점이 추가된다.
- 08 지도에는 이 장소 핀(fade 와 관계없이 40×71 · 흰 테두리 3 · 그림자, 바램은 장소의 fade · 이름표) · 현재 위치 · 현재 위치에서 장소까지의 점선 경로만 그린다.
  현재 위치 · 경로는 위치 권한이 "denied" 일 때만 숨긴다. 경로는 경로 API 가 정해지기 전까지 직선 자리표시다.
  지도 영역은 뒤로가기 아래부터 시트 기본 지점 위까지이며(mapPadding), 그 안에 현재 위치와 장소를 함께 맞춘다(위는 핀 높이, 아래는 이름표 영역만큼 여유).
  다른 저장 장소는 미니 핀(fade 와 관계없이 22×39 · 흰 테두리 2 · 그림자 없음, 바램은 각 장소의 fade, 누름 없음)으로 그린다. 화면 맞추기에는 넣지 않는다.
- 10b 요약 시트는 핀을 누르면 뜬다. 요약을 누르거나 위로 끌면 08 로 가고, 지도 빈 곳 · 아래로 끌기 · 뒤로 가기는 닫는다. 다른 탭으로 옮기면 선택을 풀고, 08 에 다녀오면 선택을 유지한다.
- 06 "{동네} 기억 보기"는 `Main > Map` 에 `area` 를 넘긴다. 지도는 그 동네로 가운데를 잡고 `area` 를 비운다.
- 지도 핀 크기는 fade 로 정한다(recent 40 · weeks 26 · months 22). 위치 권한이 "denied" 면 현재 위치 · 내 위치 버튼 · 도보 시간을 숨긴다.
- 지도 구조: 근처 감지는 OS 지오펜싱, 앱 안 표시는 Google Maps SDK, 장소 좌표는 LocationIQ 지오코딩. UI 단계는 표시와 핀만 한다.
- 시트는 `@gorhom/bottom-sheet`, 지도는 `react-native-maps`(Google provider)로 구현한다.
- 지명 라벨이 핀에 가려지는 것은 Google 지도 기본 동작이라 수정하지 않는다. 마커는 항상 지도 라벨 위에 그려지고, 겹치는 라벨을 숨기는 Advanced Marker 충돌 처리는 react-native-maps 가 지원하지 않는다. 가게 · 교통 · 도로 이름은 이미 끈다.
- 지도(10·08)는 Expo Go SDK 57 안드로이드에서 검은 화면으로 나온다(expo/expo#49323). 우리 Google Maps API 키를 넣은 개발 빌드에서 확인한다.

## 인증 · 세션

- **MVP 인증은 게스트 세션이다.** 로그인 없이 쓰는 모드는 없지만, 사용자가 계정을 고르지 않는다.
  백엔드(구현 예정): `POST /api/auth/guest {installationId}` → `{sessionToken, sessionExpiresAt}`(카카오와 같은 응답).
- 설치 id: 처음 필요할 때(실제 모드의 첫 게스트 로그인) 무작위 UUID v4 로 만들어 안전한 저장소 키 `remembrall.installation-id` 에 둔다.
  난수는 `expo-crypto` 의 `randomUUID()`(안드로이드 `java.util.UUID.randomUUID`, 보안 난수)다. `Math.random` 은 쓰지 않는다.
  이 값으로 그 게스트의 세션을 받을 수 있으므로 로그 · 오류 · 경고에 남기지 않는다. 목 모드에서는 만들지 않는다.
    - 저장소가 없는 빌드: 이번 실행 동안만 메모리에 둔다(다음 실행은 새 게스트). expo-crypto 가 없는 빌드: 시작하지 않는다(00a-err).
- 세션 저장값에 받은 제공자(`provider`: guest · kakao)를 함께 둔다(기기에만). 게스트 세션은 만료돼 있어도 로그인 상태로 복원하고 첫 요청 전에 다시 받는다.
- 카카오(MVP 이후) 백엔드(구현 완료): `POST /api/auth/kakao {kakaoAccessToken}` → `{sessionToken, sessionExpiresAt}`, `POST /api/auth/logout` → 204.
  이후 모든 요청은 `Authorization: Bearer <sessionToken>`. 리프레시 토큰은 없다. 만료되면 다시 로그인한다.
- 세션은 `expo-secure-store`(Android Keystore)의 키 `remembrall.session` 하나에 `{sessionToken, sessionExpiresAt}` 로 둔다. AsyncStorage 에 평문으로 두지 않는다.
  저장소를 읽지 못하면 세션이 없는 것으로 본다.
- 시작할 때 `sessionExpiresAt` 이 지난 카카오 세션은 없는 것으로 본다. 기본 수명은 백엔드 설정(30일)이다.
- 회원당 Device 는 1개다. 다른 폰으로 로그인하면 기존 폰의 세션이 바뀌어 다음 요청에서 401 이 난다.
- 백엔드는 카카오 토큰의 `app_id` 를 자기 설정(`kakao.app-id`)과 대조한다. 앱은 백엔드와 같은 카카오 앱 키를 쓴다.
- 아직 연결하지 않는 것: FCM 토큰 등록(`PUT /api/devices/fcm-token`)은 Firebase 프로젝트 설정(google-services.json)이 필요하다.
- 동의 API 는 서버에 있다: `GET /api/me/consents`, `PUT /api/me/consents/{consentType}`(유형 `LOCATION_BASED_SERVICE` · `PUBLIC_CANDIDATE_CONTRIBUTION`). 클라이언트 · 목은 있고(`shared/api/consents.ts`, 상수 `consentTerms.ts`, 상태 계산 `mappers/consentMapper.ts`). 00d "위치 허용하기"에 연결됐다(동의하지 않았으면 `shared/ui/LocationConsentSheet` 로 동의를 받은 뒤 권한 요청). 06b · 설정은 연결 전이다.
    - 서버는 철회되지 않은 `LOCATION_BASED_SERVICE` 동의가 없으면 위치 이벤트를 실행 없이 버리고 그대로 202 를 돌려준다. 지금은 버린 이유가 응답 · 로그 어디에도 없다.
    - PUT 으로 동의할 때(`agreed: true`) `termsVersion` 은 그 유형의 서버 설정 버전(`consent.*-terms-version`, 기본 v1)과 같아야 하고, 다르면 400 이다. 앱이 현재 버전을 조회할 방법은 아직 없다.
    - 동의는 OS 위치 권한과 분리해 별도 동의 화면에서 명시적으로 받는다(설계 중).
- 00b 의 약관 문구는 지금 링크 없이 문구만 있다. 이용약관 · 개인정보 처리방침 페이지가 생기면 링크로 연결한다.
- 의존 방향: `shared/api/client.ts` → `shared/auth/session.ts`, `shared/auth/login.ts` → `client.ts` · `session.ts` · `providers.ts`.
  `session.ts` 는 클라이언트를 import 하지 않는다(순환 방지). 둘이 같이 읽는 API 주소는 `shared/api/config.ts` 에 둔다.

## API 클라이언트

- 기본 주소는 `EXPO_PUBLIC_API_BASE_URL` 이다. 배포 주소가 정해질 때까지 로컬 백엔드(`http://<PC LAN IP>:8080`)를 쓴다.
- **목 모드**: 주소가 비어 있으면 서버를 부르지 않는다. 시작은 `MOCK_SCENARIO.signedIn`, 로그인은 제공자 로그인(목)만 하고 세션을 저장하지 않는다(가짜 토큰을 저장소에 남기지 않는다).
- 요청 시간 제한은 15초다. 저장 API 처럼 긴 요청은 붙일 때 요청별로 정한다.
- 로그인 요청의 401(제공자 자격 증명 거절)은 세션 만료가 아니라 로그인 실패다(00a-err · 00b-err).
  http(cleartext) 허용은 개발 빌드에서만 켜고 출시 빌드에는 넣지 않는다.
- 세션이 있으면 Bearer 헤더를 붙인다. 응답은 래핑하지 않고, 에러 바디 `{code, message}` 는 `ApiError` 로 바꾼다.
- 세션을 실어 보낸 요청의 401 · 만료:
    - 게스트 세션: 같은 설치 id 로 조용히 다시 받고(동시에 여러 요청이 와도 한 번만) 원래 요청을 **딱 한 번** 다시 보낸다. 다시 보낸 요청이 또 401 이면 반복하지 않는다.
      만료가 보이면 보내기 전에 미리 다시 받는다. 다시 받기가 통신 오류면 로그아웃하지 않고 원래 오류를 넘긴다. 거절(4xx)이면 세션을 지우고 00a(설치 id 는 남긴다).
    - 카카오 세션: 세션을 지우고 00b.
    - 클라이언트는 다시 받는 함수를 `setSessionRefresher` 로 주입받는다(App 시작 때 `login.ts` 가 등록). `client.ts` 는 `login.ts` 를 import 하지 않는다.
- UI 단계의 화면들은 지금처럼 mock 에서 받는다. 클라이언트를 거치는 것은 인증 API 부터다.

## 로그인 제공자 확장

- 제공자 목록은 `shared/auth/providers.ts` 한 곳에 둔다. 항목: id · 켜짐(`enabled`) · 버튼(선택) · 로그인 함수(자격 증명 credential 을 돌려준다) · 백엔드 엔드포인트 · 요청 바디.
- MVP 는 게스트만 켜고 카카오는 `enabled: false` 로 둔다(코드는 그대로). 다시 켜려면 `enabled` 만 `true` 로 바꾼다.
  켜진 버튼 제공자(`BUTTON_PROVIDERS`)가 하나라도 있으면 00b 가 등록되고 00a "시작하기"는 00b 로 간다. 없으면 00a 가 게스트로 시작한다.
- 00b 는 켜진 버튼 제공자로 버튼을 그린다(C/SocialLoginButton, 아래로 쌓기, 간격 8, 높이 size/button, radius/md).
- 제공자 추가 = 목록에 한 항목 + 버튼 변형 + 백엔드 엔드포인트. 색은 `provider/{이름}-*` 토큰(`colors.provider.{이름}`), 심볼은 공식 에셋.
  제공자 색은 플랫폼 규정이라 "강조 색은 자두색 하나" 원칙의 예외다.
- 카카오(카카오 로그인 디자인 가이드 규정): 컨테이너 `provider/kakao-container`(#FEE500), 심볼 `provider/kakao-symbol`(#000000),
  레이블 `provider/kakao-label`(#000000 85%), radius 12, 레이블은 OS 기본 시스템 서체(fontFamily 지정 안 함), 레이블 높이는 버튼의 1/3 이하(48 → 16). 라벨 "카카오 로그인".
  심볼은 `assets/images/providers/kakao-symbol.svg`(공식 kakao_login_light.svg 의 말풍선 경로. 경로 수정 금지, 색은 규정 #000000).
  SDK 는 `@react-native-kakao/core` · `user`(TurboModule, config plugin 내장, 카카오톡 앱 로그인 지원)를 쓴다.
- 로그인 실패(00b-err): 사용자가 제공자 창을 닫으면 오류 없이 00b 에 머문다. 통신 · 서버 오류일 때만 버튼 위에 오류 문구를 보인다.
- 카카오를 다시 켤 때 할 일(MVP 이후, 조사 내용):
    - 백엔드 `secrets.properties` 의 `kakao.app-id` 와 같은 카카오 앱을 쓴다(값은 저장소에 없음). 백엔드는 `access_token_info` 의 회원번호 · app_id 만 쓰므로 추가 동의 항목은 없다.
    - config plugin: `nativeAppKey`(환경 변수 `KAKAO_NATIVE_APP_KEY`, 없으면 플러그인을 빼고 경고), `android.authCodeHandlerActivity: true`. 런타임에 `initializeKakaoSDK(nativeAppKey)`.
    - 카카오 콘솔: Android 플랫폼 패키지 `com.remembrall.app`, 키 해시 등록, 카카오 로그인 활성화.
    - 키 해시 = 서명 인증서 SHA-1 의 base64. EAS 개발 빌드는 `npx eas-cli credentials -p android` 의 SHA1 Fingerprint 를 PowerShell 로 바꾼다:
      `[Convert]::ToBase64String(($sha1 -split ":" | ForEach-Object { [Convert]::ToByte($_, 16) }))`.
      로컬 RN debug keystore(공용)는 `Xo8WBi6jzSxKDVR4drqm84yr9iU=`. 빌드 후 `getKeyHashAndroid()` 로 실제 값을 확인할 수 있다. 출시 때는 Play 앱 서명 키도 등록한다.

## 기기 권한

- 00c 알림: 허용하기 → 알림 권한 요청(안드로이드 13+ 시스템 창, 12 이하는 자동 허용) → 결과와 관계없이 00d. 나중에 → 00d.
- 00d 위치 1단계: 허용하기 → 앱 사용 중 위치 요청. 허용(이번만 포함) → 00e, 거절 → 00f. 나중에 → 00f.
- 00e 위치 2단계: 설정 열기 → 백그라운드 위치 요청. 안드로이드 11+ 는 시스템이 이 앱의 위치 권한 화면을 연다. 돌아오면 결과와 관계없이 00f.
- 설정(11): 위치 권한 행은 실제 상태(항상 허용 · 앱 사용 중에만 허용 · 허용 안 함). 기기 알림이 꺼져 있으면(허용 전 포함)
  "근처에 오면 알려주기" 행이 11b 가 된다(설명 "기기 알림이 꺼져 있어요. 눌러서 켜주세요" status/danger-fg, 스위치 대신 >, 역할 button, 누르면 시스템 알림 설정).
  둘 다 화면에 들어올 때와 앱이 앞으로 돌아올 때 다시 읽는다.
- 매니페스트 권한과 이유:
    - `ACCESS_FINE_LOCATION` · `ACCESS_COARSE_LOCATION`(expo-location): 지오펜스 진입 판정. 안드로이드 12+ 는 둘을 함께 선언한다.
    - `ACCESS_BACKGROUND_LOCATION`(expo-location plugin `isAndroidBackgroundLocationEnabled`): 앱이 닫혀 있어도 OS 지오펜스가 진입 이벤트를 받는다.
    - `POST_NOTIFICATIONS`(expo-notifications): 안드로이드 13+ 알림 허용.
    - `RECEIVE_BOOT_COMPLETED`(expo-notifications 기본): 재부팅 후 예약 알림 복원 · 지오펜스 재등록.
    - 포그라운드 서비스 권한은 넣지 않는다(`isAndroidForegroundServiceEnabled: false`). 지오펜싱에는 필요 없다.
- Play 정책상 백그라운드 위치는 사용 전 고지가 필요하다. 00d · 00e 가 그 역할이다.

## 개발 빌드

- `expo-dev-client` 로 만든 개발 빌드가 기본 실행 환경이다. Expo Go 는 지도가 없는 화면 확인용으로만 쓴다(터미널 `s` 로 전환).
- EAS 프로젝트 `remembrall` 은 Expo 조직 `ktc4-chungnam-3` 소유다. 팀원은 조직 초대로 권한을 받는다.
- 기존 개발 빌드에 없는 네이티브 모듈은 import 만 해도 앱이 멈춘다. 새 네이티브 모듈은 재빌드 전까지 쓰는 경로에서만 지연 로드하거나, 재빌드 후에 연결한다.
  (예: `shared/storage/secureStore.ts` 는 expo-secure-store 를, `shared/auth/guest.ts` 는 expo-crypto 를 실제 모드에서 처음 쓸 때 불러온다. 모듈이 없으면 메모리에만 두거나 시작하지 않고 경고한다.
  `shared/permissions/` 는 expo-location · expo-notifications 를 처음 쓸 때 불러오고, 모듈이 없으면 "unavailable" 을 돌려준다. 이 두 패키지는 `shared/permissions/` 밖에서 import 하지 않는다.)
- 안드로이드 Expo Go(SDK 53+)에는 expo-notifications 기능이 없다. 불러오기만 해도 예외가 나므로 `isRunningInExpoGo()`(expo-notifications 가 쓰는 판별과 같다)이면 불러오지 않고 "unavailable" 로 처리한다.
  알림 권한(00c 창 · 11b)은 다시 만든 개발 빌드에서만 확인한다.
- 플랫폼 · Expo Go 지원 여부를 전제로 할 때는 계획 단계에서 문서 · 패키지 코드로 근거를 확인하고 적는다.
- `android/` 는 gitignore 대상이며 빌드 때마다 prebuild(CNG)로 새로 생성된다. 네이티브 설정은 `app.json` · `app.config.ts` 플러그인으로만 바꾼다.
- Google Maps 키는 `GOOGLE_MAPS_ANDROID_API_KEY` 환경 변수로만 받는다. `EXPO_PUBLIC_` 접두어를 쓰지 않아 JS 번들에 들어가지 않는다.
  로컬은 `.env.local`, EAS 는 `development` 환경의 secret 변수다. 키가 없으면 경고만 하고 빌드는 막지 않는다.
- 키 제한: Maps SDK for Android 만, 패키지 `com.remembrall.app` + SHA-1 두 개(EAS 개발 빌드 keystore, 로컬 debug keystore).
  debug keystore SHA-1 은 모든 RN 프로젝트 공용이라 개발용 키에만 등록한다.
- 개발 빌드 딥링크 scheme 은 `remembrall://` 이다(`app.json` 의 `scheme`). 실행 · 키 설정 절차는 README 에 둔다.
- 환경 변수: `KAKAO_NATIVE_APP_KEY`(카카오 네이티브 앱 키. `app.config.ts` 가 config plugin 과 `extra` 에 넣는다. SDK 초기화가 런타임에도 키를 쓴다),
  `EXPO_PUBLIC_API_BASE_URL`(API 기본 주소. 비밀이 아니라 번들에 들어간다). 저장소에 값을 넣지 않는다.

## 목 데이터

- `shared/api/mock/` 은 화면 props 모양 그대로 둔다. 서버 응답 계약처럼 만들지 않는다.
- UI 단계 동안 features 는 mock 에서 직접 받는다. `client.ts` 와 `mappers/` 를 거치지 않는다.
- mock 은 features 의 타입을 import 하지 않는다(shared → features 금지). 구조적 타입으로 맞춘다.
- 목 이미지 URL(picsum 고정 id)은 이 폴더 안에만 둔다.
- 세션과 로그인은 2 · 3번 커밋 전까지 목 플래그다. `MOCK_SCENARIO.signedIn`(시작 시 세션 여부, 기본 true) · `loginResult`(success · cancelled · failed).
  온보딩 일러스트 사진은 목 이미지다(`mock/onboarding.ts`). 첫 실행은 오프라인일 수 있어 나중에 번들 이미지로 바꾼다.
- 위치 권한과 현재 위치는 runtime 작업 전까지 목 플래그(`MOCK_SCENARIO`)와 목 좌표로 둔다.
  설정(11)의 권한 표시는 실제 권한 상태를 읽는다. 지도(10 · 08)의 현재 위치 · denied 분기는 runtime 작업 때 실제 권한으로 바꾼다.
- 위치기반서비스 동의는 목 모드에서 목 플래그다. `MOCK_SCENARIO.locationConsent`(시작 시 동의 상태, never · agreed · withdrawn, 기본 never) · `consentSaveResult`(동의 저장 결과, ok · fail, 기본 ok).
- 장소 확정 저장물은 모두 상세(`detailId` → 08)를 가진다. 실제 데이터에서도 모든 저장물에 상세가 있다. 미확정 저장물은 저장 결과(02~05)로 간다.
- 거리 표시: 도보 30분 이내는 "도보 N분", 30분을 넘으면 직선거리 "N.Nkm". 목 데이터는 이 규칙대로 문자열을 넣는다.

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
type ExtractionStatus = "SUCCESS" | "PARTIAL" | "FAILED";
type EvidenceSource =
    | "VIDEO_AUDIO"
    | "VIDEO_TEXT"
    | "VIDEO_VISUAL"
    | "TITLE"
    | "DESCRIPTION";
type FailureStage =
    | "METADATA_FETCH"
    | "VIDEO_ACCESS"
    | "GEMINI_CALL"
    | "RESPONSE_MAPPING";
```

여기서 따라오는 화면 요구사항:

- **`ExtractionStatus.PARTIAL` 은 백엔드에 있지만 UI 에는 드러내지 않는다.** UI 는 저장 판정
  (PLACE_RESOLVED / NEEDS_CONFIRMATION / NO_PLACE) 기준으로 표시하고, 부분 성공 배지는 두지 않는다(디자인 결정).
- **분석 실패와 장소 후보 없음은 다른 화면이다.** 문서에 명시돼 있다.
  빈 `placeCandidates` + `SUCCESS` 와 `FAILED` 는 사용자가 할 행동이 다르다.
- **`candidateId` 는 전역 장소 키가 아니다.** 한 추출 결과 안에서만 유효한 임시 ID다.
  라우팅 파라미터나 캐시 키로 쓰면 안 된다. 전역 장소 엔티티는 아직 존재하지 않는다.
- **인증은 구현 완료다.** 카카오 로그인 · 로그아웃 · 세션(Bearer) · FCM 토큰 등록 API 가 코드에 있다("인증 · 세션" 절).
- **목록 필드는 빈 배열 보장**이 문서 규칙이나, validation 애노테이션이 코드에 0개라
  강제되지 않는다. 방어 코드를 둔다.

## 미확정 (앱 모델은 가설이다)

아래는 `domain/` 에 타입을 만들어 두되, **백엔드 합의 전까지 확정이 아니다.**
해당 파일 상단에 미확정임을 주석으로 남긴다.

| 항목                                    | 현재 상태                                                                             | 영향                                                                                                                                                                                                                  |
| --------------------------------------- | ------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 분석 진행 상태 (ANALYZING / READY)      | 백엔드에 없음. `ExtractionStatus` 는 완료 후 결과 등급이지 진행 상태가 아니다         | 대기 UI(03 분석 중) 의 근거                                                                                                                                                                                           |
| 장소 판정 결과 (확정 / 확인필요 / 없음) | 백엔드에 없음. `uncertainties: string[]` 자유 문자열뿐                                | 되묻기 화면(02 확인 필요 · 04 장소 없음) 의 근거                                                                                                                                                                      |
| 카테고리                                | 팀 합의: 고정 enum 소수 + 분위기 태그 다수. enum 값은 미확정                          | 01 필터 칩 라벨(전체/식당/카페/가볼 곳)은 enum 확정 전까지 목 데이터 한정                                                                                                                                             |
| 비공개·삭제 영상의 링크 보관 여부       | 서버 저장 정책 문제. 백엔드 합의 필요. 05 디자인은 "링크는 그대로 보관" 으로 안내한다 | (b) "다시 시도" 가 모든 실패에 똑같이 노출된다. 재시도가 의미 없는 비공개·삭제(`VIDEO_ACCESS`)에도 보인다. 재시도 가능 여부를 서버가 내려줘야 버튼 노출을 가를 수 있다. (07 실패 셀이 생겨 05 재진입 경로는 해결됐다) |
| PARTIAL 표현                            | 부분 성공 배지는 제거(디자인 결정)                                                    | 추출 `PARTIAL` 이 저장 판정(PLACE_RESOLVED / NEEDS_CONFIRMATION / NO_PLACE) 중 무엇으로 매핑되는지 백엔드 확인 필요                                                                                                   |
| 꺼내기 제안 유형                        | 문서상 가설이 5개 (장소 1곳 / 코스 / 장소 없는 콘텐츠 재노출 / 공용 풀 보충 / 침묵)   | **SINGLE·COURSE 2분기로 부족하다.** 열린 형태로 둔다                                                                                                                                                                  |
| 알림 발생원 (FCM / 로컬)                | 백엔드에 FCM 의존성 없음                                                              | `runtime/notifications/notificationListener.ts` 안에서만 갈린다                                                                                                                                                       |
| 완료 통지 방식 (폴링 / SSE / 푸시)      | 없음                                                                                  | **가장 시급.** 이것 없이는 저장 플로우가 완성되지 않는다                                                                                                                                                              |
| API 서버 주소                           | 배포 주소 미정(백엔드 확인 중). 개발은 로컬 백엔드 `http://<PC LAN IP>:8080`          | `EXPO_PUBLIC_API_BASE_URL` 만 바꾼다                                                                                                                                                                                  |
| 지오펜스 보고 엔드포인트                | 문서에만 서술. 시그니처 없음                                                          | 서버가 조용히 무시할 수 있어 프론트가 결과를 알 방법이 필요                                                                                                                                                           |

## 화면으로 만들 것 vs 상태로 표현할 것

공유 시 앱은 뜨지 않는다. 저장 결과는 알림이나 전체 기억 항목에서 여는
저장 결과 모달(02~05)로 본다.

| 경우                                         | 표현                                                                     |
| -------------------------------------------- | ------------------------------------------------------------------------ |
| 지원하지 않는 링크                           | 알림 문구. 저장하지 않음                                                 |
| 비공개·삭제 영상 (`FAILED` / `VIDEO_ACCESS`) | 알림 문구. 저장 여부 미확정 (미확정 표 참조)                             |
| 분석 실패                                    | 05 저장 결과 모달 + 기억 탭 실패 셀 (제목을 모르면 "불러오지 못한 영상") |
| 부분 성공 (`PARTIAL`)                        | 표현하지 않음 (배지 제거). 매핑은 미확정 표 참조                         |
| 삭제·만료된 저장물 · 장소                    | 12 NotFound. "기억 목록으로" → 기억 탭                                   |
| 장소 후보 없음 (`SUCCESS` + 빈 배열)         | 04 저장 결과 모달 + 전체 기억 배지                                       |
| 분석 중                                      | 03 저장 결과 모달 + 전체 기억 배지                                       |
| 장소 후보 여러 개                            | 02 저장 결과 모달 (기존 확인 화면을 흡수)                                |
| 저장물 0개                                   | 07b 전체 기억 빈 상태. 신규 사용자에게는 사실상 튜토리얼                 |
| 근처에 저장물 없음                           | 06 근처 빈 상태                                                          |
| 제안 성립 안 함                              | 알림이 오지 않는다. 화면 없음                                            |

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
