# 방문 조건 확인 중심 도구 · v4

## 흐름

잠정 제안 → checkVisitConditions → 부적합하면 searchCandidates → 새 대안도 확인 → 최종 제안 또는 NO_ACTION.

등록 도구는 checkVisitConditions와 searchCandidates 두 개다. 회원과 트리거 장소는 실행 ID에서 조회해 ToolContext로 넣는다. 모델은 회원·트리거·제외 목록을 인자로 전달할 수 없다. CONTENT 제안과 NO_ACTION에는 방문 확인이 필요 없다. 도구를 끄면 도구 안내 문단은 남지만 콜백과 ToolCallingAdvisor는 등록하지 않는다.

searchCandidates의 source는 PERSONAL, PUBLIC, ALL이며 생략하면 ALL이다. 초기 입력과 실제로 모델에 반환된 이전 도구 응답의 콘텐츠·장소 ID를 실행별로 기억하고, SQL의 LIMIT 전에 자동 제외한다. 공용 콘텐츠의 접근 조건은 PublicCandidateRepository의 기존 활성 기여 동의 조건을 그대로 사용한다.

## 기본값

| 설정 | 기본값 |
|---|---:|
| recall.agent.tools-enabled | true |
| recall.agent.max-tool-calls | 6 |
| recall.agent.execution-timeout | 90s |
| recall.agent.timeout | 30s |
| recall.agent.max-tool-response-bytes | 24000 |
| recall.trace.max-detail-bytes | 65536 |
| recall.tools.max-visit-places | 3 |
| recall.tools.visit-summary-length | 2000자 |
| recall.tools.recent-proposal-window | 7d |
| recall.tools.search-place-limit / search-content-limit | 각각 5 |
| recall.weather.connect-timeout / read-timeout | 2s / 3s |
| recall.weather.forecast-hours | 6 |
| recall.weather.max-cache-entries | 512 |

방문 확인에는 최초 입력의 200자 제한과 기존 도구 응답의 600자 추가 자르기를 적용하지 않는다. 요약과 장소 소개는 설정한 2000자까지 제공한다. 응답 전체의 UTF-8 바이트 한도는 유지하며 생략 시 truncated를 표시한다. 모델에 실제로 전달한 응답 문자열을 Trace에 그대로 남긴다.

PERSONAL 운영 정보의 referenceAt은 PersonalSave.savedAt이다. PUBLIC은 기여 회원의 저장 관계를 노출하지 않도록 Content.createdAt(최초 콘텐츠 저장 시점)을 쓰며 referenceTimeBasis=CONTENT_FIRST_STORED로 구분한다. 현재 영업 사실을 보장하지 않는 과거 저장 자료다.

최근 이력은 해당 회원의 SENT 알림만 payload의 placeId로 찾는다. sentAt, opened, openedAt을 주며 다른 회원, 전송 실패·대기 상태, 기간 밖·미래 이력은 제외한다. 날씨는 트리거 위치로 도구 요청당 한 번 조회해 공통 필드에 담는다. 날씨 실패도 운영 정보와 알림 이력을 없애지 않는다.

## 기상청 클라이언트

- 공식 서비스: [공공데이터포털 단기예보 조회서비스](https://www.data.go.kr/data/15084084/openapi.do).
- 현재 강수·기온: getUltraSrtNcst, SKY·시간별 POP: getVilageFcst.
- 기준 URL: https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0
- 파라미터: serviceKey, pageNo=1, numOfRows=1000, dataType=JSON, base_date, base_time, nx, ny.
- 발표 규칙과 LCC 변환은 포털의 2026-10-19 공식 활용가이드 ZIP 내 문서/C 예제에 맞췄다. [공식 활용가이드](https://www.data.go.kr/cmm/cmm/fileDownload.do?atchFileId=FILE_000000007732424&fileDetailSn=1), [기상청 격자 영역 정보](https://apihub.kma.go.kr/getAttachFile.do?fileName=%2820240305%29%EB%8F%99%EB%84%A4%EC%98%88%EB%B3%B4+%EA%B2%A9%EC%9E%90%EC%98%81%EC%97%AD+%EC%A0%95%EB%B3%B4.pdf).
- 최신 가이드의 초단기실황은 10분 단위 업데이트와 10분 이후 제공을 사용한다. now−10분을 10분 단위로 내림해 기준을 선택한다. 단기예보는 02/05/08/11/14/17/20/23시 발표의 10분 후부터 선택한다. 모두 Asia/Seoul 기준이며 날짜 경계를 처리한다.
- SKY는 실황 관측에 없는 항목이므로 가장 가까운 단기예보 시각을 validAt으로 함께 표시한다. 현재 강수/기온의 관측 기준 시각과 예보 발표 시각을 구분한다.
- endpoint·격자·발표 기준 시각별 메모리 캐시를 공유한다. 같은 키의 동시 요청도 endpoint별 한 번만 호출한다. 실황과 단기예보는 서로 다른 endpoint이므로 최초 조회에는 각 한 번, 두 HTTP 요청이 필요하다.
- secrets.properties에 kma.api.key를 넣는다. 승인 전 빈 값이어도 시작되며 도구에는 WEATHER_UNAVAILABLE을 반환한다. 이번 검증에서는 실제 기상청 endpoint를 호출하지 않는다.

## 평가 장면의 선택 필드

기존 장면은 수정하지 않아도 된다. weather가 없으면 맑음·강수 없음·20도·향후 6시간 강수확률 0으로 고정한다.

```json
{
  "weather": {
    "precipitation": "RAIN",
    "temperatureCelsius": 17,
    "sky": "OVERCAST",
    "rainProbabilities": [80, 70, 60]
  },
  "recentNotifications": [
    {"placeKey": "cafe", "sentAt": "2026-10-10T17:10:00+09:00", "opened": false}
  ]
}
```

precipitation은 NONE, RAIN, RAIN_AND_SNOW, SNOW, SHOWER, DRIZZLE, DRIZZLE_AND_SNOW, SNOW_FLURRIES다. sky는 CLEAR, PARTLY_CLOUDY, OVERCAST다. 강수확률 배열은 장면 이벤트 시각 이후 1시간 간격 순서다.

평가는 FakeWeatherClient만 주입하며 실제 KmaWeatherClient를 생성하지 않는다. 장면별 EvaluationClock을 이벤트 시각으로 맞춰 최근 이력의 기간 계산도 고정한다. 최근 알림은 해당 회원의 COMPLETED/PROPOSE 실행과 SENT 알림으로 시드한다. 장면 사이에는 데이터와 가짜 날씨를 초기화한다.

## 회귀 실행

```powershell
.\gradlew.bat recallEval --console=plain '-Drecall.eval.enabled=true' '-Drecall.eval.mode=seed-db' '-Drecall.eval.scenes=classpath:recall/evaluation/datasets/golden-scenes.json' '-Drecall.eval.split=tune' '-Drecall.eval.repetitions=3' '-Drecall.eval.output=build/recall-evaluation/v4-confirmation/golden-tune-v4.jsonl' '-Drecall.agent.provider=google-genai' '-Drecall.agent.model=gemini-3.6-flash' '-Drecall.agent.tools-enabled=true'
```

실제 임베딩·모델·격리 PostgreSQL은 사용하고 날씨만 가짜 구현으로 고정한다. 결과는 기존처럼 시각별 새 폴더에 쌓인다. 과거 v3 결과는 수정하지 않는다. 새 도구 세트의 실제 모델 평가는 회귀 24장면 평가와 별개다.
