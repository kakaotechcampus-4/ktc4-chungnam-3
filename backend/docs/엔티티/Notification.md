## 알림 - Notification

사용자에게 전달할 제안 내용과 전송 결과를 관리합니다.  
`RecallExecution`의 결과가 `PROPOSE`인 경우에만 생성하며, 하나의 실행에는 하나의 알림만 생성합니다.  
사용자당 하나의 활성 기기만 허용하므로 별도의 기기별 전송 엔티티는 두지 않습니다.

### 필드

|필드|타입|제약조건|설명|
|---|---|---|---|
|`id`|UUID|PK, NOT NULL|알림 식별자입니다.|
|`executionId`|UUID|FK, NOT NULL, UNIQUE|알림을 생성한 꺼내기 실행 식별자입니다.|
|`memberId`|UUID|FK, NOT NULL|알림을 받을 사용자 식별자입니다.|
|`title`|VARCHAR(200)|NOT NULL|사용자에게 표시할 알림 제목입니다.|
|`body`|TEXT|NOT NULL|사용자에게 표시할 알림 내용입니다.|
|`status`|VARCHAR(20)|NOT NULL, CHECK|전송 상태이며 `PENDING`, `SENDING`, `SENT`, `FAILED` 중 하나입니다.|
|`providerMessageId`|VARCHAR(255)|NULL|푸시 제공자가 반환한 메시지 식별자입니다.|
|`failureCode`|VARCHAR(50)|NULL|전송 실패 원인을 분류합니다.|
|`sentAt`|TIMESTAMPTZ|NULL|푸시 제공자가 전송 요청을 정상 접수한 시각입니다.|
|`createdAt`|TIMESTAMPTZ|NOT NULL, DEFAULT CURRENT_TIMESTAMP|알림이 생성된 시각입니다.|
|`updatedAt`|TIMESTAMPTZ|NOT NULL, DEFAULT CURRENT_TIMESTAMP|알림 상태가 마지막으로 변경된 시각입니다.|

### 제약조건

```sql
FOREIGN KEY (execution_id) REFERENCES recall_execution(id) ON DELETE CASCADE
FOREIGN KEY (member_id) REFERENCES member(id) ON DELETE CASCADE
CHECK (status IN ('PENDING', 'SENDING', 'SENT', 'FAILED'))
CHECK (
    (status = 'SENT' AND sent_at IS NOT NULL)
    OR
    (status <> 'SENT' AND sent_at IS NULL)
)
CHECK (
    (status = 'FAILED' AND failure_code IS NOT NULL)
    OR
    (status <> 'FAILED' AND failure_code IS NULL)
)
```

`executionId`에 UNIQUE 제약을 적용하여 하나의 꺼내기 실행에서 알림이 중복 생성되지 않도록 합니다.

### 생성 및 전송 흐름

```text
RecallExecution이 PROPOSE 결정
→ RecallExecution 완료 처리와 Notification 생성
→ 트랜잭션 커밋
→ Notification을 PENDING에서 SENDING으로 변경
→ 사용자의 활성 Device에서 푸시 토큰 조회
→ 푸시 전송
→ 성공하면 SENT, 실패하면 FAILED
```

`RecallExecution`을 `COMPLETED`와 `PROPOSE`로 변경하는 트랜잭션에서 `PENDING` 상태의 알림을 함께 생성합니다.  
푸시 전송은 해당 트랜잭션이 커밋된 이후 실행하여 외부 API 호출 중에는 DB 트랜잭션을 유지하지 않습니다.  
전송 전 다음 조건부 변경에 성공한 요청만 실제 푸시를 전송합니다.

```sql
UPDATE notification
SET status = 'SENDING',
    updated_at = CURRENT_TIMESTAMP
WHERE id = :notificationId
  AND status = 'PENDING';
```

영향받은 행이 없는 경우 이미 다른 요청이 처리 중이거나 처리가 끝난 알림이므로 전송하지 않습니다.  
활성 기기가 없거나 푸시 제공자 호출에 실패하면 `FAILED`로 변경하고 실패 원인을 기록합니다.  
푸시 제공자가 요청을 정상 접수하면 `SENT`로 변경하고 `providerMessageId`와 `sentAt`을 저장합니다.  
`SENT`는 푸시 제공자가 전송 요청을 접수했다는 의미이며 사용자가 실제로 알림을 확인했다는 의미는 아닙니다.  
최근 알림 여부는 해당 사용자의 `SENT` 알림과 `sentAt`을 기준으로 판단합니다.  
푸시 토큰은 `Notification`에 저장하지 않고 전송 시점에 활성 `Device`에서 조회합니다.