## 장소 - Place

검증이 완료되고 지오펜스에 사용할 좌표가 확보된 실제 장소를 관리합니다.  
카카오 로컬 API는 장소 존재 여부를 검증하는 용도로만 사용하며, 카카오가 반환한 장소명, 주소, 좌표와 카테고리는 저장하지 않습니다.  
저장되는 장소명, 주소와 좌표는 영구 저장이 허용된 LocationIQ 결과를 기준으로 생성합니다.

### 생성 흐름

```text
Gemini 장소 후보 추출
→ 카카오 로컬 API로 실제 장소와 지점 확인
→ 동일한 카카오 장소 ID의 Place가 있으면 기존 Place 재사용
→ 존재하지 않으면 LocationIQ로 좌표 검색
→ 카카오와 LocationIQ 결과가 같은 장소라고 판단되면 Place 생성
→ 결과가 모호하거나 일치하지 않으면 Place와 Trigger를 생성하지 않고 실패 기록
```

### 필드

| 필드                     | 타입               | 제약조건                                | 설명                                                |
| ---------------------- | ---------------- | ----------------------------------- | ------------------------------------------------- |
| `id`                   | UUID             | PK, NOT NULL                        | 서비스 내부 장소 식별자입니다.                                 |
| `name`                 | VARCHAR(255)     | NOT NULL                            | LocationIQ 결과를 기준으로 정규화한 장소명입니다.                  |
| `address`              | VARCHAR(500)     | NULL                                | LocationIQ에서 확인한 주소입니다.                           |
| `latitude`             | DOUBLE PRECISION | NOT NULL, CHECK                     | LocationIQ에서 확인한 위도입니다.                           |
| `longitude`            | DOUBLE PRECISION | NOT NULL, CHECK                     | LocationIQ에서 확인한 경도입니다.                           |
| `geocodingProvider`    | VARCHAR(20)      | NOT NULL, CHECK                     | 좌표를 제공한 서비스이며 현재는 `LOCATIONIQ`만 허용합니다.            |
| `geocodingPlaceId`     | VARCHAR(100)     | NOT NULL                            | LocationIQ가 반환한 장소 식별자입니다. 서비스 내부 식별자로 사용하지 않습니다. |
| `verificationProvider` | VARCHAR(20)      | NOT NULL, CHECK                     | 장소 검증에 사용한 서비스이며 현재는 `KAKAO`만 허용합니다.              |
| `verificationPlaceId`  | VARCHAR(100)     | NOT NULL                            | 저장이 허용된 카카오 장소 식별자입니다.                            |
| `verifiedAt`           | TIMESTAMPTZ      | NOT NULL                            | 두 제공자의 결과를 비교해 장소 검증을 완료한 시각입니다.                  |
| `createdAt`            | TIMESTAMPTZ      | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 장소가 생성된 시각입니다.                                    |
| `updatedAt`            | TIMESTAMPTZ      | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 장소 정보가 마지막으로 변경된 시각입니다.                           |

### 제약조건

```sql
UNIQUE (verification_provider, verification_place_id)
CHECK (geocoding_provider = 'LOCATIONIQ')
CHECK (verification_provider = 'KAKAO')
CHECK (latitude BETWEEN -90 AND 90)
CHECK (longitude BETWEEN -180 AND 180)
```

같은 카카오 장소가 여러 콘텐츠에서 검증되더라도 기존 `Place`를 재사용합니다.  
서비스 내부 장소 식별자는 자체 UUID를 사용하며 외부 제공자의 식별자는 검증과 중복 방지를 위한 보조 정보로만 사용합니다.  
좌표는 장소 식별 기준으로 사용하지 않으며 UNIQUE 제약을 적용하지 않습니다. 같은 건물에 있는 여러 장소가 동일한 좌표를 가질 수 있기 때문입니다.  
LocationIQ의 전체 요청과 응답을 검색 캐시로 저장하지 않고, 최종 선택된 장소 정보만 `Place` 도메인 데이터로 정규화하여 저장합니다.  
카카오 응답의 장소명, 주소, 좌표, 카테고리와 원본 응답은 `Place`와 `PlaceCandidateLog` 어디에도 저장하지 않습니다.  
검증 기록에는 검색 질의, 카카오 장소 ID, 성공 여부와 실패 이유만 남깁니다.