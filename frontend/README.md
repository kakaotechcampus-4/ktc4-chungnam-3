# REMEMBRALL 프론트엔드

저장만 해두고 잊어버린 콘텐츠를, 에이전트가 꺼내주는 앱

저장해둔 장소 영상을 근처에 갔을 때 다시 꺼내주는 앱의 React Native(Expo) 클라이언트다.
지금은 UI 단계라 화면이 목 데이터로 렌더된다.

## 실행

```bash
npm install
npx expo start
```

터미널에 뜬 QR 코드를 폰의 Expo Go 로 스캔한다. 에뮬레이터가 있으면 `a` 를 누른다.

## 딥링크로 화면 열기

저장 결과 모달(02~05)처럼 앱 안에서 바로 갈 수 없는 화면은 딥링크로 연다.

1. Expo Go 홈 → Enter URL manually → `exp://<Metro 주소>/--/save-result/failed`
   (`<Metro 주소>` 는 `npx expo start` 가 보여주는 `192.168.x.x:8081` 같은 값)
2. adb 가 있으면:
    ```bash
    adb shell am start -W -a android.intent.action.VIEW -d "exp://<Metro 주소>/--/save-result/failed" host.exp.exponent
    ```

경로는 `save-result/needs-confirmation`, `save-result/analyzing`, `save-result/no-place`, `save-result/failed`,
`detail/sungsimdang`, `nearby`, `archive` 가 있다.

빈 상태(06 · 07b)는 `src/shared/api/mock/index.ts` 의 `MOCK_SCENARIO` 플래그를 `true` 로 바꾸고 reload 해서 본다.

## 문서

- [STRUCTURE.md](STRUCTURE.md): 디렉토리 구조, 의존 방향, 테마 토큰 규칙, 확정·미확정 항목
- [CLAUDE.md](CLAUDE.md): 작업 범위, git, 코드 규칙
