## 꺼내기 실행 - RecallExecution

트리거로 시작된 꺼내기 에이전트의 실행 상태와 최종 결과를 관리합니다.  
실행 실패와 유효한 제안을 만들지 못한 `NO_ACTION`을 구분하며, 최종 결과가 `PROPOSE`이면 제안 유형과 대상을 함께 저장합니다.

### 필드

| 필드                | 타입          | 제약조건                                | 설명                                                                        |
| ----------------- | ----------- | ----------------------------------- | ------------------------------------------------------------------------- |
| `id`              | UUID        | PK, NOT NULL                        | 꺼내기 실행 식별자입니다.                                                            |
| `triggerId`       | UUID        | FK, NOT NULL                        | 실행을 시작한 트리거 식별자입니다.                                                       |
| `triggerEventId`  | UUID        | NOT NULL, UNIQUE                    | 동일한 지오펜스 이벤트의 중복 처리를 방지하는 식별자입니다.                                         |
| `status`          | VARCHAR(20) | NOT NULL, CHECK                     | 실행 상태이며 `PENDING`, `RUNNING`, `COMPLETED`, `FAILED`, `TIMED_OUT` 중 하나입니다. |
| `result`          | VARCHAR(20) | NULL, CHECK                         | 정상 완료된 실행의 결과이며 `PROPOSE` 또는 `NO_ACTION`입니다.                              |
| `proposalType`    | VARCHAR(30) | NULL, CHECK                         | 제안 유형이며 `PLACE`, `COURSE`, `CONTENT` 중 하나입니다.                             |
| `proposalPayload` | JSONB       | NULL                                | 최종 제안에 포함된 콘텐츠와 장소 식별자를 저장합니다.                                            |
| `decisionSummary` | TEXT        | NULL                                | 최종적으로 제안하거나 침묵한 이유를 저장합니다.                                                |
| `failureCode`     | VARCHAR(50) | NULL                                | 실행 실패나 시간 초과의 원인을 분류합니다.                                                  |
| `agentVersion`    | VARCHAR(50) | NOT NULL                            | 실행에 사용한 에이전트 구성 버전입니다.                                                    |
| `eventOccurredAt` | TIMESTAMPTZ | NOT NULL                            | 지오펜스 이벤트가 발생한 시각입니다.                                                      |
| `startedAt`       | TIMESTAMPTZ | NULL                                | 에이전트 실행을 시작한 시각입니다.                                                       |
| `finishedAt`      | TIMESTAMPTZ | NULL                                | 실행이 종료된 시각입니다.                                                            |
| `createdAt`       | TIMESTAMPTZ | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 실행 데이터가 생성된 시각입니다.                                                        |
| `updatedAt`       | TIMESTAMPTZ | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 실행 상태가 마지막으로 변경된 시각입니다.                                                   |

### 제약조건

```sql
FOREIGN KEY (trigger_id) REFERENCES trigger(id) ON DELETE CASCADE
CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'TIMED_OUT'))
CHECK (result IS NULL OR result IN ('PROPOSE', 'NO_ACTION'))
CHECK (proposal_type IS NULL OR proposal_type IN ('PLACE', 'COURSE', 'CONTENT'))
```

`COMPLETED` 상태에서만 `result`를 가지며, `PROPOSE`일 때만 `proposalType`과 `proposalPayload`를 저장합니다.  
`FAILED`와 `TIMED_OUT`은 에이전트 실행 자체가 정상적으로 끝나지 않은 상태이므로 `NO_ACTION`으로 기록하지 않습니다.  
현재 위치의 정확한 좌표, 원본 프롬프트와 모델의 원시 추론 과정은 저장하지 않습니다.

## 꺼내기 실행 기록 - RecallExecutionTrace

꺼내기 실행 중 검토한 후보, 도구 호출과 결과 및 계획 변경을 순서대로 기록합니다.  
원시 추론 과정 대신 어떤 정보를 확인했고 그 결과 판단이 어떻게 변경되었는지 구조화하여 저장합니다.

### 필드

| 필드            | 타입          | 제약조건                                | 설명                                   |
| ------------- | ----------- | ----------------------------------- | ------------------------------------ |
| `id`          | UUID        | PK, NOT NULL                        | 실행 기록 식별자입니다.                        |
| `executionId` | UUID        | FK, NOT NULL                        | 꺼내기 실행 식별자입니다.                       |
| `sequence`    | INTEGER     | NOT NULL, CHECK                     | 실행 내 기록 순서입니다.                       |
| `type`        | VARCHAR(30) | NOT NULL, CHECK                     | 기록 유형입니다.                            |
| `payload`     | JSONB       | NOT NULL                            | 후보 식별자, 도구명, 처리 결과와 판단 변경 이유를 저장합니다. |
| `createdAt`   | TIMESTAMPTZ | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 기록이 생성된 시각입니다.                       |

### 제약조건

```sql
UNIQUE (execution_id, sequence)
FOREIGN KEY (execution_id) REFERENCES recall_execution(id) ON DELETE CASCADE
CHECK (sequence >= 1)
CHECK (type IN ('CANDIDATE_REVIEW', 'TOOL_CALL', 'TOOL_RESULT', 'DECISION_CHANGE'))
```

실행 기록은 순서대로 추가하며 기존 기록을 수정하지 않습니다.  
외부 API의 원본 응답이나 현재 위치 좌표는 저장하지 않고, 실행 재현과 장애 분석에 필요한 결과만 정리하여 저장합니다.