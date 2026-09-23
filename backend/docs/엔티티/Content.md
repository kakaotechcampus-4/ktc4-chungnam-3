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
| `category`              | VARCHAR(50)  | NULL                                | 서비스에서 정의한 콘텐츠 카테고리입니다.                 |
| `sourceStatus`          | VARCHAR(20)  | NOT NULL, DEFAULT `UNKNOWN`         | 마지막으로 확인한 원본 영상의 접근 상태입니다.             |
| `analysisStatus`        | VARCHAR(20)  | NOT NULL, DEFAULT `PENDING`         | 콘텐츠 분석 진행 상태입니다.                       |
| `analysisVersion`       | VARCHAR(30)  | NULL                                | 현재 분석 결과를 생성한 서비스 분석 규격 버전입니다.         |
| `lastAnalysisErrorCode` | VARCHAR(50)  | NULL                                | 마지막 분석 실패 원인을 구분하는 내부 오류 코드입니다.        |
| `metadataFetchedAt`     | TIMESTAMPTZ  | NULL                                | YouTube 메타데이터와 접근 상태를 마지막으로 확인한 시각입니다. |
| `analysisStartedAt`     | TIMESTAMPTZ  | NULL                                | 현재 분석 작업을 시작한 시각입니다.                   |
| `analyzedAt`            | TIMESTAMPTZ  | NULL                                | 분석이 정상적으로 완료된 시각입니다.                   |
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
- `PROCESSING`: 분석 중입니다.
- `SUCCEEDED`: 분석이 정상적으로 완료되었습니다.
- `FAILED`: 외부 API 오류 등으로 분석하지 못했습니다.

장소가 추출되지 않았거나 정보가 부족한 결과는 분석 실패가 아닙니다. Gemini 호출과 결과 처리가 정상적으로 끝났다면 `SUCCEEDED`로 처리합니다.

### 제약조건

```sql
UNIQUE (video_id)
CHECK (source_status IN ('UNKNOWN', 'AVAILABLE', 'UNAVAILABLE'))
CHECK (analysis_status IN ('PENDING', 'PROCESSING', 'SUCCEEDED', 'FAILED'))
```

### 생성 및 분석 흐름

```text
YouTube URL에서 videoId 추출
→ videoId로 Content 조회
→ 없으면 PENDING 상태로 생성
→ 분석 작업 시작
→ YouTube 메타데이터 조회
→ Gemini 분석
→ Content 분석 결과 저장
→ 검증된 장소를 ContentPlace로 연결
```

동시에 같은 영상이 저장될 수 있으므로 조회 후 삽입만으로 멱등성을 보장하지 않습니다. `videoId` UNIQUE 제약과 `INSERT ... ON CONFLICT`를 함께 사용합니다.
분석 시작은 `PENDING → PROCESSING` 상태 변경에 성공한 요청만 수행하도록 원자적으로 처리하여 동일 영상에 대한 Gemini 중복 호출을 방지합니다.

### 데이터 구분

콘텐츠 전체의 요약과 카테고리는 `Content`에 저장합니다.
특정 장소를 영상에서 어떻게 소개했는지와 등장 순서 및 신뢰도는 `ContentPlace`에 저장합니다.
장소 추출 및 검증 실패 과정은 `PlaceCandidateLog`에 기록합니다.
공용 후보 포함 여부는 `PersonalSave`와 `MemberConsent`로 판단하며 `Content`에는 별도의 공개 여부를 저장하지 않습니다.