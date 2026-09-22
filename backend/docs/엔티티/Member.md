## 사용자 - Member

서비스에 로그인한 사용자의 인증 식별 정보와 데이터 소유권을 관리합니다.
카카오 로그인을 기준으로 사용자를 식별하며, 개인 저장물을 직접 소유합니다. 트리거와 꺼내기 실행 및 알림은 개인 저장물을 통해 사용자와 연결됩니다.

### 필드

| 필드               | 타입           | 제약조건            | 설명                            |
| ---------------- | ------------ | --------------- | ----------------------------- |
| `id`             | UUID         | PK, NOT NULL    | 서비스 내부 사용자 식별자                |
| `authProvider`   | VARCHAR(20)  | NOT NULL, CHECK | 로그인 제공자이며 현재는 `KAKAO`만 허용합니다. |
| `providerUserId` | VARCHAR(100) | NOT NULL        | 로그인 제공자가 발급한 사용자 식별자입니다.      |
| `createdAt`      | TIMESTAMPTZ  | NOT NULL        | 회원이 최초 생성된 시각입니다.             |

### 제약조건

```sql
UNIQUE (auth_provider, provider_user_id)
CHECK (auth_provider IN ('KAKAO'))

