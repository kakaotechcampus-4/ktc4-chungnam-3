## 콘텐츠와 장소 연결 - ContentPlace

콘텐츠에서 특정 장소가 어떻게 소개되었는지를 관리합니다.  
`Content`와 `Place`의 다대다 관계를 연결하며, 콘텐츠에 종속되는 장소 설명과 추출 신뢰도를 저장합니다.  
검증에 성공한 장소만 `ContentPlace`로 생성하며, 꺼내기 에이전트는 장소를 제안하는 이유와 콘텐츠 맥락을 구성할 때 이 정보를 사용합니다.

### 필드

| 필드                     | 타입           | 제약조건                                | 설명                                |
| ---------------------- | ------------ | ----------------------------------- | --------------------------------- |
| `id`                   | UUID         | PK, NOT NULL                        | 콘텐츠와 장소 연결 식별자입니다.                |
| `contentId`            | UUID         | FK, NOT NULL                        | 장소가 추출된 콘텐츠 식별자입니다.               |
| `placeId`              | UUID         | FK, NOT NULL                        | 검증이 완료된 장소 식별자입니다.                |
| `description`          | TEXT         | NULL                                | 콘텐츠에서 해당 장소를 어떻게 소개했는지 요약한 내용입니다. |
| `extractionConfidence` | NUMERIC(4,3) | NULL, CHECK                         | Gemini가 반환한 장소 추출 신뢰도입니다.         |
| `createdAt`            | TIMESTAMPTZ  | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 연결이 생성된 시각입니다.                    |
| `updatedAt`            | TIMESTAMPTZ  | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 관계 정보가 마지막으로 변경된 시각입니다.           |

### 제약조건

```sql
UNIQUE (content_id, place_id)
FOREIGN KEY (content_id) REFERENCES content(id) ON DELETE CASCADE
FOREIGN KEY (place_id) REFERENCES place(id) ON DELETE CASCADE
CHECK (
    extraction_confidence IS NULL
    OR extraction_confidence BETWEEN 0 AND 1
)
```

동일한 콘텐츠와 장소의 연결은 하나만 생성합니다.  
`description`은 장소 자체의 설명이 아니라 해당 콘텐츠에서 장소를 소개한 맥락을 저장합니다.  
`extractionConfidence`는 모델이 반환한 참고값이므로 실제 장소 검증 결과를 대신하거나 단독으로 저장 여부를 결정하는 기준으로 사용하지 않습니다.  
콘텐츠를 다시 분석하면 기존 관계 정보를 갱신하고, 전체 분석이 성공한 뒤 새로운 결과를 한 번에 반영합니다.