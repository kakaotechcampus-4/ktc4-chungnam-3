# 검증 기록 — 2026-11-08

- 기준 소스: `origin/develop`의 `b3bd946`에 이번 작업 브랜치 변경을 적용한 작업 트리.
- 환경: Ubuntu 22.04 ARM64, Java 21, Node 22, Bruno CLI 4.3.0, 임시 PostgreSQL/pgvector 16.
- 운영과 분리된 작업 폴더에서 실행. 운영 서비스/JAR/설정/DB는 수정하지 않음.

## Bruno

저장소 루트에서 `bash test/harness/run.sh` 실행. 같은 임시 서버·DB에서 두 차례 연속 실행했다.

| 컬렉션 | 1차 | 2차 | 실패/오류 |
| --- | ---: | ---: | ---: |
| bruno-place-list | 26 | 26 | 0 |
| bruno-auth-device | 30 | 30 | 0 |
| bruno-analysis-lifecycle | 68 | 68 | 0 |
| bruno-recall-retry | 48 | 48 | 0 |
| 합계 | 172 | 172 | 0 |

숫자는 준비·로그인·관찰·정리를 포함한 HTTP 요청/테스트 블록 수다. 새 기능 시나리오 수와 다르다.
FCM 토큰은 현재 DeviceService 계약에 따라 새 기기에 등록하면 이전 기기에서 제거되고 새 기기로 이전되는지 검증했다.
분석 timeout·외부 오류는 SDK 경계의 대역 결과를 사용했다. 실제 HTTP timeout이나 실제 푸시 수신 검증 결과로 해석하지 않는다.

## 관련 JUnit

추가 설정은 `.github/ci/application-ci.properties`를 사용했다.

```bash
cd backend/Remembrall
bash ./gradlew test \
  --tests '*BrunoHarness*Test' --tests '*GlobalExceptionHandlerTest' \
  --tests '*PersonalSaveIntegrationTest' --tests '*ConfirmAnswerServiceTest' \
  --tests '*LocationEventIntegrationTest' --tests '*NotificationIntegrationTest' \
  bootJar --no-daemon --console=plain
```

| 테스트 | 실행 | 실패/오류 |
| --- | ---: | ---: |
| BrunoHarnessConfigurationTest | 4 | 0 |
| BrunoHarnessTest | 4 | 0 |
| GlobalExceptionHandlerTest | 5 | 0 |
| PersonalSaveIntegrationTest | 44 | 0 |
| ConfirmAnswerServiceTest | 7 | 0 |
| LocationEventIntegrationTest | 60 | 0 |
| NotificationIntegrationTest | 83 | 0 |
| 합계 | 207 | 0 |

## 기타 확인

- 운영용 `bootJar`에서 테스트 하네스 패키지 포함 건수 0 확인.
- Bruno 요청 파일 172개의 파싱, JavaScript 문법, Runner 분기 대상 검사 통과.
- Bash 문법 검사, actionlint, git diff --check 통과.
- XML·실행 로그는 Git 제외 폴더인 `test/harness/reports/`에 보관.
- 검증 후 하네스 프로세스 0개·테스트 컨테이너 0개 확인 후 원격 임시 폴더 삭제.
- 운영 서비스 PID와 시작 시각 유지, 헬스 체크 HTTP 200 / UP 확인.

새 GitHub Actions job은 구성과 문법을 검증한 상태이며 실제 GitHub 실행은 아직 하지 않았다.
전체 JUnit 실행, 기존 평가 기준 문서 문제 해결, 실제 외부 API 및 실제 기기 수신은 이 검증 기록에 포함하지 않는다.
