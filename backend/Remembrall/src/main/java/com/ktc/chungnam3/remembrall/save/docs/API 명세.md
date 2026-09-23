# API 명세 (저장 파이프라인)

이 문서는 **저장 파이프라인 범위**의 REST API 계약만 다룬다. 콘텐츠 저장 요청과 그에 대한 되묻기
응답만 포함하며, 꺼내기 에이전트·알림 등 다른 도메인의 API는 다루지 않는다.

> **초안 상태**: 아직 `SavePipelineService`나 컨트롤러 코드가 없는 상태에서, 설계 문서
> (`저장 파이프라인.md`, `저장담당 요구 사항.md`) 내용을 기준으로 먼저 작성했다. 경로·필드명은
> 제안이고, 실제 구현하면서 바뀔 수 있다. 에러 코드 매핑처럼 아직 안 정해진 부분은 TODO로 남겨둔다.

## 1. 콘텐츠 저장 요청

`POST /api/contents`

유튜브 URL 하나를 저장 파이프라인에 넘겨, 메타데이터 조회 → 영상 분석 → 장소 탐색·검증 →
확신도 판정까지 실행한다 (`저장 파이프라인.md` 3장 참고).

### 요청

```json
{
  "youtubeUrl": "https://www.youtube.com/shorts/abcd1234"
}
```

### 응답 200 — `status`에 따라 채워지는 필드가 다름

| status | 의미 | 채워지는 필드 |
|---|---|---|
| `PLACE_RESOLVED` | 장소 1건으로 확정됨 | `place` |
| `NEEDS_CONFIRMATION` | 후보 2~3개로 좁혀짐, 선택 필요 | `confirm` |
| `NO_PLACE` | 후보 없음/확정 불가 (정상 케이스) | 없음 |

```json
{
  "contentId": "c_123",
  "status": "NEEDS_CONFIRMATION",
  "summary": "대전 성심당을 소개하는 영상...",
  "place": null,
  "confirm": {
    "question": "어느 지점을 저장할까요?",
    "candidates": [
      { "candidateId": "p1", "name": "성심당", "branchName": "본점", "address": "대전 중구 ..." },
      { "candidateId": "p2", "name": "성심당", "branchName": "DCC점", "address": "대전 유성구 ..." }
    ]
  }
}
```

`PLACE_RESOLVED` 예시:

```json
{
  "contentId": "c_124",
  "status": "PLACE_RESOLVED",
  "summary": "...",
  "place": {
    "candidateId": "p1",
    "name": "성심당",
    "branchName": "본점",
    "address": "대전 중구 ...",
    "lat": 36.32,
    "lng": 127.42
  },
  "confirm": null
}
```

### 응답 4xx

| 상황 | 상태 코드 | 비고 |
|---|---|---|
| 지원하지 않는 형식 (Shorts 외 링크) | TODO | `저장 파이프라인.md` 3장 "0. 지원 형식 검증" — 파이프라인 호출 전 Spring이 거절 |
| 비공개·삭제된 영상 | TODO | `FailureStage.METADATA_FETCH`, 재시도 불가 |
| 분석 실패 (Gemini 호출/파싱) | TODO | `FailureStage.GEMINI_CALL` / `RESPONSE_MAPPING` |
| 장소 검색 실패 | TODO | `FailureStage.PLACE_SEARCH` (신규 제안, 아직 확정 아님) |

> **TODO**: 내부 `ExtractionStatus`/`FailureStage`/`FailureInfoDto.retryable`을 HTTP 상태 코드·에러
> 바디로 어떻게 매핑할지는 아직 안 정해짐. `SavePipelineService` 구현하면서 확정 필요.

## 2. 되묻기 응답 제출

`POST /api/contents/{contentId}/confirm`

`NEEDS_CONFIRMATION` 응답을 받은 뒤, 사용자가 후보 중 하나를 선택하면 호출한다.

### 요청

```json
{ "selectedCandidateId": "p1" }
```

### 응답 200

```json
{
  "contentId": "c_123",
  "status": "PLACE_RESOLVED",
  "place": {
    "candidateId": "p1",
    "name": "성심당",
    "branchName": "본점",
    "address": "대전 중구 ...",
    "lat": 36.32,
    "lng": 127.42
  }
}
```

**참고**
- 되묻기는 콘텐츠당 최대 1회 (`저장 파이프라인.md` 3장 "핵심 원칙"). 이 엔드포인트가 한 번 호출된
  콘텐츠는 다시 `NEEDS_CONFIRMATION` 상태로 돌아가지 않는다.
- 미응답 시엔 상위 지역으로 저장하고 다시 묻지 않는다 — 이걸 **누가/언제 트리거하는지**(스케줄러 vs
  다음 요청 시점 확인)는 아직 TODO.

## 3. 참고 문서

- `save/docs/저장 파이프라인.md` — 이 API가 내부적으로 실행하는 파이프라인 설계
- `backend/docs/저장담당 요구 사항.md` — 영상 분석 단계의 DTO 계약
