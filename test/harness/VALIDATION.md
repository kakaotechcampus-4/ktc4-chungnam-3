# 검증 기록

## 2026-11-09 — 장소 판별 메서드 호환 검증

- 작업 브랜치만 origin/develop 82a72b2로 fast-forward 갱신했다. 로컬 develop과 원격 브랜치는 변경하지 않았다.
- PR 75 통합 소스에서는 category를 포함한 6개 인자로 resolve를 호출하지만, 기존 하네스는 5개 인자 호출만 설정되어 null을 반환했다.
- 현재 develop에는 아직 5개 인자 메서드만 있으므로, 하네스가 두 형태의 resolve 호출에 합성 장소 결과를 반환하도록 수정했다.
- Ubuntu의 독립된 임시 DB·백엔드에서 아래와 같이 전체 컬렉션을 실행했다.
  - 현재 develop 82a72b2 + 수정본: 1회, 189개 통과.
  - develop과 PR 75의 702a4d6 통합 트리 4d1dbe2 + 수정본: 2회, 378개 통과.
- XML 보고서 기준 합계 567개 통과, 실패/오류 0개. 기존 분석 상태 검사 실패 4개도 두 차례 모두 통과했다.
- 보고서: Git 제외 폴더 `test/harness/reports/resolver-fix-20261109/`.
- 테스트 컨테이너·하네스 프로세스 종료 확인 후 원격 임시 폴더를 삭제했다. 운영 PID 518581 유지, active 및 헬스 UP 확인.
- GitHub Actions 재실행, 실제 외부 API 호출, 전체 JUnit 재실행은 이번 검증에 포함하지 않았다.

## 2026-11-08 검증

## 최근 발송 이력 차단 반영 후 재검증

- PR CI 실행 42475095738에서 앞 시나리오의 SENT 이력 때문에 후속 재시도·푸시 오류 검사가 차단되는 것을 확인했다.
- 각 독립 시나리오를 새 회원의 로그인·동의·저장으로 분리했다. 기존 발송 이력과 차단 로직은 유지했다.
- 동일 회원·장소에 새 이벤트 ID를 보내도 실행·알림·판단기·푸시 호출이 늘지 않는 검사 2개를 추가했다.
- 기준: 작업 브랜치 81dcc75와 origin/develop bcdf4a7의 통합 트리에 수정한 컬렉션을 적용했다.
  로컬 브랜치에 develop을 머지하거나 커밋하지 않고 임시 복사본에서 검증했다.
- Ubuntu 임시 환경에서 `bash test/harness/run.sh`를 실행했다.

| 컬렉션 | 1차 | 2차 | 실패/오류 |
| --- | ---: | ---: | ---: |
| bruno-place-list | 26 | 26 | 0 |
| bruno-auth-device | 30 | 30 | 0 |
| bruno-analysis-lifecycle | 68 | 68 | 0 |
| bruno-recall-retry | 65 | 65 | 0 |
| 합계 | 189 | 189 | 0 |

- XML 보고서 기준 총 378개 테스트 통과. 보고서는 Git 제외 폴더 `test/harness/reports/retry-fix-20261108/`에 보관했다.
- 재시도 컬렉션 65개 요청의 순서 중복·누락 및 JavaScript 문법, `git diff --check` 확인 통과.
- 테스트 컨테이너·하네스 프로세스가 남지 않은 것을 확인하고 원격 임시 폴더를 삭제했다.
- 운영 서비스 PID 518581 유지, 상태 active 및 헬스 UP 확인. 운영 설정·DB·서비스는 변경하지 않았다.
- 이번 수정 후 GitHub Actions 재실행 및 전체 JUnit 재실행은 하지 않았다. 아래는 수정 전 검증 기록이다.

## 최초 검증

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
