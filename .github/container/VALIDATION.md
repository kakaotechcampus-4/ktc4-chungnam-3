# GHCR 배포 검증 기록 — 2026-11-10

작업 브랜치: `feat/ghcr-deploy`. 이번 변경은 커밋·푸시하지 않았다. 운영 컨테이너 전환과 자동 배포 활성화도 수행하지 않았다.

## 앞서 확인한 실제 GitHub 실행과 인증

- [Backend release 실행 42805551684](https://github.com/kakaotechcampus-4/ktc4-chungnam-3/actions/runs/42805551684): 커밋 `578405b01ce71ffd8211f90114bd79e5ccecc142`, non-main 수동 실행, deploy=false. 기본 CI 5개와 `Publish and pull test ARM64 image`가 성공했다.
- 테스트 이미지 빌드 → 임시 DB 기동 검사 → 비공개 GHCR 게시 → digest pull → 재기동 검사가 실제 실행됐다. 이 실행은 아래의 새 로컬 수정까지 검사한 결과가 아니다.
- 서버 root의 Docker 로그인으로 다음 비공개 이미지를 실제 다운로드했다. 빈 인증 설정을 사용한 익명 조회는 거절됐다. 조직의 공개 설정은 변경하지 않았다.

```text
ghcr.io/kakaotechcampus-4/remembrall-backend-test@sha256:adf8ebb08a6faab372aa0e561a663c2d8529e23beb1b919480afc1d43dc77f80
```

이 이미지의 external-gate는 `not-run`, test-only는 `true`다. 실제 외부 API 검사나 운영 릴리스 통과로 해석하지 않는다. 운영 패키지 `remembrall-backend`의 생성·다운로드 권한은 별도 확인이 필요하다.

## 이번 수정과 실제 검사

- 서버의 pull 명령에 `/root/.docker` 인증 설정을 명시했다. 인증 실패 시 비밀 출력 없이 원인을 구분하고 기존 앱을 유지한다.
- 테스트 이미지 거부 조건, 포트·Compose 이름 검증, 작업 진행 중 롤백 연기, 상태 파일 교체 후 디렉터리 동기화를 보완했다.
- 실제 Compose 기동 중 확장자 없는 secret 경로 때문에 Spring이 설정 파일을 못 찾는 문제를 발견했다. 마운트 대상과 실행 인자를 `/run/secrets/application.properties`로 맞춘 후 다시 검증했다.
- Ubuntu 22.04 ARM64에서 Python 검사 **35개 통과**: 배포기 26개, 브랜치 게시 도구 9개. 모의 명령·System으로 정책, 비활성 상태, 오류 출력 보호, 전환·복구를 검사한다.
- 로컬 Node 검사 **8개 통과**. 최초 제한된 실행은 하위 프로세스 생성 권한 오류로 중단됐고, 실행 권한이 있는 환경에서 통과했다.
- Bash 문법, Compose config, `git diff --check` 통과.

## 실제 임시 컨테이너 검사

위 비공개 테스트 이미지로 `integration.py`를 실행했다. 새 pgvector DB, 전용 네트워크, 임의 localhost 포트 `36205`, 더미 API 키를 사용했다. 운영 DB·키·systemd·Nginx는 사용하지 않았다.

최종 실행 결과는 `passed`다.

- 앱 기동 후 health UP, 인증 없는 동의 API 401.
- 게스트 로그인과 위치 동의를 저장한 뒤 실제 Compose stop/up 실행, 프로세스 PID 변경 확인.
- 같은 세션으로 조회하여 재시작 전 DB에서 읽은 동의 상태·시각이 유지되는지 확인.
- 다음 시작 명령을 `/bin/false`로 바꿔 실제 컨테이너 시작 실패를 유도하고 이전 정상 설정으로 자동 복구 확인.
- 복구 후 health·세션·동의 재확인, 점검용 임시 플래그 해제 확인.
- 비어 있지 않은 pg_dump 백업 2개 생성 확인.
- 실행 종료 시 임시 앱·DB·DB 볼륨·네트워크·설정·백업 정리.

초기 실행은 설정 파일 경로 문제로 실패했다. 경로 수정 뒤 실행은 DB 시간 정밀도 차이를 잘못 비교해 실패했고, DB 조회 결과를 기준으로 전후 비교하도록 고친 뒤 최종 통과했다.

이 검사는 **동일한 이미지의 실행·복구**를 확인했다. 서로 다른 릴리스 간 마이그레이션 호환성, 운영 HTTPS 점검 처리, 기존 systemd JAR에서 컨테이너로의 첫 전환, 실제 FCM 수신은 검증하지 않았다.

## 서버 정리와 현재 상태

- 비활성 서버 소스 빌드 도구를 백업 후 제거했다: 설치 코드·config·state·전용 빌드 디렉터리·service/timer. 백업은 서버 `/home/ubuntu/apps/remembrall-updates/source-tools-before-ghcr-20261110.tar.gz`, root:600이다.
- 로컬 소스 빌드 작업도 Windows 임시 폴더의 `remembrall-ghcr-return-20261110`에 백업 후 작업 트리 밖으로 이동했다. 사용하지 않는 SSH 업로드 배포 파일 삭제는 유지한다. Gradle 다운로드 시간 제한·재시도 보완도 유지한다.
- GHCR pull.py·Compose·config·service/timer를 설치했다. config는 root:600, **enabled=false**다.
- timer는 **disabled/inactive**다. oneshot 실행은 비활성 분기에서 Result=success로 종료했으며 배포하지 않았다.
- root Docker 인증은 보존했다. 인증 파일 내용은 읽거나 공유하지 않았다.
- 운영 `remembrall`은 PID **587380**을 유지했고 health UP이다. 기존 JAR 해시도 작업 전후 동일하다.
- 운영 DB·Nginx·TLS·키 파일·기존 JAR는 유지했다. 점검 include는 준비만 했고 Nginx에 연결하지 않았다.

## 아직 남은 운영 전환

1. 사용자가 이번 변경을 커밋·푸시한 뒤 해당 변경의 GitHub CI를 확인한다.
2. main 릴리스의 실제 외부 API gate와 운영 ARM64 이미지 게시·deploy-main 승격을 확인한다. 운영 패키지에 대해 root 인증 pull이 되는지도 별도로 확인한다.
3. 첫 전환 전 Nginx 점검 include 연결·검사, 운영 DB 백업·마이그레이션 호환성 확인.
4. 수동 첫 컨테이너 전환 후 앱 기능·복구·재부팅 기동 확인.
5. 확인 후에만 서버 enabled=true와 timer 활성화. GitHub 채널 승격 스위치도 의도에 맞게 설정한다.

이미지 게시 결과를 서버 배포 성공으로 회신하거나 배포 실패를 알리는 기능은 아직 없다.
