# REMEMBRALL 프론트엔드 구조

모노레포의 `frontend/` 가 RN 프로젝트 루트다.
`package.json`, `metro.config.js`, `app.json`, `android/` 가 모두 이 아래에 생긴다.

## 전제: 백엔드 계약과 앱 모델을 나눈다

API 계약은 백엔드 `develop` 코드(Controller · DTO)가 기준이다. 서버 응답 타입은 `shared/api/*` 에 백엔드 코드대로 둔다.
YouTube 추출 결과 DTO 7개 · enum 3개(`domain/extraction/`)는 **모듈 간 내부 반환 타입**이지 HTTP 응답 바디가 아니다.

이 구조는 **백엔드 계약이 바뀌어도 고치는 범위가 좁도록** 짠다. 원칙은 하나다.

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
├── index.js                  RN 엔트리. 지오펜스 백그라운드 태스크 정의(앱보다 먼저) + 앱 등록
├── app.json                  정적 앱 설정. EAS projectId · owner 포함
├── app.config.ts             app.json 을 받아 빌드 시점 비밀값(Google Maps 키)을 플러그인에 넣고 google-services.json 경로를 정한다
├── eas.json                  EAS 빌드 프로필. development = 개발 빌드(dev client) APK, preview = 내부 배포 APK(시연 도구 켬),
│                             preview-mock = preview + 목 모드 강제(EXPO_PUBLIC_FORCE_MOCK=1, 시연 백업), production
├── .prettierrc               prettier 설정(tabWidth 4, LF). 버전은 package.json 에 고정
├── .prettierignore           포맷하지 않는 생성물(package-lock.json, android/ 등)
├── .editorconfig             prettier 를 쓰지 않는 에디터용. .prettierrc 와 같은 값
├── .gitattributes            frontend 텍스트 파일을 LF 로 받는다
├── assets/
│   ├── fonts/                Noto Sans KR 서브셋 ttf + OFL.txt
│   └── images/providers/     로그인 제공자 공식 심볼. 공식 에셋에서 심볼 경로만 남긴 파일(직접 그리지 않는다)
├── scripts/
│   └── subset-fonts.py       폰트 서브셋 생성 (원본 출처 · 범위 · 실행 방법은 파일 상단)
│                             android/ 는 커밋하지 않는다(EAS 가 prebuild 로 만든다). 공유 수신은 expo-share-intent 플러그인이
│                             MainActivity 에 SEND(text/plain) intent-filter 를 붙여 앱을 앞으로 띄운다(흐름 메모 1). 별도 액티비티 없음
└── src/
    ├── app/                  앱 셸
    │   ├── App.tsx
    │   ├── usePendingSaveSender.ts  저장 대기열 다시 보내기 시점(signedIn 직후 · 앞으로 돌아올 때)
    │   ├── useGeofenceSync.ts  지오펜스 (재)등록 시점(signedIn 직후 · 앞으로 돌아올 때 · 위치 설정 신호) · signedOut 때 해제
    │   ├── usePushTokenSync.ts  FCM 토큰 등록 시점(signedIn 직후 · 앞으로 돌아올 때 · 토큰 변경)
    │   ├── demo/             설정의 "시연 도구" 묶음(DemoToolsSection). 개발 모드 · preview 빌드에서만 App 이 등록한다
    │   ├── share/            공유 받음 처리 흐름(shareReceiver) · 시트 호스트 · 개발용 진입(devShareLink)
    │   ├── notifications/    알림을 눌러 08 열기(notificationOpener). 대기 칸 · signedIn 게이트 · navigationRef 로 push
    │   └── navigation/       RootNavigator, linking, routes, navigationRef(네비게이터 밖 이동)
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
    │   ├── consent.ts        앱 모델. 동의 상태(never · agreed · outdated · withdrawn)
    │   ├── saveState.ts      앱 모델. 저장물 상태(analyzing · failed · noPlace · ready)
    │   └── executionTrace.ts 에이전트 궤적
    │
    ├── runtime/              React 밖
    │   ├── background/       pendingQueue(저장 보내기 대기열)
    │   ├── geofence/         지오펜스 이벤트 처리 · 도착 보고 대기열 · 상태 파일(geofenceManager · geofenceState), 등록 계획(registrationPlan),
    │   │                     (재)등록 · 해제(geofenceSync), 백그라운드 태스크(geofenceTask), expo-task-manager 지연 로더(taskManager)
    │   ├── notifications/    channels(근처 알림 채널 정의 · 생성), listener(알림 누름 구독 · 콜드 스타트 응답 · 중복 거르기, 발생원 은닉),
    │   │                     router(응답 data → notificationId), FCM 토큰 등록 판단(pushTokenSync)
    │   ├── permissions/      권한 상시 감시 (자동 회수 대응) (자리만 있음, 아직 쓰지 않음)
    │   └── share/
    │       └── supportedLinks/   [확장 포인트] 플랫폼별 파서 레지스트리
    │
    ├── features/             화면
    │   ├── onboarding/       첫 실행 흐름. 소개 · 로그인 · 권한(알림 · 위치 2단계) · 첫 저장 안내(00a~00f)
    │   │   └── components/
    │   ├── archive/          기억 탭. 시간순 앨범. 저장물 목록 API(useArchivePages) · 섹션 · 바램 · 이름(albumSections)
    │   │   └── components/
    │   ├── proposal/         근처 탭. 지금 위치 근처의 저장물
    │   │   └── components/
    │   ├── save-result/      저장 결과 모달. 분석 중 / 확인 필요 / 장소 없음 / 실패 상태별 렌더 + 장소 직접 찾기(02c · 02d)
    │   │   └── components/
    │   ├── content-detail/   장소 상세 · 지도. 끌 수 있는 시트(기본 · 펼침)
    │   │   └── components/
    │   ├── settings/         설정(11). 알림 · 위치 · 계정 · 앱 정보
    │   │   └── components/
    │   ├── share-received/   공유 받음 시트(C/ShareReceivedSheet). 지금 화면 위에 뜬다
    │   ├── execution-trace/  "왜 에이전트인가" 증명 화면 (자리만 있음, 아직 쓰지 않음)
    │   └── map-view/         지도 탭(10). 사진 핀 · 요약 시트
    │       └── components/
    │
    └── shared/
        ├── auth/             로그인 · 세션
        │   ├── session.ts        세션 저장(안전한 저장소) · 메모리 캐시 · 구독(useAuthState) · 복원은 한 번(백그라운드 태스크도 부름)
        │   ├── providers.ts      [확장 포인트] 로그인 제공자 목록(켜짐 · 버튼 · 엔드포인트 · 요청 바디)
        │   ├── login.ts          로그인 흐름 · 게스트 세션 조용히 다시 받기
        │   ├── guest.ts          게스트 로그인(MVP). 설치 id 만들기 · 보관
        │   └── kakao.ts          카카오 로그인(MVP 이후. 지금은 목)
        ├── permissions/      알림 · 위치 권한 조회와 요청(온보딩 · 설정이 쓴다)
        │   ├── notifications.ts  알림 권한 조회 · 요청 · 시스템 알림 설정 열기 · 기기 FCM 토큰 받기 · 토큰 변경 구독 ·
        │   │                     채널 생성 · 근처 알림 채널 id · 앞에 있을 때 표시 · 누름 응답 · 로컬 알림 띄우기(목)
        │   ├── location.ts       위치 권한 조회(always · whileInUse · denied) · 앱 사용 중 · 백그라운드 요청 · 지오펜스 등록 · 해제 · 등록용 현재 위치
        │   ├── locationSettings.ts  위치 설정(동의 저장 · 권한 요청)이 바뀌었다는 신호. app 의 hook 이 받아 지오펜스를 다시 등록
        │   └── usePermissionStatus.ts  진입 · 앞으로 돌아올 때 다시 읽는 hook
        ├── api/
        │   ├── config.ts     API 기본 주소(EXPO_PUBLIC_API_BASE_URL). 비거나 EXPO_PUBLIC_FORCE_MOCK=1 이면 목 모드
        │   ├── client.ts     기본 주소 · Bearer 헤더 · ApiError · 401 처리 · 요청별 시간 제한(timeoutMs)
        │   ├── consents.ts   동의 API(조회 · 위치 동의 변경) · 목 분기
        │   ├── consentTerms.ts   약관 URL 상수(약관 버전은 서버가 준다)
        │   ├── personalSaves.ts  저장물 API(저장 · 상세 · 목록 · 삭제)와 서버 응답 타입 · 목 분기
        │   ├── personalSavesMock.ts  저장물 목. 서버 규칙을 따르는 메모리 저장소(서버 계약 모양이라 mock/ 밖)
        │   ├── geofences.ts  지오펜스 API(등록 목록 · 장소 도착 보고)와 서버 타입 · 목 분기
        │   ├── geofencesMock.ts  지오펜스 목. 서버 규칙대로 저장물 목의 장소로 목록을 만들고, 도착 보고를 메모리에 남긴다(새 도착마다 알림 목이 알림을 만든다)
        │   ├── devices.ts    기기 API(FCM 토큰 등록) · 목 분기(서버를 부르지 않고 성공)
        │   ├── notifications.ts  알림 API(상세 · 열람)와 서버 타입 · 목 분기
        │   ├── notificationsMock.ts  알림 목. 목 도착마다 PLACE 알림 1개를 만들어 로컬 알림으로 띄우고, 상세는 읽을 때 저장물을 다시 확인한다
        │   ├── usePersonalSaveDetail.ts  저장물 상세 hook. 포커스 · 앱 복귀 때 읽기, 분석 중 5초마다 다시 읽기
        │   ├── useNotificationDetail.ts  알림 상세 hook. 처음 그릴 때 한 번 읽고 열 화면(toNotificationTarget)을 정한다
        │   ├── mappers/      ★ 서버 응답 -> 앱 모델 변환. 백엔드 변경 흡수 지점
        │   │   └── personalSaveMapper.ts  저장물 상세 · 목록 항목 -> SaveState
        │   └── mock/         UI 개발용 목 데이터. 화면 props 모양. 서버 계약 아님
        ├── demo/
        │   └── demoTools.ts      시연 도구 켜짐 여부(__DEV__ · EXPO_PUBLIC_DEMO_TOOLS) · 설정 묶음 자리(app 이 등록, 설정이 그림)
        ├── storage/
        │   ├── storage.ts        로컬 JSON 파일 저장소(키 하나 = Paths.document/{key}.json). 비밀이 아닌 값. 지오펜스 상태
        │   └── secureStore.ts    안전한 저장소(expo-secure-store) 지연 로더. 세션 · 설치 id
        ├── geo/
        │   └── distanceMeters.ts  두 좌표 거리(m). 서버 GeoDistance 와 같은 haversine
        ├── external-links/   지도 딥링크 (단순 URL 빌더)
        │   ├── youtubeLinks.ts  YouTube Shorts 주소 · 썸네일 주소(hqdefault). 공유 파서와 화면들이 같이 쓴다
        │   └── openExternalUrl.ts  웹 페이지(약관) 열기. expo-web-browser 지연 로드, 실패하면 Linking
        └── ui/               두 개 이상 feature 가 쓰는 공통 컴포넌트
            ├── savedAt.ts    저장 시각 → 바램(fadeFor) · 지난 시간(savedAgo). 순수 함수
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
- 두 feature 이상이 쓰는 것: ScreenHeader(근처 · 기억 헤더), Chip(근처 · 지도 필터), PhotoMarker · PinLabel · CurrentLocation(지도 탭 · 08 지도), SheetHandle(08 · 10b 시트), NotFound(저장 결과 · 장소 상세), LocationConsentSheet(00d · 06b · 설정 11 의 위치 정보 이용 동의 시트), ModalSheet(확인 시트 틀 · 버튼 묶음. 바깥 닫힘 · 저장 중 차단. 앱 루트 BottomSheetModalProvider 위에 뜬다. 동의 시트 · 11d 거두기 시트가 쓴다).
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
- SaveResult(02~05)는 기억 항목 탭으로 진입한다(저장물로 연 08 이 ready 가 아니어도 이리로 바뀐다). 알림은 08 을 연다(아래).
- 알림으로 여는 08: `ContentDetail { notificationId }` 를 지금 화면 위에 push 한다(navigate 는 쓰지 않는다. 같은 이름이 스택에 있으면 그리로 돌아가 버린다).
  화면은 알림 상세(`useNotificationDetail`)를 읽고, 읽으면 열람 처리(`openNotification`)를 한 번 보낸 뒤(기다리지 않고 실패는 무시, 서버는 멱등)
  첫 항목으로 `replace(ContentDetail { personalSaveId, placeId })` 한다. 그래서 뒤로 가면 알림을 누르기 전 화면이고, 히스토리에 알림 08 이 남지 않는다.
  장소가 없는 항목(CONTENT)은 `{ personalSaveId }` 만 넘긴다(ready 가 아니면 저장 결과로 바뀐다). 404 · 항목 없음 · PUBLIC(내 저장물 아님)은 12, 그 밖의 읽기 실패는 다시 불러오기.
  여러 장소(COURSE)도 지금은 첫 항목만 연다. 개발용 딥링크 `notification/:notificationId` 로도 연다.
- 알림 누름 처리: `runtime/notifications/notificationListener.ts` 의 `startListening`(App 마운트 때 한 번, 헤드리스 태스크에서는 부르지 않는다)이
  응답 리스너를 붙인 뒤 앱을 연 마지막 응답(종료 상태에서 누름)을 읽고 지운다(`takeLastNotificationResponse`). 앞 · 뒤 상태는 이벤트로 온다.
  id 는 `router.ts` 의 `toNotificationId`(data.notificationId, 문자열이 아니거나 비면 무시)로 꺼내고, 이번 실행에서 넘긴 id 는 다시 넘기지 않는다.
  여는 것은 app 의 `app/notifications/notificationOpener.ts`: 대기 칸 한 칸(마지막 것만)에 두고, signedIn 이고 네비게이터가 준비돼 ContentDetail 이 등록돼 있으면 push 하고 비운다.
  비우는 때는 넘겨받은 즉시 · NavigationContainer `onReady` · signedIn 이 된 직후 · signedIn 동안 내비게이션 상태가 바뀔 때(화면 등록이 늦게 반영되는 경우)다.
  signedOut 이 되면 버린다. 위치 동의는 조건이 아니다. 시연 도구의 "알림 열기"도 같은 opener 를 부른다(중복 거르기는 거치지 않아 다시 누르면 다시 연다).
- 02c 에서 저장하면 SaveResult 모달 하나만 닫아 흐름을 끝낸다. 뒤로 가기는 02c → 02 로 간다.
- 08 "이 장소가 아니에요"는 SaveResult 모달에 PlaceSearch 하나만 올린 바꾸기 모드(placeId)다. 바꾸기 · 뒤로 가기 모두 모달을 닫고 08 로 돌아간다. 딥링크는 없다.
- 08 "길 안내 시작"은 동작하지 않는다. 백엔드 협의 이슈 대기.
- 하단 바의 선택 표시는 `bg/subtle` pill 이다. 브랜드 색을 쓰지 않는다.
- 없는 저장 결과 id · 장소 id 로 들어오면 NotFound(12)를 보여준다. "기억 목록으로" → 기억 탭.
- 기억 탭(07 · 07b)은 저장물 목록 API(`fetchPersonalSaves`)를 읽는다. 첫 페이지 20개, 끝 근처(한 화면 이내)까지 내리면 `nextCursor` 로 다음 페이지를 붙인다.
  탭에 포커스될 때와 포커스된 동안 앱이 앞으로 올 때 다시 읽는다(백엔드 합의: 보관함 진입 · 앱 복귀 시 재조회).
  다시 읽을 때는 이미 읽은 개수만큼(20~100) 한 번에 읽어 통째로 바꾸고, 응답이 올 때까지 보이던 목록을 둔다. 마지막 읽기만 반영한다.
  첫 로딩은 헤더만, 처음 읽기 실패는 캡션 자리에 "확인하지 못했어요. 눌러서 다시 불러와요"(누르면 다시 읽기), 다시 읽기 · 다음 페이지 실패는 목록을 그대로 둔다.
  목록이 비면 07b. "모두 N곳"(N 은 저장물 수)은 마지막 페이지까지 읽었을 때만, 그 전에는 "오래 잊은 것일수록 바래 있어요"만 보인다.
- 기억 탭 바램 · 섹션은 저장 시각에서 지난 시간으로 정한다(`albumSections`). 7일 미만 recent "이번 주", 30일 미만 weeks "지난 몇 주",
  30일 이상 months 이고 섹션은 계절(3~5 봄 · 6~8 여름 · 9~11 가을 · 12~2 겨울, 12월은 다음 해 겨울)에 "올해" · "지난해" · "{YYYY}년"을 붙인다.
  근처에 왔을 때 바램이 되살아나는 것(Figma 2061:632)은 목록 응답에 그 정보가 없어 아직 하지 않는다.
- 기억 탭 셀: 썸네일은 videoId 의 YouTube 썸네일, 배지는 SaveState(analyzing 분석 중 · failed 실패 · noPlace 장소 없음 · ready 없음).
  "확인 필요"는 목록 응답으로 알 수 없어 아직 나오지 않는다. 이름은 ready 면 영상 제목(따옴표 없음, 목록에 장소명이 없어 임시),
  미확정이면 “영상 제목”, 제목이 없으면 failed 는 "불러오지 못한 영상", 그 밖은 "제목 없는 영상".
- 기억 탭에서 미확정 셀은 `SaveResult > Result { personalSaveId }`, ready 셀은 `ContentDetail { personalSaveId }` 로 연다.
  지도 · 근처는 지금처럼 `ContentDetail { placeId }`(장소 목)로 연다.
- 저장 결과(03 · 04 · 05)는 저장물 상세 API(`usePersonalSaveDetail` → `toSaveState`)를 읽는다. 포커스될 때 · 포커스된 동안 앱이 앞으로 올 때 읽고,
  분석 중이면 5초마다 다시 읽다가 끝나면 멈춘다. analyzing → 03, noPlace → 04, failed → 05, ready → 모달을 08(`ContentDetail { personalSaveId }`)로 바꾼다(뒤로 가면 기억 탭).
  404 → 12. 처음 읽기 실패 → Figma 05 문구 + "다시 시도"(다시 읽기). 읽는 동안은 닫기 줄만 보인다.
  03 본문은 "보통 1분쯤 걸려요. 이 화면을 닫아도 계속 찾아요."(실측 평균 59초). meta 는 "{지난 시간} 저장 · YouTube Shorts".
- 05 문구는 실패 종류로 가른다(`failedMessage`, 디자인 세션 결정). retryable(EXTRACTION_API_ERROR) "영상을 불러오지 못했어요" /
  "분석하는 중에 연결이 끊겼어요. 다시 시도하면 처음부터 찾아볼게요." + 다시 시도. videoUnavailable "볼 수 없는 영상이에요" /
  "비공개로 바뀌었거나 삭제된 영상이라 다시 분석할 수 없어요." analysisError "장소를 알아내지 못했어요" / "이 영상은 다시 분석해도 같은 결과가 나와요. 링크는 그대로 보관해둘게요."
  코드가 없으면(null) Figma 05 문구 + 다시 시도. 다시 시도는 상세의 sourceUrl 을 다시 저장하고 상세를 다시 읽는다(서버가 다시 분석하면 03, 아니면 05 에 머문다).
- 저장물로 연 08 은 `placeId` 파라미터와 같은 장소, 없으면 첫 장소를 그린다(`savedPlaceSheet` 의 `pickPlace`, 장소별 전환 전). 큰 글씨는 지난 시간, 바램은 저장 시각, 썸네일은 videoId.
  응답에 없는 값은 08b 규칙대로 비운다: 도보 시간 없음, 메모는 장소 설명 → 영상 요약 → 숨김, 태그는 카테고리 1개 → 숨김, 주소 · 도보 시간이 모두 없으면 메타 줄을 숨긴다.
  원본 영상 줄 둘째 줄은 "{영상 제목} · YouTube Shorts"(제목이 없으면 "YouTube Shorts"), 누르면 videoId 의 Shorts 주소를 연다(sourceUrl 은 watch 형식).
  미니 핀은 그리지 않는다. ready 가 아니면 저장 결과로 바꾸고, 404 는 12. 읽는 동안은 바탕과 뒤로가기만, 실패하면 "확인하지 못했어요. 눌러서 다시 불러와요".
  "이 장소가 아니에요"는 지금처럼 02c 바꾸기 모드(그린 장소의 placeId)를 연다. 02c 의 실제 저장 · 바꾸기는 아직 목이다.
- 02c 캡션의 영상 제목은 `sourceTitle` 파라미터로 받는다(04 · 저장물 08 에서). 넘겨받지 않으면(개발용 딥링크 · 목 08) 화면 목에서 찾는다.
- 개발 모드(`__DEV__`)에서 저장 결과의 personalSaveId 가 화면 목(`saveResults`) 키면 API 대신 그 목을 그린다.
  02 · 02b(확인 필요)는 아직 이 경로로만 본다(되묻기 연결 전). 서버 id 는 UUID 라 목 키와 겹치지 않는다.
- 08 시트 멈춤 지점은 기본 · 펼침이다. 지도 탭에서만 요약(10b) 지점이 추가된다.
- 08 지도에는 이 장소 핀(fade 와 관계없이 40×71 · 흰 테두리 3 · 그림자, 바램은 장소의 fade · 이름표) · 현재 위치 · 현재 위치에서 장소까지의 점선 경로만 그린다.
  현재 위치 · 경로는 위치 권한이 "denied" 일 때만 숨긴다. 경로는 경로 API 가 정해지기 전까지 직선 자리표시다.
  지도 영역은 뒤로가기 아래부터 시트 기본 지점 위까지이며(mapPadding), 그 안에 현재 위치와 장소를 함께 맞춘다(위는 핀 높이, 아래는 이름표 영역만큼 여유).
  다른 저장 장소는 미니 핀(fade 와 관계없이 22×39 · 흰 테두리 2 · 그림자 없음, 바램은 각 장소의 fade, 누름 없음)으로 그린다. 화면 맞추기에는 넣지 않는다.
- 10b 요약 시트는 핀을 누르면 뜬다. 요약을 누르거나 위로 끌면 08 로 가고, 지도 빈 곳 · 아래로 끌기 · 뒤로 가기는 닫는다. 다른 탭으로 옮기면 선택을 풀고, 08 에 다녀오면 선택을 유지한다.
- 06 "{동네} 기억 보기"는 `Main > Map` 에 `area` 를 넘긴다. 지도는 그 동네로 가운데를 잡고 `area` 를 비운다.
- 지도 핀 크기는 fade 로 정한다(recent 40 · weeks 26 · months 22). 위치 권한이 "denied" 면 현재 위치 · 내 위치 버튼 · 도보 시간을 숨긴다.
- 지도 구조: 근처 감지는 OS 지오펜싱, 앱 안 표시는 Google Maps SDK, 장소 좌표는 서버가 LocationIQ · 공공데이터(상가) · TourAPI 로 정한다. UI 단계는 표시와 핀만 한다.
- 시트는 `@gorhom/bottom-sheet`, 지도는 `react-native-maps`(Google provider)로 구현한다.
- 지명 라벨이 핀에 가려지는 것은 Google 지도 기본 동작이라 수정하지 않는다. 마커는 항상 지도 라벨 위에 그려지고, 겹치는 라벨을 숨기는 Advanced Marker 충돌 처리는 react-native-maps 가 지원하지 않는다. 가게 · 교통 · 도로 이름은 이미 끈다.
- 지도(10·08)는 Expo Go SDK 57 안드로이드에서 검은 화면으로 나온다(expo/expo#49323). 우리 Google Maps API 키를 넣은 개발 빌드에서 확인한다.

## 인증 · 세션

- **MVP 인증은 게스트 세션이다.** 로그인 없이 쓰는 모드는 없지만, 사용자가 계정을 고르지 않는다.
  백엔드(구현 완료): `POST /api/auth/guest {installationId}` → `{sessionToken, sessionExpiresAt}`(카카오와 같은 응답).
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
- FCM 토큰 등록: `PUT /api/devices/fcm-token {fcmToken}` → 본문 없는 204(`shared/api/devices.ts`). 서버는 지금 세션의 기기에만 저장하고,
  새 세션 발급(로그인 · 게스트 세션 다시 받기) · 로그아웃 때 토큰을 지운다. 토큰 해제 API 는 없고 앱도 해제하지 않는다.
    - 토큰은 Expo 푸시 토큰이 아니라 기기 FCM 토큰(`getDevicePushTokenAsync`, `shared/permissions/notifications.ts` 의 `getFcmToken`)이다. 서버가 firebase-admin 으로 직접 보낸다.
    - 조건: signedIn + 알림 권한 granted(위치 동의와 무관). 판단은 `runtime/notifications/pushTokenSync.ts` 의 `syncPushToken`,
      부르는 때는 `app/usePushTokenSync.ts`(RootNavigator): signedIn 직후 · 앱이 앞으로 돌아올 때 · signedIn 동안 토큰 변경 리스너(`addFcmTokenListener`).
    - (세션 토큰, FCM 토큰) 쌍을 메모리에만 기억하고 쌍이 바뀔 때만 보낸다(서버가 새 세션마다 토큰을 지우므로 세션도 비교). signedIn 이 아니게 되면 지운다.
      실패는 조용히 넘기고 다음 호출이 다시 한다. 재시도 큐 · 백오프는 없다.
    - Expo Go · 모듈 없는 빌드 · google-services.json 없이 만든 빌드(Firebase 초기화 안 됨)는 토큰을 받지 못해 건너뛴다(경고 한 번).
    - 알려진 한계: 요청 도중 게스트 세션을 다시 받으면 서버는 토큰이 지워진 상태로 그 요청을 처리한다(예: 서버 초기화 뒤 첫 도착 알림이 오지 않는다). 다음에 앱이 앞으로 돌아올 때 다시 등록된다.
    - 알림 채널: "근처 알림"(id `nearby`, 중요도 DEFAULT, 소리 · 진동 · 표시 범위는 OS 기본값). `runtime/notifications/channels.ts` 의 `ensureChannels` 가
      `shared/permissions/notifications.ts` 의 `ensureNotificationChannel` 로 만들고, App 마운트 때 켤 때마다 부른다(헤드리스 태스크에서는 부르지 않는다).
      만든 뒤에는 이름 · 설명만 바뀌고 중요도 · 소리는 앱이 바꿀 수 없다. id 는 바꾸지 않는다(app.json 의 `defaultChannel` 과 같아야 한다).
      id 상수 `NEARBY_CHANNEL_ID` 는 `shared/permissions/notifications.ts` 에 둔다. 목 로컬 알림(shared/api)도 쓰는데 shared 는 runtime 을 import 하지 않기 때문이다.
      서버가 channelId 없이 보내는 FCM 은 앱이 뒤에 있을 때 `defaultChannel`(manifest `default_notification_channel_id`)로 이 채널에 온다.
      앱이 앞에 있을 때는 expo 가 받아 JS 핸들러에 묻는다. 핸들러(`setForegroundPresentation`, App 마운트 때 채널과 같은 자리)는
      `{ shouldShowBanner: false, shouldShowList: true, shouldPlaySound: true, shouldSetBadge: false }` 다(목록 · 소리, 배지 없음).
      Android 8+ 는 banner · list 를 가르지 않고, 헤드업 · 소리는 채널이 정한다. 그런데 channelId 가 없으면 expo 대체 채널("Miscellaneous", 중요도 HIGH → 헤드업)로 간다.
      그래서 서버가 `channelId: "nearby"` 를 보내도록 백엔드에 요청했다(`defaultChannel` 은 빠뜨렸을 때의 안전망).
- 동의 API 는 서버에 있다: `GET /api/me/consents`, `PUT /api/me/consents/{consentType}`(유형 `LOCATION_BASED_SERVICE` · `PUBLIC_CANDIDATE_CONTRIBUTION`). 클라이언트 · 목은 있고(`shared/api/consents.ts`, 약관 URL `consentTerms.ts`, 상태 계산 `mappers/consentMapper.ts`). 00d "위치 허용하기"에 연결됐다(동의하지 않았으면 `shared/ui/LocationConsentSheet` 로 동의를 받은 뒤 권한 요청)과 설정(11c · 11d, "기기 권한" 절)에 연결됐다. 06b 는 연결 전이다.
    - 거두면(`agreed: false`) 서버에 기록한 뒤 위치 설정 신호를 내고, `syncGeofences` 가 동의가 `agreed` 가 아니므로 지오펜스를 해제하고 상태를 지운다.
    - 서버는 철회되지 않은 `LOCATION_BASED_SERVICE` 동의가 없으면 위치 이벤트를 실행 없이 버리고 그대로 202 를 돌려준다. 지금은 버린 이유가 응답 · 로그 어디에도 없다.
    - PUT 으로 동의할 때(`agreed: true`) `termsVersion` 은 그 유형의 서버 설정 버전(`consent.*-terms-version`, 기본 v1)과 같아야 하고, 다르면 400 이다.
      GET · PUT 응답의 `currentTermsVersion` 이 그 현재 버전이다(서버는 늘 채운다). 앱은 버전을 고정하지 않고 화면이 읽어 둔 `currentTermsVersion` 을 보낸다.
      화면이 모르면(00d 미리 읽기 실패 등) `setLocationConsent` 가 PUT 전에 GET 을 한 번 해서 얻고, 그것도 실패하거나 값이 없으면 예외(시트 Error)다. 철회는 버전 없이 보낸다.
    - 동의 중인데 `termsVersion` 이 `currentTermsVersion` 과 다르면 `outdated` 다(백엔드 합의: 동의한 버전이 현재 버전과 다르면 재동의를 받는다).
      화면은 outdated 를 동의 없음과 같이 다룬다(근처 기능 꺼짐). 지오펜스 등록도 `agreed` 만 통과시킨다.
      `currentTermsVersion` 이 없으면(필드가 없는 옛 서버) 비교할 수 없어 `agreed` 로 보지만, 이때 동의 PUT 은 버전을 얻지 못해 실패한다.
    - 재동의는 00d 와 설정에서만 묻는다. 동의 시트는 outdated 에서 열 때 제목만 "위치 정보 약관이 바뀌었어요"다(`termsChanged`, 열 때의 값을 닫힐 때까지 유지).
    - 동의는 OS 위치 권한과 분리해 동의 시트(`shared/ui/LocationConsentSheet`)에서 명시적으로 받는다(00d · 설정).
- 지오펜스 API(`shared/api/geofences.ts`)는 develop 코드(`GeofenceResponse` · `LocationEventRequest`)와 ADR003\_지오펜스 기준이다. docs/API.md 의 `regions[{…}]` · `dwellDelayMs` 모양은 옛 것이라 따르지 않는다.
    - `GET /api/geofences?latitude&longitude&platform=ANDROID`(앱은 ANDROID 고정) → `{ regions: [], resync: { geofenceId: "resync", latitude, longitude, radiusMeters } | null, places: [{ geofenceId: "place:{placeId}", placeId, latitude, longitude, radiusMeters }] }`.
      장소는 현재 위치에서 가까운 순으로 최대 99개(한도 100 에서 재조회 한 자리), 반경 150m. 재조회 반경은 max(3000, 가장 먼 장소 거리 + 500). 위치 동의가 없으면 빈 목록 · resync null 이다.
      서버는 철회되지 않은 동의만 보고 약관 버전은 보지 않는다(outdated 도 목록을 받는다). "agreed 만 등록"은 앱 쪽 게이트다.
    - `POST /api/location-events` `{ placeId, triggerEventId, eventType: "ENTER", eventOccurredAt, location? }` → 본문 없는 202. 중복 triggerEventId · 30분 지난 이벤트 · 회원 쿨다운(2시간) · 동의 없음이어도 똑같이 202 다.
      서버가 요청 안에서 꺼내기 실행과 알림 발송까지 마친 뒤 응답하므로 이 요청에만 시간 제한 10초를 건다. `reportLocationEvent` 는 예외를 던지지 않고
      sent · timeout(서버는 받았을 수 있음) · networkError · serverError(5xx) · rejected(4xx, 401 포함)를 돌려준다. 다시 보낼지는 호출자가 정한다(같은 triggerEventId 로 보내면 서버가 중복을 거른다).
- 지오펜스 판단 로직(`runtime/geofence`, ADR003 의 프론트 지침 기준).
    - 등록 계획(`planRegistration`, 순수): 장소 region 은 들어옴 · 나감을 모두, 재조회 region 은 나감만 받는다. identifier 는 서버 geofenceId 그대로다.
      장소 상태는 이전 상태가 있으면 그대로 두고, 새로 목록에 들어온 장소만 현재 위치에서 반경 안이면 inside 다. 목록에서 빠진 장소의 상태는 지운다.
    - **초기 트리거 거르기**: Android expo-location 은 초기 트리거(ENTER | EXIT)를 켜고 등록한다. inside 인 장소의 ENTER 는 무시하고,
      이전 outside 인데 지금 안에 있는 장소(등록 직전에 들어옴)는 초기 ENTER 로 보고된다. 앱을 처음 쓸 때 이미 안에 있는 장소는 보고하지 않는다.
    - 이벤트(`handleGeofenceEvent`): 장소 들어옴 → outside 였으면 inside 로 바꾸고 도착 보고(새 triggerEventId, eventOccurredAt = 처리 시각, location 없음)를 대기열에 넣고 바로 보낸다.
      장소 나감 → outside. 재조회 나감 → `resyncNeeded`(태스크가 다시 등록한다). 모르는 id 는 무시. 가상 도착(G4)도 같은 함수를 부른다(나감 → 들어옴이면 매번 보고된다).
    - **대기열**(`flushLocationEvents`): 앞에서부터 하나씩 보낸다. sent 는 지우고 401 이 아닌 4xx 는 버린다. timeout · networkError · serverError · 401 은 남기고 이번 보내기를 멈춘다(다음에 같은 triggerEventId).
      보내기 직전에 30분 넘은 이벤트는 버린다. 최대 20개. 시간 예산 12초(태스크 예산 15초에서 여유를 뺌): 첫 보내기는 늘 하고, 다음은 경과 + 10초 ≤ 12초일 때만.
      동시에 부르면 하나로 합친다. `clearGeofenceState`(등록 조건이 안 맞을 때 `syncGeofences` 가 부름)가 지우면 보내는 중이던 결과는 반영하지 않는다.
    - 상태 파일 하나(`storage` 키 `geofence-state`): 장소 상태 · 재조회 id · 마지막 등록(시각 · 중심 · id 목록) · 대기열. 깨졌거나 모양이 다르면 빈 상태로 시작한다.
- 지오펜스 등록 · 해제(`runtime/geofence/geofenceSync.ts` 의 `syncGeofences`). 지오펜싱은 expo-task-manager(네이티브)가 있는 개발 빌드에서만 동작한다.
    - **등록 조건**: signedIn, 위치 권한 '항상 허용', 위치 동의 `agreed`(outdated · withdrawn · never 는 안 됨, 서버는 버전을 보지 않으므로 앱 게이트).
      맞으면 현재 위치 → `fetchGeofences`(8초 제한) → `planRegistration`(이전 장소 상태) → `startGeofencingAsync`(목록이 비면 해제) → `applyRegistration` → `flushLocationEvents`.
      조건이 확실히 안 맞으면 해제하고 `clearGeofenceState`. 권한 · 동의 읽기 · 위치 · 목록 · 등록이 실패하면 지금 등록을 그대로 두고 failed.
      태스크 매니저 · expo-location 이 없는 빌드(Expo Go · 그 전 개발 빌드)에서는 아무것도 하지 않는다(skipped, 상태도 그대로).
    - 해제: 등록된 태스크가 있을 때만 `stopGeofencingAsync`. 백그라운드 위치 권한이 없으면 이것이 예외를 던져 `TaskManager.unregisterTaskAsync` 로 해제한다.
    - 위치: 마지막 위치(2분 이내 · 정확도 200m 이하) → 없으면 현재 위치(Balanced, 설정 대화상자 없음, 5초 제한) → 그래도 없으면 failed.
    - 실행 중에 또 불리면 하나로 합치고, 끝난 뒤 한 번 더 돈다.
    - **부르는 때**(`app/useGeofenceSync.ts`, RootNavigator): signedIn 이 된 직후, 앱이 앞으로 돌아올 때, 위치 설정 신호(`shared/permissions/locationSettings.ts`) —
      `setLocationConsent` 성공 · 위치 권한 요청 뒤에 shared 안에서 낸다. features 는 runtime 을 부르지 않는다. signedOut 이 되면 해제 · 지우기. 읽는 중 · 온보딩 중에는 부르지 않는다.
      OS 설정에서 바꾼 권한은 앱이 앞으로 돌아올 때 반영된다. 백그라운드 태스크의 재조회 지오펜스 나감에서도 부른다.
- **헤드리스 흐름**(`runtime/geofence/geofenceTask.ts`): `index.js` 가 번들 최상위에서 `defineGeofenceTask` 를 `registerRootComponent` 보다 먼저 부른다
  (백그라운드로 깨어나면 컴포넌트가 마운트되지 않고, 이벤트가 왔을 때 정의가 없으면 태스크 매니저가 태스크를 해제한다).
  태스크 매니저가 없으면 경고 한 번(앱은 그대로 뜬다). 태스크는 `registerSessionRefresher` → `ensureSessionRestored`(아직 읽기 전일 때만 복원) →
  signedOut 이면 해제, 온보딩 중이면 무시 → eventType(1 들어옴 · 2 나감)을 `handleGeofenceEvent` 로 → `resyncNeeded` 면 `syncGeofences("resync")` → `flushLocationEvents`.
  `restoreSession` 은 실제로 한 번만 돈다(App 과 태스크가 같은 결과를 기다린다. 다시 정하면 온보딩 중에 저장된 세션 때문에 온보딩을 건너뛴다).
- **시연 도구**(설정 맨 아래 "시연 도구" 묶음, `app/demo/DemoToolsSection.tsx`): `__DEV__` 이거나 `EXPO_PUBLIC_DEMO_TOOLS === "1"`(eas.json preview 프로필 env)일 때만 보인다.
  묶음은 runtime 을 불러야 해서 app 이 만들어 `shared/demo/demoTools.ts` 자리에 등록하고, 설정(features)은 등록된 묶음을 그리기만 한다. 꺼진 빌드에서는 등록 · 렌더가 일어나지 않는다.
    - 줄: 지오펜스 등록 상태("등록된 곳 N곳 · HH:mm" 또는 "등록된 곳 없음 · {마지막 등록 결과}") · 보낼 도착 N건 · "지금 다시 등록"(`syncGeofences`, 결과를 설명에) ·
      저장한 장소별 "{장소}에 도착"(목록 API 중 장소가 있는 저장물만 상세를 하나씩 읽음, 묶음을 처음 그릴 때 한 번) · 목 모드면 목 서버가 받은 도착 기록(최근 순).
      목 도착 기록은 목 서버가 만든 알림이 있으면 눌러서 연다(알림 누름과 같은 opener, `openFromNotification`).
    - 가상 도착(`simulateArrival`)은 **누르면 바로 보낸다.** RN Android 는 앱이 백그라운드면 JS 타이머가 멈춰(JavaTimerManager `onHostPause`) 예약하지 않는다. 보낸 뒤 홈으로 내리고 알림을 기다린다.
      등록된 장소면 `handleGeofenceEvent` 에 나감 → 들어옴(실제 이벤트와 같은 길, 반복해도 매번 보고), 등록 안 된 장소면 장소 상태는 그대로 두고 같은 대기열로 보낸다. 결과는 보냄 · 남김 · 버림.
- 00b 의 약관 문구는 지금 링크 없이 문구만 있다. 이용약관 · 개인정보 처리방침 페이지가 생기면 링크로 연결한다.
- 의존 방향: `shared/api/client.ts` → `shared/auth/session.ts`, `shared/auth/login.ts` → `client.ts` · `session.ts` · `providers.ts`.
  `session.ts` 는 클라이언트를 import 하지 않는다(순환 방지). 둘이 같이 읽는 API 주소는 `shared/api/config.ts` 에 둔다.

## API 클라이언트

- 기본 주소는 `EXPO_PUBLIC_API_BASE_URL` 이다. 배포 주소가 정해질 때까지 로컬 백엔드(`http://<PC LAN IP>:8080`)를 쓴다.
- **목 모드**: 주소가 비어 있거나 `EXPO_PUBLIC_FORCE_MOCK` 이 "1" 이면(시연 백업용 preview-mock 프로필. preview 환경의 주소를 무시한다) 서버를 부르지 않는다.
  판정은 `shared/api/config.ts` 의 `isApiConfigured` 한 곳이다. 두 값은 번들에 인라인되도록 `process.env.이름` 으로 정적으로 읽는다.
  eas.json env 는 빈 문자열을 거절해서(eas-json 스키마 `Joi.string()`) 주소를 비우는 대신 이 플래그를 쓴다. 시작은 `MOCK_SCENARIO.signedIn`, 로그인은 제공자 로그인(목)만 하고 세션을 저장하지 않는다(가짜 토큰을 저장소에 남기지 않는다).
- 요청 시간 제한은 15초다. 저장 API 는 분석을 기다리지 않고 바로 돌아오므로(분석은 서버에서 비동기) 같은 제한을 쓴다.
- 성공 상태 코드가 필요하면 `requestWithStatus` 를 쓴다(저장 API 의 201 새로 저장 · 200 이미 저장). 그 밖에는 `request` 로 본문만 받는다.
- 로그인 요청의 401(제공자 자격 증명 거절)은 세션 만료가 아니라 로그인 실패다(00a-err · 00b-err).
  http(cleartext) 허용은 개발 빌드에서만 켜고 출시 빌드에는 넣지 않는다.
- 세션이 있으면 Bearer 헤더를 붙인다. 응답은 래핑하지 않고, 에러 바디 `{code, message}` 는 `ApiError` 로 바꾼다.
- 세션을 실어 보낸 요청의 401 · 만료:
    - 게스트 세션: 같은 설치 id 로 조용히 다시 받고(동시에 여러 요청이 와도 한 번만) 원래 요청을 **딱 한 번** 다시 보낸다. 다시 보낸 요청이 또 401 이면 반복하지 않는다.
      만료가 보이면 보내기 전에 미리 다시 받는다. 다시 받기가 통신 오류면 로그아웃하지 않고 원래 오류를 넘긴다. 거절(4xx)이면 세션을 지우고 00a(설치 id 는 남긴다).
    - 카카오 세션: 세션을 지우고 00b.
    - 클라이언트는 다시 받는 함수를 `setSessionRefresher` 로 주입받는다(App 시작 때 `login.ts` 가 등록). `client.ts` 는 `login.ts` 를 import 하지 않는다.
- 인증 · 저장물 · 동의 · 지오펜스 · 기기 · 알림 API 가 클라이언트를 거친다. 근처 탭 · 지도 탭은 아직 화면 목(`shared/api/mock/`)에서 받는다.

## 저장 보내기 대기열

- 공유받은 링크는 바로 `savePersonalSave` 로 보낸다. 보내지 못하면 `runtime/background/pendingQueue.ts` 에 남겨 두고 나중에 다시 보낸다.
  세션이 없거나 온보딩 중에 들어온 공유도 대기열에만 넣는다.
- 항목은 정규화된 url · videoId · 넣은 시각이다. 같은 videoId 는 한 번만 두고, 최대 20개(넘치면 가장 오래된 것부터 버린다).
- 저장 수단은 `expo-file-system` 파일 하나(`Paths.document/pending-saves.json`)다. 링크는 비밀이 아니라 안전한 저장소를 쓰지 않는다.
  모듈은 처음 쓸 때 불러오고, 없거나 읽기 · 쓰기에 실패하면 이번 실행 동안 메모리에만 둔다.
- drain: 동시에 두 번 돌지 않는다. 앞에서부터 하나씩 보내고,
  성공(201 · 200)과 401 을 뺀 4xx 는 지운다. 응답 없음(네트워크 · 시간 초과) · 401 · 5xx · 그 밖의 예외는 남기고 이번 drain 을 멈춘다.
  결과(보낸 수 · 남은 수 · 버린 수)는 아직 화면에 보이지 않는다. 재시도 횟수 제한 · 백오프는 없다.
- 다시 보내는 시점은 `app/usePendingSaveSender.ts` 다. RootNavigator 가 `signedIn` 을 넘긴다.
  signedIn 이 된 직후 한 번, 그 뒤 AppState `active` 마다 보낸다. 세션이 없거나 온보딩 중에는 보내지 않는다.
- 목 모드도 같은 규칙이다. `MOCK_SCENARIO.saveResult` 가 networkError 면 남고, invalid 면 버린다.

## 공유 받음 처리

- 처리 함수는 `app/share/shareReceiver.ts` 의 `receiveSharedText(text)` 하나다. 실제 공유와 개발용 딥링크가 이것을 부른다.
  세션을 읽는 중(loading)이면 끝날 때까지 기다린다(`waitForAuthResolved`).
- 실제 공유는 `app/share/nativeShareIntent.ts` 가 받는다. expo-share-intent(app.json 플러그인: `disableIOS`, `androidIntentFilters: ["text/plain"]`,
  launchMode 는 플러그인 기본 singleTask)를 처음 쓸 때 불러오고, 네이티브 모듈이 없으면(Expo Go · 옛 개발 빌드) 경고 한 번 뒤 아무것도 하지 않는다.
  리스너를 붙인 뒤 `getShareIntent` 를 한 번 불러 콜드 스타트 공유를 받고, 실행 중 공유는 네이티브가 바로 보낸다. 넘긴 뒤 `clearShareIntent`.
  받는 값은 공유 텍스트(`text`), 없으면 `webUrl` 이다.
- 알려진 한계: 프로세스가 죽은 뒤 최근 앱 목록에서 다시 열면 Android 가 원래 SEND intent 로 Activity 를 다시 만들어 같은 공유가 다시 처리될 수 있다.
- signedIn 이 아니면 링크를 대기열에만 넣고 시트는 띄우지 않는다(온보딩을 마치고 Main 에 들어오면 대기열이 보내진다).
- signedIn 이면 지금 화면 위에 공유 받음 시트를 띄운다. 링크 없음 → NotShorts. 있으면 Saving → 저장.
  201 Saved · 200 AlreadySaved · 400 NotShorts, 그 밖의 실패는 대기열에 넣고 Queued. 저장이 성공하면 대기열도 이어서 보낸다.
- 시트가 떠 있는 동안 새 공유가 오면 새 결과로 바꾼다. 앞선 공유의 늦은 결과는 버린다.
- "돌아가기"는 시트를 닫고 앱을 뒤로 보낸다(`BackHandler.exitApp`). "기억 탭 보기"(Saved · AlreadySaved, Saving 에서는 비활성)는
  `navigationRef` 로 기억 탭에 간다. 바깥 닫힘은 시트만 닫고, Saving 중에는 버튼 · 끌기 · 배경 · 뒤로를 막는다.
- 썸네일은 플랫폼 파서의 `thumbnailUrl`(YouTube 는 `shared/external-links/youtubeLinks.ts` 의 `hqdefault.jpg`)을 9:16 으로 가운데 잘라 보인다. 불러오지 못하면 자리표시.
- 개발용 진입: `__DEV__` 에서만 `remembrall://dev/share?text=…`(Expo Go 는 `exp://<Metro 주소>/--/dev/share?text=…`)를 받아 처리 함수를 부른다.
  linking 설정은 이 주소를 화면 경로로 해석하지 않는다(`filter`).

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
- 설정(11) 위치 정보 이용 동의(11c 2202:915 · 11c-off 2202:986): 화면에 포커스될 때 서버에서 읽는다(확인하고 있어요 · {M}월 {D}일에 동의했어요 · 동의하지 않았어요 · 확인하지 못했어요).
  동의 안 함이면 동의 행과 "근처에 오면 알려주기" 행(11b 보다 우선, 설명 "위치 정보 이용 동의가 필요해요. 눌러서 켜주세요")이 동의 시트를 띄운다. 설정에서는 동의만 기록하고 권한은 요청하지 않는다.
  예전 약관 동의(outdated)는 동의 행 설명이 "약관이 바뀌어 다시 동의가 필요해요"이고, 동의 행 · 근처 알림 행이 동의 안 함과 같이 동의 시트를 띄운다(거두기 없음).
  동의함이면 동의 행이 11d 거두기 시트(2202:1049)를 띄운다. 동의 · 거두기가 성공하면 PUT 응답으로 바로 갱신하고, 실패하면 시트에 오류를 보인다. 읽기에 실패하면 행을 눌러 다시 읽는다.
- 설정(11) 약관 및 정책: 웹 페이지(`TERMS_URLS.policies`)를 연다. URL 이 없는 동안은 오픈소스 라이선스처럼 눌리지 않는다.
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
- Firebase 설정(`google-services.json`, 패키지 `com.remembrall.app`)은 공개 레포라 커밋하지 않는다(gitignore). `app.config.ts` 가 `android.googleServicesFile` 을 정한다:
  EAS file 환경 변수 `GOOGLE_SERVICES_JSON`(경로) → 없으면 `frontend/google-services.json` 이 있을 때 그 파일 → 둘 다 없으면 설정하지 않고 경고(FCM 토큰을 받지 못하는 빌드).
  환경 변수를 넣었는데 그 경로에 파일이 없으면 config 평가를 실패시킨다(FCM 없는 빌드가 조용히 나오지 않게).
- expo-notifications 플러그인은 알림 색(`color` 브랜드 자두색 #6B4E7A)과 FCM 기본 채널(`defaultChannel` `nearby`, "인증 · 세션" 절의 알림 채널)을 정한다. small icon 은 아직 없다.
- 키 제한: Maps SDK for Android 만, 패키지 `com.remembrall.app` + SHA-1 두 개(EAS 개발 빌드 keystore, 로컬 debug keystore).
  debug keystore SHA-1 은 모든 RN 프로젝트 공용이라 개발용 키에만 등록한다.
- 개발 빌드 딥링크 scheme 은 `remembrall://` 이다(`app.json` 의 `scheme`). 실행 · 키 설정 절차는 README 에 둔다.
- 환경 변수: `KAKAO_NATIVE_APP_KEY`(카카오 네이티브 앱 키. 카카오를 다시 켤 때 `app.config.ts` 가 config plugin 과 `extra` 에 넣는다. 지금은 읽지 않는다),
  `EXPO_PUBLIC_API_BASE_URL`(API 기본 주소. 비밀이 아니라 번들에 들어간다). 저장소에 값을 넣지 않는다.

## 목 데이터

- `shared/api/mock/` 은 화면 props 모양 그대로 둔다. 서버 응답 계약처럼 만들지 않는다.
  서버 계약 모양의 목(API 함수의 목 분기)은 API 모듈 안이나 옆에 둔다(`consents.ts`, `personalSavesMock.ts`).
- 화면 목을 쓰는 features(근처 탭 · 지도 탭 · 지도 · 근처에서 연 08)는 mock 에서 직접 받는다. `client.ts` 와 `mappers/` 를 거치지 않는다.
  API 를 쓰는 화면: 기억 탭 · 저장 결과 · 저장물로 연 08 은 저장물 API(`personalSaves.ts` · `usePersonalSaveDetail.ts`)와 `mappers/personalSaveMapper.ts`,
  00d · 설정(11)은 동의 API(`consents.ts`)와 `mappers/consentMapper.ts`, 알림으로 연 08 은 알림 API(`notifications.ts` · `useNotificationDetail.ts`)를 쓴다. 목 모드에서는 각 API 의 목 분기가 답한다.
- mock 은 features 의 타입을 import 하지 않는다(shared → features 금지). 구조적 타입으로 맞춘다.
- 목 이미지 URL(picsum 고정 id)은 이 폴더 안에만 둔다.
- 목 모드에서 세션과 로그인은 목 플래그다. `MOCK_SCENARIO.signedIn`(시작 시 세션 여부, 기본 false(개발 중에 팀원이 바꿈). true 면 근처 탭부터) · `loginResult`(success · cancelled · failed).
  온보딩 일러스트 사진은 목 이미지다(`mock/onboarding.ts`). 첫 실행은 오프라인일 수 있어 나중에 번들 이미지로 바꾼다.
- 지도(10 · 08)의 현재 위치와 denied 분기는 목 플래그(`MOCK_SCENARIO.locationPermission`)와 목 좌표(`mapViewMock`)다.
  설정(11)과 지오펜스는 실제 권한 · 위치를 쓴다.
- 위치기반서비스 동의는 목 모드에서 목 플래그다. `MOCK_SCENARIO.locationConsent`(시작 시 동의 상태, never · agreed · withdrawn, 기본 never) · `consentSaveResult`(동의 저장 결과, ok · fail, 기본 ok) ·
  `locationTermsVersion`(목 서버의 현재 약관 버전, v1 · v2, 기본 v1). 시작 상태의 동의 기록은 v1 이라 v2 + agreed 면 outdated 다.
  목 PUT 도 서버처럼 현재 버전이 아닌 동의를 400 으로 거절한다. 공용 후보 동의의 목 현재 버전은 v1 고정.
- 지오펜스 API 는 목 모드에서 `geofencesMock.ts` 가 답한다. 목록은 목 위치 동의(agreed, 버전 무관)와 저장물 목의 장소(분석 실패 제외)로 서버 규칙대로 만든다.
  도착 보고는 이번 실행 동안 메모리에 남는다(`getMockLocationEvents`). `MOCK_SCENARIO.locationEventResult`(ok · networkError · timeout, 기본 ok):
  networkError 는 기록하지 않고, timeout 은 기록한 뒤 timeout 을 돌려준다. 같은 triggerEventId 는 다시 기록하지 않고 sent.
- 알림 API 는 목 모드에서 `notificationsMock.ts` 가 답한다. 새로 기록한 도착마다 목 위치 동의가 agreed 이고 그 장소를 가진 저장물(분석 실패 제외)이 있으면
  PLACE 알림 1개(PERSONAL 항목, 그 장소)를 만든다. 서버의 꺼내기 판단 · 회원 쿨다운은 흉내 내지 않는다(시연에서 반복).
  푸시 대신 로컬 알림(`presentLocalNotification`)을 "근처 알림" 채널로 바로 띄운다. data 는 FCM 과 같은 `{ notificationId }`, 식별자도 notificationId 라
  같은 알림은 새로 쌓이지 않고 바뀌고, 누름은 실제 알림과 같은 길(notificationListener → opener)을 탄다. 목 모드에서만 띄운다(실제 모드는 목 서버가 불리지 않고, 띄우기 전에도 한 번 더 막는다).
  알림 권한이 granted 가 아니거나 Expo Go · 모듈 없는 빌드면 띄우지 않고 알림만 만든다(시연 도구 기록 줄로는 연다).
  문구: 제목은 장소 이름, 본문은 "{저장 시점}에 저장한 곳이 근처에 있어요"(기억 탭과 같은 `savedAgo`, "방금"이면 "방금 저장한 곳이 근처에 있어요"). 상세의 title · body 도 같다.
  목 도착 보고에 목 지연(600ms, JS 타이머)이 있어, "도착"을 누르자마자 홈으로 내리면 앱으로 돌아올 때까지 알림이 뜨지 않는다(백그라운드에서 JS 타이머가 멈춘다).
  상세는 읽을 때 저장물을 다시 확인해 지웠으면 items 가 빈다. 없는 id 는 404, 열람은 멱등. 이번 실행 동안 메모리에 남는다(`getMockNotifications`).
- 저장물 API 는 목 모드에서 메모리 저장소다. `MOCK_SCENARIO.archiveEmpty` 면 빈 저장소, 아니면 샘플 6개로 시작한다
  (장소 있는 성공 2 · 분석 중 1 · 실패(EXTRACTION_API_ERROR) 1 · 볼 수 없는 영상 1 · 장소 없음 1, 저장 시각은 오늘 · 3주 전 · 2달 전).
  샘플 영상 id 는 가짜(`mockSave001`~`006`)라 썸네일은 자리표시다. 분석 중 샘플은 앱을 켜고 약 5초 뒤 `analysisResult` 로 끝난다.
  `MOCK_SCENARIO.saveResult`(저장 결과, ok · networkError · invalid, 기본 ok) ·
  `analysisResult`(저장하고 약 5초 뒤 상세 · 목록에 보일 분석 결과, success · noPlace · failed · videoUnavailable · analysisError, 기본 success).
  같은 영상을 다시 저장하면 `created: false` 다. 공유로 저장한 것도 같은 저장소라 기억 탭에 바로 보인다.
  `analysisResult: "analysisError"` 는 EXTRACTION_RESULT_ERROR 로 끝난다(05 analysisError 문구 확인용).
  저장 결과 · 저장물로 연 08 도 이 목을 읽는다. 개발용 딥링크의 목 키(`saveResults`)와 지도 · 근처의 08(`placeDetails`)은 화면 목이다.
- 장소 확정 저장물(ready)은 상세(08)로, 미확정 저장물은 저장 결과(02~05)로 간다.
- 거리 표시: 도보 30분 이내는 "도보 N분", 30분을 넘으면 직선거리 "N.Nkm". 목 데이터는 이 규칙대로 문자열을 넣는다.

## 의존 방향

```
features/ ──┐
            ├──> domain/  <──── runtime/
shared/  ───┘                     │
   ^                              │
   └──────────────────────────────┘
```

- `runtime/` 은 `shared/`(API · 저장소)를 쓴다. `shared/` 는 `runtime/` 을 import 하지 않는다.

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
  라우팅 파라미터나 캐시 키로 쓰면 안 된다. 전역 장소 키는 서버 Place 의 `placeId`(UUID)다.
- **인증은 구현 완료다.** 카카오 로그인 · 로그아웃 · 세션(Bearer) · FCM 토큰 등록 API 가 코드에 있다("인증 · 세션" 절). 앱은 FCM 토큰 등록까지 연결했다.
- **목록 필드는 빈 배열 보장**이 문서 규칙이나, validation 애노테이션이 코드에 0개라
  강제되지 않는다. 방어 코드를 둔다.

저장물 API(`/api/personal-saves`, 타입은 `shared/api/personalSaves.ts`):

```ts
type AnalysisStatus =
    | "PENDING"
    | "ANALYZING"
    | "SUCCESS"
    | "PARTIAL"
    | "FAILED";
type AnalysisFailureCode =
    | "VIDEO_UNAVAILABLE"
    | "EXTRACTION_API_ERROR"
    | "EXTRACTION_RESULT_ERROR"
    | "PLACE_SEARCH_API_ERROR"
    | "PLACE_VERIFICATION_API_ERROR";
```

- **분석 진행 상태는 `analysisStatus` 다.** PENDING · ANALYZING 은 대기 UI(03 분석 중)로 본다.
- **완료 통지는 따로 없다.** 앱이 상세 · 목록을 다시 읽어 확인한다(언제 다시 읽는지는 "내비게이션" 절의 기억 탭 · 저장 결과).
- **PARTIAL 은 끝난 것으로 본다.** SUCCESS · PARTIAL 은 장소가 있으면 ready, 없으면 noPlace(`mappers/personalSaveMapper.ts`).
- 실패 코드는 상세에만 있고 PARTIAL · FAILED 일 때만 채워진다. 같은 영상을 다시 저장하면 서버가 다시 분석하는 실패는
  `EXTRACTION_API_ERROR` 뿐이다(`VIDEO_UNAVAILABLE` 은 다시 분석해도 소용없다).
- 저장 응답은 새로 저장하면 201, 이미 저장한 영상이면 200 이고 본문은 같다.

## 미확정 (앱 모델은 가설이다)

아래는 `domain/` 에 타입을 만들어 두되, **백엔드 합의 전까지 확정이 아니다.**
해당 파일 상단에 미확정임을 주석으로 남긴다.

| 항목                                    | 현재 상태                                                                             | 영향                                                                                                                                                                                                                  |
| --------------------------------------- | ------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 장소 판정 결과 (확정 / 확인필요 / 없음) | 서버 상세에 `confirmCandidates` 가 있다. 앱은 되묻기 연결 전                          | 되묻기 화면(02 확인 필요 · 04 장소 없음) 의 근거                                                                                                                                                                      |
| 카테고리                                | 팀 합의: 고정 enum 소수 + 분위기 태그 다수. enum 값은 미확정                          | 01 필터 칩 라벨(전체/식당/카페/가볼 곳)은 enum 확정 전까지 목 데이터 한정                                                                                                                                             |
| 비공개·삭제 영상의 링크 보관 여부       | 서버 저장 정책 문제. 백엔드 합의 필요. 05 디자인은 "링크는 그대로 보관" 으로 안내한다 | (b) "다시 시도" 가 모든 실패에 똑같이 노출된다. 재시도가 의미 없는 비공개·삭제(`VIDEO_ACCESS`)에도 보인다. 재시도 가능 여부를 서버가 내려줘야 버튼 노출을 가를 수 있다. (07 실패 셀이 생겨 05 재진입 경로는 해결됐다) |
| 꺼내기 제안 유형                        | 문서상 가설이 5개 (장소 1곳 / 코스 / 장소 없는 콘텐츠 재노출 / 공용 풀 보충 / 침묵)   | **SINGLE·COURSE 2분기로 부족하다.** 열린 형태로 둔다                                                                                                                                                                  |
| 알림 발생원 (FCM / 로컬)                | 백엔드가 firebase-admin 으로 FCM 을 보낸다(channelId 없음). 앱은 토큰을 등록한다      | `runtime/notifications/notificationListener.ts` 안에서만 갈린다                                                                                                                                                       |
| API 서버 주소                           | 배포 주소 미정(백엔드 확인 중). 개발은 로컬 백엔드 `http://<PC LAN IP>:8080`          | `EXPO_PUBLIC_API_BASE_URL` 만 바꾼다                                                                                                                                                                                  |
| 지오펜스 보고 엔드포인트                | 서버 코드 있음(늘 202). 클라이언트 `shared/api/geofences.ts`                          | 서버가 조용히 무시할 수 있어 프론트가 결과를 알 방법이 필요                                                                                                                                                           |

## 화면으로 만들 것 vs 상태로 표현할 것

공유하면 앱이 앞으로 뜨고 지금 화면 위에 공유 받음 시트(저장 중 · 저장함 · 이미 저장함 · 대기열 · Shorts 아님)가 뜬다.
분석 결과는 알림이 아니라 기억 탭에서 여는 저장 결과 모달(02~05)로 본다.

| 경우                                         | 표현                                                                     |
| -------------------------------------------- | ------------------------------------------------------------------------ |
| 지원하지 않는 링크                           | 공유 받음 시트 NotShorts. 저장하지 않음                                  |
| 비공개·삭제 영상 (`FAILED` / `VIDEO_ACCESS`) | 05 저장 결과(볼 수 없는 영상 문구) + 기억 탭 실패 셀                     |
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
