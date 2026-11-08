# 실제 장소 이벤트와 알림 생성 검증

기본 CI 컬렉션과 분리된 수동 실제 연동 테스트다. `feat/addtest`의 기존 테스트와 독립적으로 새 회원을 만든다.
백엔드의 업무 코드·조회 API·실행 모드를 변경하지 않는다. 일반 PR CI에는 추가하지 않는다.

## 확인하는 흐름

게스트 A·B 로그인 → 위치 동의 → 실제 영상 저장 → SUCCESS 결과·확정 장소 → 지오펜스 반영 → DWELL 이벤트
→ 실제 실행 기록·알림 생성 → 알림 상세 → 다른 회원 조회·열람 차단 → 본인 열람·반복 열람 → 재전송 후 건수 → 저장 삭제·로그아웃.

기본 기대 결과는 **COMPLETED / PROPOSE 및 알림 한 건**이다.
202만 받거나 실행 기록이 없거나, FAILED·TIMED_OUT·NO_ACTION이면 제안 생성 시나리오는 실패한다.
실제 모델은 상황에 따라 정상적으로 NO_ACTION을 선택할 수 있으므로, 이때는 결과와 입력 조건을 검토해야 한다.
그 자체가 곧 업무 로직 버그라는 뜻은 아니다. 임의 재시도로 PROPOSE가 나올 때까지 비용을 쓰지 않는다.

### 발송 성공은 이번 검사 범위가 아니다

새 회원에 FCM 토큰을 등록하지 않아 실제 기기로 발송하지 않는다.
제안·알림은 생성되되, 발송 기록은 **FAILED / NO_FCM_TOKEN**이어야 한다. 이를 발송 성공으로 집계하지 않는다.
알림 상세 조회와 열람 처리는 실제 API로 확인한다. `sentAt`은 null, 열람 후 `openedAt`은 기록돼야 한다.

## DB 확인 도구가 필요한 이유

현재 `POST /api/location-events`는 실행·알림 ID 없이 202를 반환하고, 일반 알림 목록·실행 조회 API도 없다.
`run.mjs`가 임의 포트의 127.0.0.1에 잠깐 확인 도구를 열고 Bruno가 이 도구를 호출한다.
도구는 Docker PostgreSQL에 **BEGIN READ ONLY**로 접속해 다음 조건의 기록만 조회한다.

- 이번 실행에서 새로 생성한 이벤트 UUID
- 이번 게스트 설치 UUID의 SHA-256과 일치하는 회원
- 실제 저장물에서 읽은 장소 UUID

실행 상태·agentVersion·실패 코드·제안 항목과 알림 ID·상태·열람 시각만 반환한다.
DB 행을 만들어 검사에 맞추지 않는다. 서버에 테스트용 API를 추가하지 않는다.
도구는 실행마다 바뀌는 인증값을 요구한다. 인증값은 임시 환경 파일에만 두고 정상 종료 시 파일과 도구를 정리한다.

## 준비

1. 백엔드와 API용 SSH 터널을 실행한다. 기본 주소는 `http://127.0.0.1:18080`이다.
2. 제안을 확인하려면 서버를 실제 LLM 모드로 실행하고 해당 공급자의 인증·모델을 준비한다.
   기본 `recall.agent.mode=stub`은 항상 NO_ACTION을 반환하므로 PROPOSE 검사를 통과할 수 없다.
   실행기 자체는 서버를 재시작하거나 설정을 바꾸지 않는다.
3. 새 분석이 필요한 영상이라면 YouTube·Gemini·장소 검색 등 기존 분석 설정도 필요하다.
   기본 영상은 기존 공개 Shorts `FyXT_7huLds`다. 기존 분석 결과를 재사용할 수 있으며 매번 새 분석을 입증하지 않는다.
4. Node.js 22와 Bruno CLI `4.3.0`을 준비한다. SSH를 쓰면 해당 서버의 호스트 키를 미리 확인해 known_hosts에 등록한다.

```powershell
npm install --global @usebruno/cli@4.3.0
```

## 실행: 우분투 DB를 기존 SSH로 읽기

저장소 루트의 PowerShell에서 설정한다. 주소·키 경로는 본인 환경의 값을 사용하고 커밋하지 않는다.

```powershell
$env:RECALL_SSH_TARGET = 'ubuntu@서버주소'
$env:RECALL_SSH_IDENTITY = 'C:/본인경로/서버접속.key'
node test/bruno-recall/run.mjs
```

SSH 대상이 없으면 로컬 Docker를 사용한다. 기본 컨테이너·DB·사용자는 각각 `remembrall-db`, `remembrall`, `remembrall`이다.
필요하면 `RECALL_DB_CONTAINER`, `RECALL_DB_NAME`, `RECALL_DB_USER`로 바꾼다. 비밀번호는 실행기에 넣지 않는다.
해당 컨테이너 내부의 로컬 PostgreSQL 인증이 가능한 환경이 필요하다.

선택 설정:

- `RECALL_BASE_URL`: 실행할 백엔드 URL
- `RECALL_SHORTS_URL`: 장소가 확인되는 공개 영상의 정규 Shorts URL
- `BRUNO_CLI_JS`: 표준 전역 설치 경로가 아닌 경우 `@usebruno/cli/bin/bru.js`의 절대 경로

Bruno 앱으로 요청을 열어 읽을 수 있지만, 전체 실행은 확인 도구와 임시 환경 파일을 준비하는 `run.mjs`를 사용한다.
로그인 세션은 Bruno 실행 중 변수로만 유지하며 콘솔·환경 파일에 저장하지 않는다.
보고서는 요청·응답 본문 및 헤더를 제외한 `reports/recall.xml`에 기록한다. reports는 Git에서 제외한다.

## NO_ACTION 진단은 별도 시나리오

동의 철회·다른 회원·삭제·오래된 이벤트·쿨다운의 차단 정책은 `node test/bruno-recall/run.mjs --policy`로 별도 실행한다.
자세한 범위는 `test/bruno-recall-policy/README.md`를 참고한다. `--policy`와 `--expect-no-action`은 함께 사용하지 않는다.

```powershell
node test/bruno-recall/run.mjs --expect-no-action
```

이 명령은 실행 한 건·COMPLETED/NO_ACTION·알림 0건 및 재전송 후 건수 유지를 확인한다.
알림 상세·열람 요청은 건너뛴다. **이 모드의 통과는 제안·알림 생성 성공이 아니다.** 기본 모드가 자동으로 이 모드로 내려가지 않는다.
진단 보고서는 `reports/recall-no-action.xml`에 저장하여 기본 제안 시나리오의 `reports/recall.xml`과 구분한다.

## 범위와 정리

- 이벤트 재전송 때 중복과 회원 쿨다운이 함께 적용되므로 중복 정책만 단독 검증했다고 판단하지 않는다.
- 실제 기기 위치 감지, FCM 전달·수신, 분석 결과의 의미적 품질은 별도로 확인한다.
- 계정·공용 콘텐츠는 남을 수 있다. 개인 저장물과 세션은 마지막 요청에서 정리한다.
- 강제 종료·네트워크 단절이면 정리 요청이 실행되지 않을 수 있다. 로그의 실행 ID로 해당 테스트 데이터를 확인한다.
- DB 접속 자체가 안 되면 실행기는 회원 생성 전 실패한다. 관찰 오류를 빈 기록으로 바꾸거나 성공으로 처리하지 않는다.

도구 자체의 인증·입력 검증 테스트:

```powershell
node --test test/bruno-recall/run.test.mjs
```
