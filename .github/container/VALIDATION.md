# 검증 기록 — 2026-11-09

작업 브랜치: feat/ghcr-deploy. 기준 develop: bf1b359. 커밋·푸시·GitHub 게시 작업은 수행하지 않았다.

## non-main 수동 GHCR 검사 추가

- 기본 브랜치 develop에 이미 등록된 Backend release workflow_dispatch를 확인하고 해당 파일의 non-main 경로를 확장했다.
  신규 파일 등록을 위해 main에 머지할 필요 없이 현재 브랜치를 선택하도록 구성했다.
- main 경로의 실제 외부 gate와 운영 게시를 명시적으로 main에 제한했다.
- non-main 수동 경로는 기본 CI 후 별도 패키지·고유 테스트 태그로 빌드/기동 검사/push/digest pull/재기동 검사를 수행하도록 구성했다.
  외부 검사 receipt와 라벨은 not-run으로 기록하고 성공한 실제 외부 API 검사로 표시하지 않는다.
- Ubuntu 임시 폴더에서 실제 publish-test.sh를 가짜 Docker·기동 명령과 실행한 검사 9개 통과.
  main·push·tag·잘못된 SHA 거부, 테스트 저장소에만 게시, 실패 시 게시 차단, push/pull/재기동 실패 시 성공 결과 미생성을 확인했다.
- 기존 배포기 21개와 합쳐 Python 검사 총 30개 통과. Bash 문법과 actionlint 검사도 통과했다.
- 이번 검증은 실제 GHCR 로그인·업로드·다운로드나 새 GitHub Actions 실행 결과가 아니다. 운영 서버는 변경하지 않았다.
- 커밋·푸시는 사용자가 수행하며, 이후 Actions → Backend release → Run workflow에서 feat/ghcr-deploy를 선택해 실제 검사를 실행한다.

## 실제 실행한 검사

- Ubuntu 22.04 ARM64 / Docker 29 / Compose 2.40.3 / Python 3.10에서 pull 배포기 단위 검사 21개 통과.
  모의 System으로 비활성 상태, 아키텍처·출처·모델·digest 정책, 중복·역순 배포 차단,
  작업 중 배포 연기, 백업 실패, 점검 조건 누락, 새 버전 실패, 외부 health 실패,
  중단 복구, 상태 저장 실패, 실패 버전 재배포 차단, 이전 버전 복구를 검사했다.
- 로컬 Node에서 외부 API 검사 도구의 보호·관찰·URL 정규화 검사 8개 통과.
- Compose config 검증, Bash 문법 검사, actionlint 1.7.7로 CI/release YAML 검증 통과.
- 현재 서버에서 JUnit 735개를 통과한 bf1b359 JAR을 사용해 실제 ARM64 이미지를 빌드했다.
  이미지 이름은 로컬 remembrall-ghcr-smoke:20261109이며 GHCR에 올리지 않았다.
- 별도 Docker 네트워크·빈 pgvector DB·읽기 전용 컨테이너·non-root UID로 smoke.sh 실행 통과.
  DB 마이그레이션 후 health UP 및 인증 없는 동의 API의 401을 확인했다. 키는 CI용 더미만 사용했다.
- 원격 테스트 이미지·컨테이너·네트워크·작업 폴더를 정리했다.
  로그는 Git 제외 폴더 .github/container/reports/에 보관했다.

## 서버에 실제로 준비한 것

- Docker Compose CLI 플러그인만 추가 설치했다. Docker daemon을 재시작하지 않았다.
- root 소유 pull.py/Compose/config와 systemd service/timer를 설치했다.
- config.json은 root:600, enabled=false다. timer는 disabled/inactive다.
- 설치한 oneshot service를 실행하여 Result=success / ExecMainStatus=0을 확인했다.
  비활성 분기에서 이미지 pull이나 서비스 전환 없이 종료했다. status 출력은 enabled=false다.
- 기존 운영 remembrall PID 587380 유지, active 및 외부 HTTPS health UP 확인.
- 기존 DB 컨테이너·Nginx·키 파일은 변경하지 않았다. Nginx 점검 include 파일은 준비했지만 활성 location에는 연결하지 않았다.

## 아직 검증하지 않은 것

- 새 GitHub Actions 워크플로 실행, 실제 키 외부 API gate 및 GHCR 로그인·push·pull.
- GHCR 패키지 공개 설정과 deploy-main 채널 게시.
- 운영 Nginx 점검 조건 연결, 실제 첫 컨테이너 전환·복구·재부팅 후 기동.
- 이미지 게시 성공을 서버 배포 성공으로 회신하는 기능, 실패 알림, 부하 시험.

모의 상태 전이 테스트를 운영 장애 복구 성공으로, 더미 키 이미지 기동을 실제 외부 API 연동 성공으로 해석하지 않는다.
