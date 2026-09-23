## 개인 저장물 - PersonalSave

사용자가 콘텐츠를 저장한 관계를 관리합니다.
`PersonalSave`가 존재하면 저장된 상태이며, 저장을 취소하면 해당 데이터를 삭제합니다.
공용 후보 제공 여부는 `PersonalSave`에 저장하지 않고 사용자의 유효한 `MemberConsent`를 기준으로 판단합니다.

### 필드

| 필드 | 타입 | 제약조건 | 설명 |
| --- | --- | --- | --- |
| `id` | UUID | PK, NOT NULL | 개인 저장물 식별자입니다. |
| `memberId` | UUID | FK, NOT NULL | 콘텐츠를 저장한 사용자 식별자입니다. |
| `contentId` | UUID | FK, NOT NULL | 사용자가 저장한 콘텐츠 식별자입니다. |
| `savedAt` | TIMESTAMPTZ | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 사용자가 콘텐츠를 저장한 시각입니다. |

### 제약조건

```sql
UNIQUE (member_id, content_id)
FOREIGN KEY (member_id) REFERENCES member(id) ON DELETE CASCADE
FOREIGN KEY (content_id) REFERENCES content(id) ON DELETE CASCADE
```

동일한 사용자가 같은 콘텐츠를 동시에 저장하더라도 하나의 `PersonalSave`만 생성되도록 `(memberId, contentId)`에 UNIQUE 제약을 적용합니다.
중복 저장 요청은 오류로 처리하지 않고 기존 `PersonalSave`를 반환하여 멱등하게 처리합니다.
별도 조회 인덱스는 초기 설계에 포함하지 않고 실제 데이터와 실행계획을 측정한 뒤 추가합니다.

### 공용 후보 포함 조건

`PersonalSave` 자체에는 공용 제공 여부를 저장하지 않습니다.
해당 사용자의 `PUBLIC_CANDIDATE_CONTRIBUTION` 동의가 유효한 경우에만 저장된 콘텐츠를 공용 후보 조회에 포함합니다.
사용자가 동의를 철회하면 개인 저장물은 유지하지만 공용 후보 조회에서는 즉시 제외합니다.
사용자가 저장을 취소하면 해당 `PersonalSave`와 연결된 `Trigger`도 함께 삭제합니다.