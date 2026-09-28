## 트리거 - Trigger

개인 저장물을 다시 꺼내기 위한 위치 조건을 관리합니다.  
`PersonalSave`와 `ContentPlace`를 연결하며 지오펜스 반경과 활성 상태를 저장합니다.  
하나의 콘텐츠에 여러 장소가 연결되어 있으면 장소별로 트리거를 생성할 수 있으며, 공용 후보는 트리거를 생성하지 않습니다.

### 필드

| 필드               | 타입          | 제약조건                                | 설명                                                     |
| ---------------- | ----------- | ----------------------------------- | ------------------------------------------------------ |
| `id`             | UUID        | PK, NOT NULL                        | 트리거 식별자입니다.                                            |
| `personalSaveId` | UUID        | FK, NOT NULL                        | 트리거의 소유자가 되는 개인 저장물 식별자입니다.                            |
| `contentPlaceId` | UUID        | FK, NOT NULL                        | 트리거 대상이 되는 콘텐츠와 장소의 연결 식별자입니다.                         |
| `radiusMeters`   | INTEGER     | NOT NULL, CHECK                     | 지오펜스 반경을 미터 단위로 저장합니다.                                 |
| `active`         | BOOLEAN     | NOT NULL, DEFAULT TRUE              | 트리거 사용 여부입니다.                                          |
| `createdAt`      | TIMESTAMPTZ | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 트리거가 생성된 시각입니다.                                        |
| `updatedAt`      | TIMESTAMPTZ | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 트리거가 마지막으로 변경된 시각입니다.                                  |
| `opendAt`        | TIMESTAMPTZ | NULL                                | 사용자가 푸시 알림을 눌러 제안을 처음 연 시각입니다. 열지 않은 경우 `NULL`로 유지합니다. |

### 제약조건

```sql
UNIQUE (personal_save_id, content_place_id)
FOREIGN KEY (personal_save_id) REFERENCES personal_save(id) ON DELETE CASCADE
FOREIGN KEY (content_place_id) REFERENCES content_place(id) ON DELETE CASCADE
CHECK (radius_meters > 0)
```

동일한 개인 저장물과 장소 연결에는 하나의 트리거만 생성합니다.  
트리거를 생성할 때 `PersonalSave.contentId`와 `ContentPlace.contentId`가 같은지 애플리케이션 트랜잭션에서 검사합니다.  
이미 분석이 완료된 콘텐츠를 저장하면 연결된 `ContentPlace`를 기준으로 트리거를 생성하고, 저장 이후 분석이 완료되면 장소 검증과 `ContentPlace` 생성 후 트리거를 생성합니다.  
개인 저장물이나 `ContentPlace`가 삭제되면 연결된 트리거도 함께 삭제합니다.  
`active`는 서버에서 해당 트리거를 사용할지 나타내며, 실제 기기에 지오펜스가 등록되었는지는 의미하지 않습니다. 기기별 등록 상태는 `Device`와 트리거의 동기화 구조에서 별도로 관리합니다.  
지오펜스 반경의 기본값은 DB에 고정하지 않고 실제 위치 정확도 테스트 후 애플리케이션 설정으로 결정합니다.