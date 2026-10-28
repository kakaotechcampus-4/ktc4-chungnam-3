# 저장된 평가 결과 Judge

에이전트, 임베딩, DB는 실행하지 않는다. 입력 JSONL의 실제 입력과 Trace, 변환된 결과, 서버 검증 결과만 읽는다.

## 실행

```powershell
.\gradlew.bat recallJudge `
  '-Drecall.judge.input=build/recall-evaluation/원본.jsonl' `
  '-Drecall.judge.scenes=src/test/resources/recall/evaluation/datasets/golden-scenes.json' `
  '-Drecall.judge.provider=openai' `
  '-Drecall.judge.model=실제-모델명'
```

공급자와 모델 기본값은 없다. 공급자는 `google-genai`, `openai`를 지원한다. 설정은 에이전트 설정과 분리된다.

| 설정/환경변수 | 용도 |
|---|---|
| recall.judge.provider / RECALL_JUDGE_PROVIDER | 공급자, 필수 |
| recall.judge.model / RECALL_JUDGE_MODEL | 모델, 필수 |
| recall.judge.openai-base-url / RECALL_JUDGE_OPENAI_BASE_URL | OpenAI 호환 base URL, 해당 공급자에서 필수 |
| recall.judge.openai-api-key / RECALL_JUDGE_OPENAI_API_KEY | OpenAI 호환 키, 해당 공급자에서 필수 |
| recall.judge.timeout | 호출 제한 시간, 기본 60s |
| recall.judge.temperature / RECALL_JUDGE_TEMPERATURE | 기본 0. 고정 온도만 허용하는 공급자/모델은 그 모델의 허용 최솟값을 명시 |
| recall.judge.force-structured-output / RECALL_JUDGE_FORCE_STRUCTURED_OUTPUT | 기본 false. true일 때만 공급자의 JSON Schema 구조화 출력을 강제 |
| recall.judge.conditions | 기본 file:../docs/에이전트 평가 기준.md (backend/docs 원본) |

기본은 시스템 프롬프트로 JSON 형식을 요청하고 서버가 파싱·검증하는 방식이다. OpenAI 호환 요청에는 `response_format`을 보내지 않으며, Google 요청에도 응답 MIME 형식이나 스키마를 강제하지 않는다. 구조화 출력을 지원하는 공급자에서는 `recall.judge.force-structured-output=true`로 기존 강제 방식을 선택할 수 있다.

응답을 감싼 JSON 코드 블록은 벗겨내고 파싱한다. JSON 파싱이나 기존 응답 검증에 실패하면 시스템·사용자 메시지를 바꾸지 않고 한 번 더 요청한다. 최대 두 번 모두 실패하면 JUDGE_FAILED로 기록한다. 통신·모델 호출 실패와 정상 응답의 필수 조건 위반(BAD)은 이 재시도 대상이 아니다. 재요청한 경우 호출 시간과 사용 토큰은 합산하고, 원본 응답은 마지막으로 받은 값을 보존한다.

Google 키와 Vertex 설정은 기존 `secrets.properties` 또는 `spring.ai.google.genai.*` 설정을 읽는다. 엘리스 키를 사용할 경우 키를 출력하거나 실행 인자에 적지 말고 환경변수로 연결한다.

```powershell
$env:RECALL_JUDGE_OPENAI_API_KEY = $env:ELICE_API_KEY
$env:RECALL_JUDGE_OPENAI_BASE_URL = '확인한 엘리스 base URL'
```

## 판정

1. 원본 평가의 `SEED`, `CANDIDATE_PREPARATION`, `MODEL_CALL`, `CONVERSION` 실패는 AGENT_FAILED(무점수)다. 원래 실패 단계·메시지를 그대로 보존하고, 서버 검증 기록이 없어도 Judge를 호출하지 않는다.
2. 에이전트 단계 실패가 없고 원본 `verdict.passed=false`면 BAD(1), 조건 5로 기록하고 모델을 호출하지 않는다.
3. 모델은 `에이전트 평가 기준.md`의 조건 1/2/3/4/6/7 위반만 판정한다. 위반이 있으면 BAD(1).
4. 위반이 없으면 코드가 goodChoices와 비교하여 일치하면 GOOD(5), 불일치하면 ACCEPTABLE(3), 비교할 수 없으면 UNCOMPARABLE(무점수).

`goodChoices`, `reference`, `judgeNotes`, 채점 기준 표는 Judge 모델에 전달하지 않는다. 실제 입력에서 잘린 요약의 나머지나 미동의 회원의 저장물도 전달하지 않는다. 빈 도구 Trace는 도구 반환 결과가 없다고 명시한다.

기준 문서는 `backend/docs/에이전트 평가 기준.md` 하나만 유지한다. 매 실행마다 원본을 읽고 `## 채점 기준` 이전의 필수 조건과 평가 적용 원칙만 모델에 전달한다. 원본 전체 파일 바이트의 SHA-256과 실제 문서 경로를 JSONL의 `criteriaSha256`, `criteriaPath` 및 Markdown 보고서에 기록한다. 문서가 없으면 복사본으로 대체하지 않고 실패한다. 단위 테스트는 문서 복사본 대신 고정 테스트 문자열을 사용한다.

JUDGE_FAILED는 Judge 호출, 응답 파싱·검증이나 Judge 입력 검증에 실패한 경우다. 원본 에이전트 실패를 이 라벨로 바꾸지 않는다. AGENT_FAILED, JUDGE_FAILED, UNCOMPARABLE은 품질 평균의 분모에서 제외한다. 오류 단계, 메시지, 얻은 원본 응답과 토큰은 결과에 보존한다.

보고서는 품질 점수와 신뢰성을 분리한다. 신뢰성에는 전체 실행 수 대비 AGENT_FAILED·JUDGE_FAILED 비율, 라벨별 실패 단계 건수, 장면·반복·원본 줄 번호별 실패 위치를 기록한다. 장면별 라벨 표와 사람 라벨 CSV에도 AGENT_FAILED를 그대로 표시한다. 기존 결과 파일은 재작성하지 않는다.

## ID 대응 및 선택 비교

다음 선택적 ID 대응 형식을 우선 사용한다. 역방향 ID→키 매핑과 `placeIds`/`contentIds` 필드명도 읽는다.

```json
{
  "seedIds": {
    "places": {"cafe": "place UUID"},
    "contents": {"eval-seed-gold-video": "content UUID"}
  }
}
```

없으면 실제 입력과 도구 반환 결과에 있는 장소 이름/영상 제목을 장면의 키/videoId와 대응한다. 중복된 이름·제목을 임의로 선택하지 않는다. 선택 ID를 대응할 수 없으면 `comparison.status=UNCOMPARABLE`, `matched=null`과 오류를 기록한다. 이때 위반이 없으면 UNCOMPARABLE 라벨로 남기고 점수와 평균 분모에서 제외한다. 보고서에는 개수, 해당 장면과 반복, 대응 실패 ID를 표시한다. 위반 때문에 선택 비교를 건너뛴 경우 `NOT_RUN`이며, 대응 실패 자체는 BAD 결과에도 기록된다.

`ordered=true`면 선택 순서까지 일치해야 한다. false 또는 생략이면 순서를 무시한다. videos가 지정되면 영상도 일치해야 한다. 장소와 영상이 모두 지정된 경우 항목별 연결도 함께 비교한다. NO_ACTION끼리는 일치한다. `matchedIndexes`는 0부터 시작한다.

## 출력

평가(`recallEval`)와 Judge(`recallJudge`)를 실행할 때마다 한국시간(Asia/Seoul) 기준 `yyyy-MM-dd_HH-mm-ss-SSS` 이름의 새 폴더를 만든다. 같은 밀리초에 시작한 실행이 있으면 `-2`, `-3` 순으로 접미사를 붙여 덮어쓰지 않는다. 실행 ID와 시작 시각은 기존처럼 JSONL에 남긴다.

평가는 `recall.eval.output`에 지정한 파일명을 새 폴더 안에서 그대로 사용한다. 기본 파일명은 JSON 모드 `results.jsonl`, 시드 DB 모드 `seed-results.jsonl`이다. Judge는 다음 세 파일을 한 폴더에 모은다.

```text
build/recall-evaluation/
  2026-10-23_03-30-00-123/
    seed-results.jsonl
  2026-10-23_03-35-00-456/
    judged.jsonl
    judged.md
    judged-human.csv
```

Judge 입력이 이전 실행 폴더 안에 있으면 새 폴더는 그 실행 폴더와 같은 상위 경로에 만든다. 날짜 폴더를 중첩하지 않는다. 기존 결과는 이동하거나 수정하지 않는다. 출력 경로는 실행 시 콘솔에 표시된다. build 아래 생성 결과는 clean 시 삭제되므로 보관이 필요하면 별도로 복사한다.

CSV의 humanLabel만 GOOD/ACCEPTABLE/BAD로 채운 후 다음을 실행한다. 비교 모드는 모델 설정, 키, 네트워크 호출이 필요 없다. finalLabel 열을 바꿔도 비교 기준은 원본 judged JSONL이다.

```powershell
.\gradlew.bat recallJudge `
  '-Drecall.judge.input=build/recall-evaluation/2026-10-23_03-35-00-456/judged.jsonl' `
  '-Drecall.judge.human-labels=build/recall-evaluation/2026-10-23_03-35-00-456/judged-human.csv'
```

사람 라벨 미입력과 AGENT_FAILED, JUDGE_FAILED, UNCOMPARABLE은 일치율 분모에서 제외한다. CSV에서 빠진 행 수도 따로 표시한다. rowId는 원본 실행 ID와 JSONL 줄 번호의 조합이므로 중복·다른 실행의 행은 거부한다.
