# GHCR에서 서버가 가져오는 배포

SSH 키를 GitHub에 등록하지 않는다. GitHub-hosted runner에서 검사·이미지 게시를 수행하고,
서버의 root 소유 배포기가 고정된 GHCR 저장소의 배포 채널을 확인한다. 서버는 GitHub runner가 아니다.
현재 방식은 짧은 점검 시간에 앱을 교체한다. 무중단·다중 서버 구성은 제공하지 않는다.

## 흐름과 두 스위치

- develop PR/push: 기존 CI 5개. delivery tools 작업에서 pull 배포기 검증도 수행한다.
- main push 또는 main 수동 실행: CI → 실제 외부 API gate → 같은 JAR로 ARM64 이미지 생성 → 일회용 DB로 이미지 기동 검사 → GHCR 게시.
- `sha-<commit>` 이미지가 게시돼도 운영 서버에 배포됐다는 뜻은 아니다.
- 저장소 Variable `BACKEND_DEPLOY_ENABLED=true`인 경우에만 `deploy-main` 태그를 갱신한다.
  수동 실행은 `deploy=true`도 필요하다. 이때 더 최신 main이 있으면 채널 승격을 생략한다.
- 서버 `/etc/remembrall-container/config.json`의 `enabled=true`와 timer 활성화까지 해야 주기 배포가 동작한다.
- 두 스위치는 독립적이다. GitHub 쪽을 false로 바꾸면 앞으로의 채널 갱신만 막는다.
  이미 게시된 배포 대상을 적용하지 않으려면 서버 쪽도 false로 바꾸고 timer를 멈춘다.

이미지는 `ghcr.io/kakaotechcampus-4/remembrall-backend`에 게시한다. Actions의 GITHUB_TOKEN에
packages:write를 주고 원본 저장소 라벨을 붙인다. 별도 PAT·SSH 키는 Actions에 필요하지 않다.
production Environment는 main만 허용하고 main/workflow 변경에 리뷰를 적용한다.

기존 external-api Environment는 그대로 사용한다. Secrets는 EXTERNAL_YOUTUBE_API_KEY,
EXTERNAL_GEMINI_API_KEY, EXTERNAL_LOCATIONIQ_API_KEY, EXTERNAL_KAKAO_API_KEY,
EXTERNAL_DATAPORTAL_API_KEY, EXTERNAL_VWORLD_API_KEY 및 선택 EXTERNAL_KMA_API_KEY다.
Variables는 EXTERNAL_RECALL_MODEL과 EXTERNAL_SHORTS_URL이다. 모델은 서버 config.json의 model과 같아야 한다.
이미지 게시 단계는 production Environment에 속하지만 추가 SSH Secret을 요구하지 않는다.

첫 게시 후 조직 Packages에서 패키지를 public으로 설정해야 서버가 익명 다운로드할 수 있다.
공개 패키지는 누구나 JAR를 내려받을 수 있다. 비공개를 원하면 서버의 root Docker 계정에만
read:packages 권한 토큰으로 로그인한다. 토큰은 소스·config.json·이미지에 넣지 않는다.
패키지 게시 권한은 운영 코드 실행 권한으로 이어지므로 신뢰할 사람에게만 준다.

## 이미지와 비밀 설정

Docker 빌드 context는 검증된 artifact를 내려받은 release 폴더뿐이다. Dockerfile은 JAR와
검증 요약 EXTERNAL_RESULT.json만 복사한다. 서버의 secrets.properties·Firebase JSON은 포함하지 않는다.
Java 21 ARM64 JRE에서 non-root로 실행한다. 서버에서는 해당 파일 소유자의 UID/GID로 실행하고
Compose secrets로 두 파일을 읽기 전용 연결한다. 비밀 값은 config.json이나 runtime.env에 넣지 않는다.

업로드한 이미지와 서버는 ARM64다. 개발 PC에서 직접 실행하려면 ARM64 에뮬레이션이 필요할 수 있다.
이미지 base 태그의 보안 업데이트는 새 이미지 빌드 때 반영된다. 서버가 실행 중인 이미지를 몰래 바꾸지 않는다.

## 서버 최초 준비

운영 서비스는 기존 systemd `remembrall`, DB는 기존 `remembrall-db` 그대로 둔 채 시작한다.
Docker Compose v2와 Python 3.10 이상이 필요하다. 검토한 config.example.json을 서버 밖 비밀 없는 설정 파일로 복사하고
enabled=false, UID/GID, 실제 모델, 키 파일 경로를 확인한다.

```bash
sudo bash .github/container/bootstrap.sh /절대경로/config.json
```

이 명령은 다음만 설치한다. timer를 enable/start하지 않으며 DB·Nginx·앱을 변경하지 않는다.

- `/usr/local/lib/remembrall-container/`: root 소유 pull.py와 고정 Compose 파일
- `/etc/remembrall-container/config.json`: root:600, 비밀 값 없이 경로·모델·설정만 보관
- `/etc/remembrall-container/maintenance.conf`: 최초 활성화 전 Nginx에 연결할 점검 조건
- `/var/lib/remembrall-container/`: root:700, 현재/이전 버전·실패 버전·백업·배포 잠금
- `remembrall-pull.service` / `remembrall-pull.timer`

Nginx의 기존 HTTPS `location /` 안에 아래 한 줄을 추가한다. 기존 proxy_pass·인증서·헤더·timeout은 유지한다.

```nginx
include /etc/remembrall-container/maintenance.conf;
```

`sudo nginx -t` 후 reload하고 평소 HTTPS 요청이 정상인지 확인한다. 배포기는 점검 파일을 만든 후
외부 health URL이 503을 반환하는 것까지 확인해야 기존 앱을 종료한다. include가 없으면 배포를 중단한다.
Nginx worker가 `/run/remembrall-container/maintenance`를 볼 수 있어야 한다.

첫 이미지를 게시하고 공개 여부를 확인한 후 수동으로 첫 전환한다.

```bash
sudo python3 /usr/local/lib/remembrall-container/pull.py status
sudo python3 /usr/local/lib/remembrall-container/pull.py deploy
```

manual deploy는 enabled=false여도 운영자가 명시적으로 수행할 수 있다. CI에서 호출하지 않는다.
로그인·저장·분석·알림·재부팅 후 자동 기동을 직접 확인한 다음 서버 config의 enabled를 true로 바꾸고 실행한다.

```bash
sudo systemctl enable --now remembrall-pull.timer
```

## 전환과 복구

1. deploy-main을 pull한 뒤 ARM64, source, 커밋 SHA, 검사 통과 라벨, 검증 모델, JAR 해시 형식을 확인한다.
2. 태그 대신 고정된 repository@sha256 digest로 실행한다. 같은 버전·실패한 버전·낮은 실행 순번은 반복 배포하지 않는다.
3. 진행 중인 분석·알림이 있으면 요청을 막지 않고 이번 배포를 미룬다. 비어 있으면 기존 DB에 전용 Docker 네트워크를 연결한다.
   호스트 localhost:5432 바인딩과 데이터 볼륨은 유지한다.
4. Nginx 점검 조건으로 새 요청을 503 처리한 뒤 DB 전체 분석·알림 실행이 연속 3회 비었는지 확인한다.
   약 90초 내 비워지지 않으면 점검을 해제하고 기존 앱을 유지한다. 오래 멈춘 작업은 운영자가 원인을 확인한다.
5. pg_dump 백업을 만든다. 기존 앱을 중지하기 전 디스크에 전환 상태를 기록한다.
6. 기존 앱을 종료하고 새 컨테이너를 실행한다. localhost:8080만 공개하고 2GB 메모리/1.5 CPU, 로그 순환을 적용한다.
7. 내부 health UP·인증 없는 API의 401 확인 후 점검을 해제하고 외부 HTTPS health를 확인한다.
8. 성공하면 현재 digest를 기록하고 기존 JAR systemd 서비스를 disable해 재부팅 시 포트 충돌을 막는다.
   Docker restart policy가 컨테이너를 다시 기동한다. Nginx와 Docker의 부팅 활성화는 최초 전환 때 확인한다.
9. 실패하면 이전 digest 또는 첫 전환 전의 systemd 앱을 복구한다. 정상 복구 전에는 점검을 유지한다.
   프로세스 중단 시 다음 호출은 저장된 전환 상태를 보고 복구한다. 정상 구동 후 앱 장애는 restart policy 대상으로,
   배포 직후 검사 이후까지 자동 이전 버전 복구를 보장하는 감시 시스템은 아니다.

```bash
sudo python3 /usr/local/lib/remembrall-container/pull.py rollback
sudo python3 /usr/local/lib/remembrall-container/pull.py deploy --retry  # 원인 해결 후 실패한 같은 digest 재시도
sudo journalctl -u remembrall-pull.service -f
docker logs --tail 100 -f remembrall-backend
```

DB는 자동 복원하지 않는다. Flyway 변경은 이전 앱과 호환돼야 한다. 비호환 마이그레이션은 별도 계획이 필요하다.
백업과 이전 이미지는 자동 삭제하지 않는다. 디스크를 확인하며 현재/이전 digest를 보존한 상태로 정리한다.
롤백 후 같은 실패 이미지가 다음 분에 다시 적용되지 않도록 실패 digest를 기록한다.
서버 실행 결과는 서버 journal/state.json에서 확인한다. GitHub의 게시 성공과 서버 배포 성공은 별개이며,
GitHub 상태 회신이나 실패 알림은 아직 연결하지 않았다.

## 검증 범위

```bash
python3 -m unittest discover -s .github/container -p 'test_*.py' -v
node --test test/external/run.test.mjs
bash .github/container/smoke.sh 로컬-ARM64-이미지
```

Python 상태 전이 검사는 Linux 환경에서 가짜 System을 사용한다. 실제 Docker·Nginx 장애 복구 결과가 아니다.
smoke는 실제 ARM64 이미지와 일회용 DB를 사용하지만 외부 키는 더미이며 실제 FCM 수신을 검증하지 않는다.
실제 API gate, GHCR 게시·다운로드, 운영 첫 전환·복구·재부팅 결과는 별도 실행 기록으로 남긴다.
