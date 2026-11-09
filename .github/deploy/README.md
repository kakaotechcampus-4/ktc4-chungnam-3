# 이전 SSH 배포 방식 — 현재 워크플로에서 사용하지 않음

새 배포 경로는 [GHCR pull 배포](../container/README.md)다. 아래 SSH 전송·블루/그린 스크립트는 이전 구현 참고용이며
`backend-release.yml`에서 호출하지 않는다. 새 서버에는 이 문서의 bootstrap을 실행하지 않는다.

## 이전 구현 기록

develop 대상 PR 및 develop push에서는 실제 키 없이 기본 CI만 실행한다. 통합 시점에는 배포하지 않는다.
`backend-release.yml`: main push → 해당 커밋의 Backend CI 재검증·JAR 생성 → 실제 외부 API gate → 동일 JAR 배포.
develop→main PR 생성만으로 배포하지 않는다. main에 머지된 이후 실행한다.
수동 실행은 main에서만 허용하며 `deploy=false`가 기본이다. 실제 키 호출에는 비용이 발생한다.

## 현재 상태

코드와 오프라인 검증만 준비된 상태다. 운영 서버의 기존 systemd/Nginx 설정은 변경하지 않았다.
실제 외부 API gate, GitHub Secrets 등록, 실제 GitHub release 실행과 운영 배포는 아직 검증하지 않았다.
키가 없으면 external gate가 사전 검사에서 실패하며, 더미 키로 통과시키지 않는다.
자동 배포는 저장소 Variable `BACKEND_DEPLOY_ENABLED=true` 설정 전까지 건너뛴다.

## 1. external-api Environment

Environment Secret 이름:

- `EXTERNAL_YOUTUBE_API_KEY`
- `EXTERNAL_GEMINI_API_KEY`
- `EXTERNAL_LOCATIONIQ_API_KEY`
- `EXTERNAL_KAKAO_API_KEY`
- `EXTERNAL_DATAPORTAL_API_KEY`
- `EXTERNAL_VWORLD_API_KEY`
- `EXTERNAL_KMA_API_KEY`: 날씨 도구를 확인할 경우 선택 입력

Environment Variables:

- `EXTERNAL_RECALL_MODEL`: 팀 프로젝트에서 실제 호출 가능한 것으로 확인한 Google GenAI 모델 ID
- `EXTERNAL_SHORTS_URL`: `https://www.youtube.com/shorts/영상ID` 형태. 정상 분석·확정 장소가 나오는 공개 테스트 영상

Secrets를 브라우저의 GitHub 설정에서 입력한다. 키를 채팅, 소스, workflow 입력란이나 보고서에 넣지 않는다.
처음에는 수동 `deploy=false`로 실행하여 실제 키·모델·영상 조합을 검증한다.
external-api와 production Environment의 허용 브랜치는 main으로 제한한다. 배포용 SSH 인증과 실제 API 키는 다른 Environment에 둔다.

## 2. 실제 외부 gate

`test/external/run.mjs`가 새 localhost DB와 일반 애플리케이션 JAR를 실행한다. 테스트 하네스·합성 데이터·운영 DB를 사용하지 않는다.
영상 1개 분석, 장소·카카오 검증·768차원 임베딩 저장, 실제 LLM 모델 호출 trace와 결과 계약을 검사한다.
LLM의 정상 NO_ACTION은 허용한다. FAILED/TIMED_OUT, stub, 성공한 모델 호출 기록 없음, 부정확한 제안 연결은 실패다.
PROPOSE이면 알림 생성을 검사하되 실제 기기 토큰은 등록하지 않아 발송은 NO_FCM_TOKEN을 기대한다.
추천의 의미적 품질, 모든 제공사 API 경로, 실제 FCM 수신을 검증했다는 뜻은 아니다.

분석 1회·위치 이벤트 1회이며 시나리오 자동 재시도는 없다. 모델 도구 호출은 3회로 제한한다.
SDK 자체 재시도와 도구별 추가 호출은 발생할 수 있으므로 시간 제한을 정확한 과금 상한으로 해석하지 않는다.
YouTube·Gemini·카카오 등은 실제 제공사 클라이언트를 사용한다. 실패 시 다음 배포를 막고 기존 서버는 유지한다.
원본 서버/CLI 로그와 키 파일은 임시 폴더에서만 사용 후 삭제한다. 보고서에는 본문·헤더를 제외한 JUnit XML과 단계·결과 요약만 보관한다.

## 3. production Environment

Secrets:

- `DEPLOY_HOST`: 서버 호스트/IP
- `DEPLOY_USER`: 현재 템플릿은 ubuntu 기준
- `DEPLOY_SSH_KEY`: 배포용 SSH 개인키. 기존 개인키 공유 대신 별도 배포 키 권장
- `DEPLOY_KNOWN_HOSTS`: 관리자가 서버 fingerprint를 확인한 known_hosts 줄

Variable `DEPLOY_ROOT=/home/ubuntu/apps/remembrall-deploy`.
배포 활성화 플래그 `BACKEND_DEPLOY_ENABLED`는 **저장소 수준 Variable**로 설정한다.
현재 등록된 키를 소스에서 서버나 GitHub로 자동 복사하는 동작은 없다.
서버를 SSH/SCP로 접근 가능하게 하는 네트워크 정책도 별도로 확인한다.
GitHub-hosted runner의 IP는 고정이 아니므로 22번 포트 정책에 맞는 접속 방식/runner를 선택해야 한다.

## 4. 서버 최초 준비 — 운영자 명시 실행

이 절차는 CI가 자동으로 실행하지 않는다. 현재 단일 서비스의 블루 포트는 8080, 새 그린 포트는 8081이다.
템플릿은 현재 ubuntu 사용자와 작업 경로 기준이며, 사용자·경로가 다르면 unit도 먼저 수정한다.

1. `deploy.conf.example`을 서버의 별도 파일로 복사하고 경로·DB·HTTPS 주소를 확인한다.
2. 검토한 소스에서 아래를 실행한다.

```bash
sudo bash .github/deploy/bootstrap.sh /절대경로/deploy.conf
```

3. 현재 HTTPS server block의 `location /`에서 **기존 proxy_pass 한 줄만** 다음으로 교체한다.
   기존 TLS·proxy_set_header·timeout 설정은 보존하고 proxy_pass를 중복 선언하지 않는다.

```nginx
include /etc/nginx/remembrall/active-location.conf;
```

4. `sudo nginx -t` 후 `sudo systemctl reload nginx`. 헬스 응답의 `X-Remembrall-Release: legacy`를 확인한다.
5. 배포 계정이 incoming 폴더에 업로드하고 설치된 `/usr/local/sbin/remembrall-deploy`를 sudo로 실행할 권한을 준비한다.
   일반 사용자에게 설정 파일·설치 스크립트 수정 권한을 주지 않는다. `/etc/remembrall/deploy.conf`는 root:600으로 관리한다.
6. BACKEND_DEPLOY_ENABLED를 끈 상태에서 실제 external gate를 통과시킨다.
7. 아래 마이그레이션/드레인 조건을 검토한 뒤 플래그를 켜고 실제 배포를 검증한다.

bootstrap은 두 슬롯의 파일과 unit만 준비한다. 기존 앱을 재시작하거나 새 슬롯을 enable하지 않는다.
nginx 설정을 자동 문자열 치환하지 않으며 HTTPS 설정은 운영자가 확인한다.

## 5. 배포와 롤백

검증된 artifact를 SHA-256과 함께 incoming에 업로드한다. 서버는 checksum 검증 후 root-owned releases에 보관한다.
같은 SHA의 기존 release를 다른 바이너리로 덮어쓰지 않는다. GitHub의 최신 main과 다른 오래된 배포도 거절한다.
artifact의 `EXTERNAL_RESULT.json`에서 같은 커밋·성공한 모델 호출·768차원 벡터 검증을 확인하고,
그때 검증한 모델이 서버 deploy.conf의 `RECALL_MODEL`과 다르면 배포를 거절한다.
unit은 해당 모델·Google GenAI·LLM 모드를 명시한다. 운영 키에도 이 모델의 사용 권한이 있어야 한다.

1. 비활성 포트 점유 여부 확인. 이전 작업이 남아 있으면 강제 종료하지 않고 배포 중단.
2. 운영 DB 백업. 실패하면 새 버전 시작 전 중단.
3. 새 슬롯 기동, 내부 UP 및 세션 없는 보호 API의 401 확인.
4. Nginx include를 교체하고 nginx -t/reload.
5. 실제 HTTPS에서 UP과 새 SHA 헤더 확인. 실패하면 기존 Nginx 설정으로 복구하고 새 슬롯 중지.
6. 성공한 새 슬롯을 enable하고 active-slot을 기록.
7. DB 전체의 ANALYZING 및 PENDING/RUNNING recall 실행이 연속 3회 0일 때 이전 서비스를 stop/disable.

드레인은 보수적인 DB 전체 검사다. 약 10분 안에 비워지지 않으면 구버전을 **계속 실행**시켜 작업을 보존한다.
이 경우 다음 배포는 비활성 포트 점유로 중단되므로, 운영자가 작업 종료를 확인하고 이전 unit을 stop/disable해야 한다.
완료된 신규 요청이 계속 생기면 드레인이 지연될 수 있다. durable queue나 인스턴스별 작업 계측을 대체하는 설계는 아니다.

**Flyway는 새 슬롯 시작 때 운영 DB에 적용된다. Nginx 롤백은 DB를 되돌리지 않는다.**
구버전과 공존 가능한 추가 위주 마이그레이션만 이 경로로 배포한다.
컬럼 삭제/이름 변경 등 비호환 변경은 별도 배포 계획이 필요하다. DB 자동 덮어쓰기 복구는 하지 않는다.
두 인스턴스가 겹쳐 실행되는 동안 주기 작업의 중복 실행 정책도 운영 전 검토한다.
백업과 release 보관 정책·디스크 여유도 운영자가 관리한다.
한 VM 구성으로 VM 자체 장애까지 해결되는 것은 아니다.

## 6. 검증 명령

```bash
node --test test/external/run.test.mjs
docker run --rm --network none --entrypoint bash -v "$PWD:/workspace:ro" \
  pgvector/pgvector:pg16 /workspace/.github/deploy/test.sh
```

7개 Node 보호/관찰 테스트와 9개 모의 배포/롤백 시나리오를 실행한다.
모의 테스트는 systemctl/nginx/docker/curl을 가짜 명령으로 바꾼 별도 컨테이너에서만 동작한다.
실제 Nginx·systemd·SSH 전송·외부 API·운영 DB 마이그레이션의 통과 결과와 구분한다.
