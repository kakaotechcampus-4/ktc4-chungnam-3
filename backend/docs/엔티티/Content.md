## 콘텐츠 - Content

사용자가 공유한 YouTube 콘텐츠와 현재 분석 결과를 관리합니다.
동일한 영상은 `videoId`를 기준으로 멱등하게 처리하며, 여러 사용자가 저장해도 하나의 콘텐츠만 생성합니다.

### 필드

| 필드                      | 타입           | 제약조건                                | 설명                                     |
| ----------------------- | ------------ | ----------------------------------- | -------------------------------------- |
| `id`                    | UUID         | PK, NOT NULL                        | 서비스 내부 콘텐츠 식별자입니다.                     |
| `videoId`               | VARCHAR(32)  | NOT NULL, UNIQUE                    | YouTube가 영상에 부여한 고유 식별자입니다.            |
| `title`                 | VARCHAR(500) | NULL                                | YouTube 영상 제목입니다.                      |
| `summary`               | TEXT         | NULL                                | 콘텐츠가 무엇을 다루는지 정리한 분석 결과입니다.            |
| `category`              | VARCHAR(50)  | NULL                                | 전달받은 경우 저장하는 콘텐츠 카테고리입니다.              |
| `sourceStatus`          | VARCHAR(20)  | NOT NULL, DEFAULT `UNKNOWN`         | 마지막으로 확인한 원본 영상의 접근 상태입니다.             |
| `analysisStatus`        | VARCHAR(20)  | NOT NULL, DEFAULT `PENDING`         | 콘텐츠 분석 진행 상태입니다.                       |
| `analysisVersion`       | VARCHAR(30)  | NULL                                | 현재 분석 결과를 생성한 서비스 분석 규격 버전입니다.         |
| `lastAnalysisErrorCode` | VARCHAR(50)  | NULL                                | 마지막 분석 실패 원인을 구분하는 내부 오류 코드입니다.        |
| `metadataFetchedAt`     | TIMESTAMPTZ  | NULL                                | 전달받은 YouTube 메타데이터 확인 시각입니다.           |
| `analysisStartedAt`     | TIMESTAMPTZ  | NULL                                | 현재 분석 작업을 시작한 시각입니다.                   |
| `analyzedAt`            | TIMESTAMPTZ  | NULL                                | 분석 결과가 최종 상태로 확정된 시각입니다.               |
| `createdAt`             | TIMESTAMPTZ  | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 콘텐츠가 생성된 시각입니다.                        |
| `updatedAt`             | TIMESTAMPTZ  | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 콘텐츠가 마지막으로 변경된 시각입니다.                  |
|                         |              |                                     |                                        |

### 상태

`sourceStatus`는 다음 값을 사용합니다.

- `UNKNOWN`: 아직 원본 상태를 확인하지 않았습니다.
- `AVAILABLE`: 원본 영상에 접근할 수 있습니다.
- `UNAVAILABLE`: 삭제, 비공개 또는 접근 제한으로 사용할 수 없습니다.

`analysisStatus`는 다음 값을 사용합니다.

- `PENDING`: 분석 대기 상태입니다.
- `ANALYZING`: 분석을 선점한 요청이 처리 중입니다.
- `COMPLETED`: 분석이 정상적으로 완료되었습니다.
- `PARTIAL_SUCCESS`: 일부 결과를 얻었지만 분석이 부분적으로 실패했습니다.
- `FAILED`: 유효한 분석 결과를 얻지 못했습니다.

장소가 추출되지 않았거나 정보가 부족한 결과는 분석 실패가 아닙니다. 분석과 결과 처리가 정상적으로 끝났다면 `COMPLETED`로 처리합니다.
`COMPLETED`, `PARTIAL_SUCCESS`, `FAILED` 결과의 조건부 반영에 성공하면 모두 `analyzedAt`을 기록합니다. 조건부 반영에 실패하면 기존 값은 변경하지 않습니다.

### 제약조건

```sql
UNIQUE (video_id)
CHECK (source_status IN ('UNKNOWN', 'AVAILABLE', 'UNAVAILABLE'))
CHECK (analysis_status IN ('PENDING', 'ANALYZING', 'COMPLETED', 'PARTIAL_SUCCESS', 'FAILED'))
```

### 현재 저장·분석 상태 반영 흐름

```text
ContentPersistenceService.saveAndClaim(memberId, videoId) 호출
→ Content INSERT ... ON CONFLICT DO NOTHING 후 기존 행 조회
→ PersonalSave INSERT ... ON CONFLICT DO NOTHING 후 기존 행 조회
→ PENDING → ANALYZING 조건부 UPDATE에 성공한 요청만 분석 선점
→ Content·PersonalSave ID, 현재 상태와 선점 여부 반환
→ 외부 분석 결과를 전달받으면 ANALYZING인 Content에만 최종 상태 반영
```

Content와 PersonalSave에는 각각 `video_id`, `(member_id, content_id)` UNIQUE 제약을 적용합니다. 저장·선점과 결과 반영은 각각 별도의 짧은 트랜잭션으로 처리합니다.
선점할 때 `analysisStartedAt`을 기록합니다. 결과 반영에 실패해도 PersonalSave는 유지하며, `ANALYZING` 자동 복구는 현재 범위에 없습니다.
YouTube·Gemini 호출과 이 저장 계층의 연결, 검증된 장소의 `ContentPlace` 저장은 아직 구현되지 않았습니다. 외부 호출을 연결할 때는 호출 중 트랜잭션을 유지하지 않아야 합니다.

### 데이터 구분

콘텐츠 전체의 요약과 전달받은 카테고리는 `Content`에 저장합니다.
특정 장소를 영상에서 어떻게 소개했는지와 신뢰도를 `ContentPlace`에 저장하는 것은 이후 구현할 설계입니다.
장소 추출 및 검증 실패 과정을 `PlaceCandidateLog`에 기록하는 것도 이후 구현할 설계입니다.
공용 후보 포함 여부는 `PersonalSave`와 `MemberConsent`로 판단하며 `Content`에는 별도의 공개 여부를 저장하지 않습니다.