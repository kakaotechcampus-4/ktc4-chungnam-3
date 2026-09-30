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

- EAS 빌드: 환경 변수로 등록한다.
    ```bash
    npx eas-cli env:create --environment development --name GOOGLE_MAPS_ANDROID_API_KEY --value <키> --visibility secret
    ```
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

## 문서

- [STRUCTURE.md](STRUCTURE.md): 디렉토리 구조, 의존 방향, 테마 토큰 규칙, 확정·미확정 항목
- [CLAUDE.md](CLAUDE.md): 작업 범위, git, 코드 규칙
