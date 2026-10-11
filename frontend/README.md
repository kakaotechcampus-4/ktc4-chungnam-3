# REMEMBRALL 프론트엔드

저장만 해두고 잊어버린 콘텐츠를, 에이전트가 꺼내주는 앱

저장해둔 장소 영상을 근처에 갔을 때 다시 꺼내주는 앱의 React Native(Expo) 클라이언트다.
지금은 UI 단계라 화면이 목 데이터로 렌더된다.

## 실행

```bash
npm install
npx expo start
```

`expo-dev-client` 가 들어 있어 `npx expo start` 는 개발 빌드 모드로 뜬다.
폰에 설치한 개발 빌드(Remembrall)로 터미널의 QR 코드를 스캔하거나, 앱 첫 화면의 서버 목록에서 고른다.
에뮬레이터가 있으면 `a` 를 누른다.

Expo Go 로 보려면 터미널에서 `s` 를 눌러 Expo Go 모드로 바꾼 뒤 Expo Go 로 스캔한다.
지도(10·08)는 Expo Go SDK 57 안드로이드에서 검은 화면으로 나온다([expo/expo#49323](https://github.com/expo/expo/issues/49323)).
지도가 없는 화면만 볼 때 쓴다.

## 개발 빌드

지도는 우리 Google Maps API 키를 넣은 개발 빌드에서 확인한다.

1. EAS 프로젝트 `remembrall` 은 Expo 조직 `ktc4-chungnam-3` 소유다. 조직 초대를 받은 뒤 로그인한다.
    ```bash
    npx eas-cli login
    ```
2. 빌드한다. 끝나면 나오는 링크나 QR 코드로 APK 를 폰에 설치한다.
    ```bash
    npx eas-cli build --profile development --platform android
    ```
3. JS 만 바뀌면 다시 빌드하지 않는다. 네이티브 의존성, `app.json`, `app.config.ts` 가 바뀌면 다시 빌드한다.

첫 빌드 뒤에는 keystore 를 백업한다. `npx eas-cli credentials -p android` → development 프로필 → Keystore 에서 내려받는다.
백업 파일은 저장소에 커밋하지 않는다.

### Google Maps API 키

`app.config.ts` 가 빌드 시점에 `GOOGLE_MAPS_ANDROID_API_KEY` 를 읽어 react-native-maps 플러그인에 넣는다.
번들에 들어가지 않도록 `EXPO_PUBLIC_` 접두어를 쓰지 않는다. 키가 없으면 경고만 뜨고 빌드는 된다. 이때 지도는 비어 보인다.

- EAS 빌드: 빌드 프로필의 `environment` 에 환경 변수로 등록한다. 값은 명령줄에 쓰지 않고 프롬프트에 입력한다(셸 히스토리에 남지 않게).
    ```bash
    npx eas-cli env:set development --name GOOGLE_MAPS_ANDROID_API_KEY --type string --visibility secret
    ```
    preview · production 빌드 전에는 해당 환경에도 등록한다. production 에는 출시용 키(아래 키 제한 참고)를 넣는다.
    ```bash
    npx eas-cli env:set preview --name GOOGLE_MAPS_ANDROID_API_KEY --type string --visibility secret
    npx eas-cli env:set production --name GOOGLE_MAPS_ANDROID_API_KEY --type string --visibility secret
    ```
    secret 변수는 빌드 서버에서만 읽힌다. `eas build` 를 시작할 때 로컬에 "키가 없습니다" 경고가 떠도 정상이다.
- 로컬 빌드(`npx expo run:android`): `frontend/.env.local` 에 `GOOGLE_MAPS_ANDROID_API_KEY=<키>` 를 적는다. gitignore 돼 있다.

Google Cloud 콘솔에서 키를 제한한다.

- API 제한: Maps SDK for Android 만 허용한다.
- 애플리케이션 제한: Android 앱. 패키지 `com.remembrall.app` 에 SHA-1 두 개를 등록한다.
    - EAS 개발 빌드 keystore: `npx eas-cli credentials -p android` → development 프로필 → Keystore 의 SHA1 Fingerprint
    - 로컬 debug keystore: `npx expo prebuild -p android` 뒤
        ```bash
        keytool -list -v -keystore android/app/debug.keystore -alias androiddebugkey -storepass android
        ```

RN 기본 debug keystore 는 모든 프로젝트 공용이라 SHA-1 도 같다.
이 SHA-1 은 개발용 API 키에만 등록하고, 출시용 키에는 릴리스(Play 앱 서명) SHA-1 만 등록한다.

## 딥링크로 화면 열기

저장 결과 모달(02~05)처럼 앱 안에서 바로 갈 수 없는 화면은 딥링크로 연다.

개발 빌드는 scheme `remembrall://` 을 쓴다.

```bash
adb shell am start -W -a android.intent.action.VIEW -d "remembrall://save-result/failed" com.remembrall.app
```

Expo Go 에서는:

1. Expo Go 홈 → Enter URL manually → `exp://<Metro 주소>/--/save-result/failed`
   (`<Metro 주소>` 는 `npx expo start` 가 보여주는 `192.168.x.x:8081` 같은 값)
2. adb 가 있으면:
    ```bash
    adb shell am start -W -a android.intent.action.VIEW -d "exp://<Metro 주소>/--/save-result/failed" host.exp.exponent
    ```

경로는 `save-result/needs-confirmation`, `save-result/analyzing`, `save-result/no-place`, `save-result/failed`,
`detail/sungsimdang`, `nearby`, `map`, `archive`, `settings` 가 있다.
후보가 1곳인 확인 필요(02b)는 `save-result/needs-confirmation-one` 이다.
장소 직접 찾기(02c)는 `save-result/needs-confirmation/search` 이다.
빈 필드 장소 상세(08b)는 `detail/daeheung-cathedral` 이다.
없는 id 로 열면 저장물 없음(12)이 뜬다. 예: `save-result/unknown`, `detail/unknown`.

빈 상태(06 · 07b), 위치 권한, 깨진 썸네일은 `src/shared/api/mock/scenario.ts` 의 `MOCK_SCENARIO` 플래그를 바꾸고 reload 해서 본다.

## 로컬 백엔드 연결

`EXPO_PUBLIC_API_BASE_URL` 이 없으면 목 모드다(서버를 부르지 않고 목 로그인 · 목 세션). 배포 주소가 정해질 때까지는 PC 에서 띄운 백엔드에 붙인다.

1. 백엔드를 PC 에서 실행한다(기본 포트 8080).
2. `ipconfig` 로 PC 의 LAN IP(와이파이 어댑터의 IPv4)를 확인하고 `frontend/.env.local` 에 적는다.
    ```
    EXPO_PUBLIC_API_BASE_URL=http://192.168.x.x:8080
    ```
3. 값은 JS 번들에 들어가므로 바꾸면 Metro 를 다시 시작한다: `npx expo start --clear`
4. 폰과 PC 가 같은 와이파이에 있어야 한다.
5. PC 방화벽에서 8080 인바운드를 개인 네트워크에서만 연다(관리자 PowerShell).
    ```powershell
    New-NetFirewallRule -DisplayName "Remembrall API 8080" -Direction Inbound -Protocol TCP -LocalPort 8080 -Action Allow -Profile Private
    ```
    와이파이가 Windows 에서 "공용 네트워크"로 잡혀 있으면 이 규칙이 적용되지 않는다. 설정 → 네트워크 및 인터넷 → Wi-Fi → 연결된 네트워크 속성에서 "개인 네트워크"로 바꾼다.
    작업이 끝나면 규칙을 지운다.
    ```powershell
    Remove-NetFirewallRule -DisplayName "Remembrall API 8080"
    ```

- 주소를 지우고 Metro 를 다시 시작하면 목 모드로 돌아간다.
- 개발 빌드는 JS 를 Metro 에서 받으므로 EAS 환경 변수는 필요 없다. 출시 빌드를 만들 때 등록한다.
- 세션 저장(expo-secure-store)은 네이티브 모듈이다. 이 모듈이 들어가기 전에 만든 개발 빌드에서는 실제 모드에서 세션이 이번 실행 동안만 메모리에 남고, 경고("개발 빌드를 다시 만드세요")가 뜬다. 앱을 다시 켜면 다시 로그인해야 한다. 저장까지 확인하려면 Expo Go 를 쓰거나 개발 빌드를 다시 만든다. 목 모드는 이 모듈을 불러오지 않는다.
- MVP 는 게스트 세션이다. 00a "시작하기"가 `POST /api/auth/guest` 를 부른다. 백엔드에 이 엔드포인트가 생기기 전에는 실제 모드에서 00a-err 가 뜨는 것이 정상이다(401 `INVALID_SESSION`. 엔드포인트가 아직 없고 서버의 인증 허용 목록에도 없다).
- expo-crypto 가 없는 개발 빌드에서는 설치 id 를 만들 수 없어 실제 모드에서 00a-err 가 뜬다(경고: 개발 빌드를 다시 만드세요). 목 모드는 영향 없다.

## 온보딩 다시 보기

지금은 세션과 로그인이 목이다(`MOCK_SCENARIO`). Expo Go 에서도 확인할 수 있다.

- 처음부터(00a): `signedIn: false` 로 바꾸고 Metro 터미널에서 `r` 로 reload 한다. 기본값 `true` 는 근처 탭부터 뜬다.
- 시작 결과: `loginResult` 를 바꾼다. `"success"` → 00c 로, `"failed"` → 00a-err. (`"cancelled"` 는 카카오를 다시 켰을 때 00b 에 그대로 머무는 경우다.)
- 흐름(MVP): 00a → 00c → 00d → 00e → 00f → 근처 탭. 00b 는 건너뛴다. 00d 에서 "나중에 할게요"를 누르면 00e 를 건너뛴다.
  권한 단계(00c~00f)에서 시스템 뒤로 가기를 누르면 앱이 닫힌다.

## 권한 확인

00c · 00d · 00e 는 실제 권한을 요청하고, 설정(11)은 실제 권한 상태를 보여 준다.

| 확인 항목                                       | Expo Go                                           | 권한 모듈 없는 개발 빌드 | 다시 만든 개발 빌드 |
| ----------------------------------------------- | ------------------------------------------------- | ------------------------ | ------------------- |
| 앱이 멈추지 않고 시작                           | 가능                                              | 가능(경고만 뜬다)        | 가능                |
| 00c 알림 권한 창(안드로이드 13+)                | 불가(창 없이 00d, 다시 만든 개발 빌드에서만 확인) | 창 없이 넘어감           | 가능                |
| 00d 앱 사용 중 위치 창, 허용 → 00e · 거절 → 00f | 가능                                              | 창 없이 넘어감           | 가능                |
| 00e '항상 허용' 설정 화면                       | 미확인(개발 빌드에서 확인)                        | 창 없이 넘어감           | 가능                |
| 설정(11) 위치 권한 표시 · 돌아올 때 갱신        | 가능(Expo Go 앱의 권한)                           | 목 값                    | 가능                |
| 설정 11b(기기 알림 꺼짐)                        | 불가(원래 스위치, 다시 만든 개발 빌드에서만 확인) | 원래 스위치              | 가능                |

SDK 53 부터 안드로이드 Expo Go 에서는 expo-notifications 를 불러오는 순간 예외가 난다(57.0.21 의 `warnOfExpoGoPushUsage`. 문서는 로컬 알림은 된다고 하지만 이 버전의 코드는 불러오기만 해도 막힌다). 그래서 Expo Go 에서는 이 모듈을 불러오지 않고(`isRunningInExpoGo()`), 알림 권한은 확인하지 않는다.

00e: expo-location 문서는 안드로이드 11+ 에서 백그라운드 위치 요청이 시스템 설정 화면을 연다고 적는다. 안드로이드 Expo Go 는 위치 '서비스'를 지원하지 않는다고만 적혀 있어, Expo Go 에서의 백그라운드 권한 요청은 확인하지 않았다. 다시 만든 개발 빌드에서 확인한다.

권한을 처음 상태로 되돌려 다시 보려면 앱 정보 → 저장공간 → 데이터 지우기, 또는

```bash
adb shell pm reset-permissions -p com.remembrall.app
```

## 포맷

prettier 설정은 `.prettierrc` 에 있고, 버전은 `package.json` 에 고정돼 있다(3.7.4).
VS Code 의 Prettier 확장은 `npm install` 로 받은 이 버전과 설정을 쓴다. 개인 VS Code 설정의 prettier 옵션은 적용되지 않는다.

```bash
npm run format        # 전체 포맷
npm run format:check  # 검사만
```

Android Studio · IntelliJ 는 `.editorconfig` 로 같은 들여쓰기(4칸)와 줄바꿈(LF)을 따른다.

### 줄바꿈 (LF)

`frontend/.gitattributes` 가 frontend 텍스트 파일을 Windows 에서도 LF 로 받게 한다.
이 설정이 들어오기 전에 받은 클론은 워킹트리가 CRLF 로 남아 있어 `format:check` 가 모든 파일을 잡는다. 한 번만 다시 받는다.

실행 전에 frontend 의 미커밋 변경을 커밋하거나 stash 한다. 아래 명령은 frontend 의 추적 파일을 HEAD 내용으로 다시 쓴다.
`frontend` 폴더 안에서 실행하며, frontend 밖 파일(backend · AI 등)은 건드리지 않는다.

```bash
cd frontend
git rm -r --cached -q .
git restore --source=HEAD --staged --worktree .
```

## 문서

- [STRUCTURE.md](STRUCTURE.md): 디렉토리 구조, 의존 방향, 테마 토큰 규칙, 확정·미확정 항목
- [CLAUDE.md](CLAUDE.md): 작업 범위, git, 코드 규칙
