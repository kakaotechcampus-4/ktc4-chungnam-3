## 트리거 - Trigger

사용자가 저장한 장소에서 꺼내기 실행을 촉발하는 연결입니다. **회원과 장소의 조합마다 하나만 생성합니다.** 같은 장소를 다룬 콘텐츠를 여러 개 저장해도 트리거는 하나이며, 공용 후보만으로는 생성하지 않습니다.
트리거는 어떤 저장물을 제안할지 결정하지 않습니다. 장소 이벤트가 발생하면 에이전트가 그 회원의 관련 저장물을 조회해 판단합니다.

### 필드

|필드|타입|제약조건|설명|
|---|---|---|---|
|`id`|UUID|PK, NOT NULL|트리거 식별자입니다.|
|`memberId`|UUID|FK, NOT NULL|장소를 저장한 회원의 식별자입니다.|
|`placeId`|UUID|FK, NOT NULL|이벤트를 발생시키는 장소의 식별자입니다.|
|`createdAt`|TIMESTAMPTZ|NOT NULL, DEFAULT CURRENT_TIMESTAMP|트리거가 생성된 시각입니다.|

### 제약조건

```
UNIQUE (member_id, place_id)
FOREIGN KEY (member_id) REFERENCES member(id) ON DELETE CASCADE
FOREIGN KEY (place_id) REFERENCES place(id) ON DELETE CASCADE
```

### 생성과 삭제

- 개인 저장물에 검증된 `ContentPlace`가 연결되면 해당 회원과 장소의 트리거를 생성합니다. 이미 있으면 추가로 생성하지 않습니다.
- 검증된 ContentPlace가 있는 콘텐츠를 다른 회원이 저장한 경우에도 그 회원의 트리거를 생성합니다.
- 저장물을 삭제해도 같은 회원이 해당 장소와 연결된 다른 저장물을 갖고 있다면 트리거를 유지합니다. 더는 연결된 저장물이 없을 때 삭제합니다.
- 위치 동의가 철회되면 서버는 해당 트리거의 이벤트를 처리하지 않으며, 앱은 등록한 지오펜스를 해제합니다.

앱은 장소당 지오펜스 하나를 등록합니다. 장소 이벤트가 오면 서버는 `memberId`와 `placeId`로 트리거 하나를 찾고, `RecallExecution`을 한 건 생성합니다.
지오펜스 반경은 등록 설정에서 관리합니다. 
