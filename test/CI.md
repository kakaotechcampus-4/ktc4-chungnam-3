# 백엔드 GitHub Actions 사용 방법

## 실행 시점과 검사

`Backend CI`는 `develop` 대상 PR 생성·수정 시에만 실행한다.
`develop`에서 `main`으로 보내는 멘토 리뷰 PR과 머지 후 push에는 실행하지 않는다.
경로 필터를 두지 않아 문서만 수정한 PR에도 같은 검사 결과가 남는다.

| 검사 이름 | 하는 일 |
| --- | --- |
| Backend build | Java 21과 저장소의 Gradle Wrapper로 테스트 코드 컴파일·실행 JAR 생성 |
| Backend JUnit | 기존 `test` 태스크 전체 실행. `live` 태그는 기존 설정대로 제외 |
| Bruno API regression | 빌드한 JAR와 임시 PostgreSQL/pgvector로 `test/bruno` 전체 실행 |

빌드와 JUnit은 별도 작업이다. JUnit 실패 시에도 빌드가 성공했다면 Bruno 결과를 확인할 수 있다.
세 검사 모두 성공해야 전체 CI가 성공한다. 실패를 무시하거나 테스트를 임의로 제외하지 않는다.
이 워크플로는 배포하지 않으며 우분투 서버, PC의 SSH 터널, 팀 DB를 사용하지 않는다.

## 인증 파일과 외부 연동

`.github/ci/application-ci.properties`를 추가 설정으로 읽는다. 실제 API 키 대신 무효한 고정 값을 사용하고,
채팅·임베딩 클라이언트는 API 키 방식으로 초기화해 GCP ADC 파일이 필요하지 않게 한다.
FCM 인증 파일은 사용하지 않으며 꺼내기 에이전트는 기존 stub 모드로 실행한다.
초기 CI에 GitHub Secrets를 등록할 필요는 없다.

기본 Bruno 컬렉션은 유효한 영상 분석 요청을 보내지 않는다.
실제 분석용 `test/bruno-live`, 저장·분석 데이터가 필요한 `test/bruno-geofence`, Gradle `liveTest`,
`recallEval`, `recallJudge`는 이번 CI에 포함하지 않는다.
더미 키는 외부 API의 테스트 대역이 아니다. 실제 연동 동작이나 FCM 기기 수신이 검증됐다는 뜻은 아니다.

## 결과 확인

1. PR 하단의 검사 목록에서 실패한 검사 옆 `Details`를 연다.
2. 실패한 단계의 로그를 확인한다.
3. 실행 화면의 Artifacts에서 JUnit 결과 또는 Bruno XML을 내려받는다.
4. 백엔드 기동·API 테스트 실패 시에는 `bruno-backend-log`도 확인한다.

보고서와 JAR는 7일간 보관한다. Bruno CLI는 `4.3.0`으로 고정하며, 보고서에는 요청·응답 헤더와 본문을 제외한다.
세션 토큰이 출력되므로 CLI에 `--verbose`나 응답 출력 스크립트를 추가하지 않는다.
Bruno CLI 버전을 변경할 때는 기본 컬렉션을 다시 실행한다.

## 현재 알려진 선행 문제

`JudgeCriteriaTest.canonicalDocumentHasReadableMarkdownAndExcludesItsScoringSection`는
`backend/docs/에이전트 평가 기준.md`를 읽지만 CI 도입 시점의 저장소에는 이 문서가 없다.
팀의 실제 기준 문서를 해당 경로에 복원하거나 담당자와 올바른 참조 경로를 정해야 한다.
내용을 임의로 만들거나 해당 테스트를 제외해 통과시키지 않는다. 해결 전에는 JUnit 검사가 실패한다.

## 로컬에서 같은 구성 실행

Docker가 실행 중인 환경에서 저장소 루트를 기준으로 실행한다. 아래는 CI와 같은 Linux 환경의 명령이다.

```bash
export SPRING_CONFIG_ADDITIONAL_LOCATION="file:$(pwd)/.github/ci/application-ci.properties"
cd backend/Remembrall
bash ./gradlew test compileTestJava bootJar --no-daemon --console=plain
```

Bruno 검증은 별도의 비어 있는 테스트 DB를 준비한 뒤 저장소 루트에서 실행한다.
기존 DB를 지정하면 테스트 계정이 남고 Flyway 마이그레이션도 적용되므로 운영·팀 공용 DB를 지정하지 않는다.

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5432/remembrall_ci
export SPRING_DATASOURCE_USERNAME=remembrall_ci
export SPRING_DATASOURCE_PASSWORD=ci-only-password
npm install --global @usebruno/cli@4.3.0
bash .github/ci/run-bruno.sh
```

## PR에 적용하는 순서

현재 작업 브랜치에 CI 파일과 실행 대상 Bruno 파일을 함께 커밋해 `develop` 대상 PR을 만든다.
워크플로는 해당 PR부터 실행된다. 외부 기여자의 PR은 저장소 정책에 따라 실행 승인이 필요할 수 있다.
현재 로컬에만 추가된 테스트 파일을 커밋하지 않으면 GitHub에서는 그 테스트를 실행할 수 없다.

검사와 기존 실패 원인 해결을 확인한 후 저장소 관리자가 `develop` ruleset/브랜치 보호에
`Backend build`, `Backend JUnit`, `Bruno API regression`을 필수 검사로 등록한다.
워크플로 추가만으로 머지 차단 규칙이 자동 설정되지는 않는다.
