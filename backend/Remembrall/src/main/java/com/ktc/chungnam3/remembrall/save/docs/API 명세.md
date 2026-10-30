# API 명세 (저장 파이프라인)

이 문서는 **저장 파이프라인 범위**의 REST API 계약만 다룬다. 콘텐츠 저장 요청만 포함하며,
꺼내기 에이전트·알림 등 다른 도메인의 API는 다루지 않는다.

> **2026-10-11 갱신**: `SavePipelineController`/`SavePipelineService` 실제 구현 완료 후, 아래 내용을
> 실제 코드(`SaveContentRequestDto`/`SaveContentResponseDto`/`PlaceOutcomeDto`) 기준으로 다시 썼다.
> 이전 초안(설계 문서만 보고 먼저 쓴 버전)의 `PLACE_RESOLVED`/단일 `place`/`confirm` 필드,
> 되묻기 전용 엔드포인트(`POST /api/contents/{contentId}/confirm`)는 실제 구현과 달라서 전부 뺐다 —
> 영상 하나에 장소 후보가 여럿 나올 수 있어(`저장 파이프라인.md` 참고) 후보별로 독립 판정하는
> 구조로 갔고, 되묻기 응답 제출은 아직 별도 엔드포인트가 없다(아래 "아직 안 된 것" 참고).
> `/api/personal-saves`를 따로 만들 계획이 팀에 있었다면, 이 엔드포인트가 그 역할까지 겸하고
> 있다는 점을 담당자와 맞춰야 한다 — 중복/충돌 방지.

## 1. 콘텐츠 저장 + 분석 요청

`POST /api/contents` (인증 필요 - 세션 쿠키/헤더, 기존 `/api/auth/kakao` 로그인 흐름과 동일)

유튜브 URL 하나를 받아 "링크 저장"과 "분석(메타데이터 조회 → 영상 분석 → 장소 탐색·검증 →
카카오 검증 → 확신도 판정 → DB 반영)"을 한 요청 안에서 전부 실행한다. 분석은 콘텐츠당 한 번만
실행된다(이미 분석 중이거나 끝난 콘텐츠를 다시 요청하면 `alreadyProcessed=true`만 돌려주고 재실행
안 함).

### 요청

```json
{
  "youtubeUrl": "https://www.youtube.com/shorts/abcd1234"
}
```

### 응답 200 — 이미 처리된 콘텐츠

```json
{
  "contentId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "alreadyProcessed": true,
  "status": null,
  "summary": null,
  "places": [],
  "failureMessage": null
}
```

### 응답 200 — 처음 분석한 콘텐츠

`status`는 콘텐츠 단위(영상 분석 자체 성공 여부), `places[].decision`은 후보 하나하나의 판정이다
(둘 다 `com.ktc.chungnam3.remembrall.save.dto.SavePipelineResultDto`/`PlaceOutcomeDto` 그대로).

| status | 의미 |
|---|---|
| `SUCCESS` | 영상 분석 성공 - 장소가 전부 없어도(`NO_PLACE`)/되묻기(`NEEDS_CONFIRMATION`)가 섞여도 포함 |
| `PARTIAL` | 영상 분석은 성공했지만 후보 중 하나 이상에서 장소 검색/카카오 검증 API가 실패함 |
| `FAILED` | 영상 분석(VideoContentAnalyzer) 자체가 실패함 - `summary`/`places` 없음, `failureMessage`만 채워짐 |

`places[].decision` 값: `RESOLVED`(저장 완료, `resolvedPlace` 채워짐) /
`NEEDS_CONFIRMATION`(`confirmRequest` 채워짐, 아래 "아직 안 된 것" 참고) /
`NO_PLACE`(정상 케이스, 확정 불가) / `PLACE_SEARCH_FAILED` / `PLACE_VERIFICATION_FAILED`
(외부 API 호출 자체가 실패).

```json
{
  "contentId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "alreadyProcessed": false,
  "status": "SUCCESS",
  "summary": "대전 성심당을 소개하는 영상...",
  "places": [
    {
      "candidateId": "p1",
      "decision": "RESOLVED",
      "resolvedPlace": {
        "candidateId": "p1",
        "name": "성심당",
        "branchName": "본점",
        "address": "대전 중구 ...",
        "lat": 36.32,
        "lng": 127.42,
        "geocodingProvider": "LOCATIONIQ",
        "geocodingPlaceId": "227225446",
        "verificationPlaceId": "26410594"
      },
      "confirmRequest": null
    }
  ],
  "failureMessage": null
}
```

### 응답 4xx

| 상황 | 상태 코드 | 비고 |
|---|---|---|
| `youtubeUrl`에서 videoId를 못 찾음(지원 안 하는 형식 포함) | 400 (`INVALID_REQUEST`) | `YoutubeUrlParser.extractVideoId` 실패 시 |
| 인증 안 됨 | 401 | 다른 컨트롤러와 동일한 세션 인증 |

영상 분석/장소 검색 자체의 실패는 전부 200으로 응답하고 `status`/`places[].decision`으로 구분한다
(네트워크·외부 API 실패를 4xx로 올리지 않는다 - 요청 자체는 정상이었기 때문).

## 2. 아직 안 된 것 (TODO)

- ~~**되묻기 응답 제출 엔드포인트**~~ — **구현 완료(2026-10-27~30)**. `places[].decision ==
  NEEDS_CONFIRMATION`인 후보는 `ConfirmCandidateRepository`에 영속화되고,
  `POST /api/personal-saves/{personalSaveId}/places/{candidateId}/confirm`(`ConfirmAnswerController`)
  으로 답변을 제출받아 반영한다. 과거 있었던 "미응답 시 자동으로 상위 지역 좌표로 저장" 폴백
  (`ConfirmPolicy`)은 호출부가 항상 `alreadyAsked=false`를 넘겨 한 번도 실행되지 않던 죽은 코드였음이
  PR 리뷰로 드러나 삭제함 — 지금은 답이 올 때까지 `NEEDS_CONFIRMATION`으로 남아있는다.
- **분석 상태 조회(polling) API**: `alreadyProcessed=true` 응답은 "이미 처리됐다"만 알려주고 그
  결과 자체를 돌려주지 않는다. 결과를 다시 보려면 별도 조회 API가 필요(미구현).

## 3. 참고 문서

- `save/docs/저장 파이프라인.md` — 이 API가 내부적으로 실행하는 파이프라인 설계
- `backend/docs/저장담당 요구 사항.md` — 영상 분석 단계의 DTO 계약
