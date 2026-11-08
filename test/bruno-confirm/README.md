# 장소 후보 답변 API 테스트

Bruno에서 이 폴더를 별도 컬렉션으로 열고 `local` 환경을 선택한 뒤 전체 Runner를 실행한다.
기존 `test/bruno/07-save-confirm-flow`는 그대로 두고, 자동 폴링·권한·잘못된 입력·반영 결과 검증을 확장한 컬렉션이다.
기본 CI에는 포함하지 않는다. 실제 분석이나 카카오 장소 검증에 외부 API 비용이 발생할 수 있다.

## 환경 설정

| 변수 | 의미 |
| --- | --- |
| `baseUrl` | 마지막 `/` 없이 입력. 기본값 `https://remembrall-api.duckdns.org` |
| `shortsUrl` | 미응답 장소 후보가 남은 테스트 영상. 기본값은 기존 테스트 영상 `VpRAN6EG9pQ` |
| `confirmAction` | `validate`: 오류·권한과 변경 없음 / `select`: 실제 장소 선택 / `reject`: 후보 중에 없음 |
| `candidateId` | 답할 후보 ID. 비우면 응답에서 첫 미응답 후보 선택 |
| `selectedOptionId` | 선택지 ID. 비우면 해당 후보의 첫 선택지 사용 |
| `pollIntervalMs` | 분석 조회 간격. 기본 5초 |
| `analysisTimeoutMs` | 분석 최대 대기. 기본 5분 |

처음에는 기본값 `validate`로 실행한다. 새 회원 A·B가 같은 영상을 각각 저장한 다음, 미응답 후보에 대한 오류 요청이 기존 상세를 바꾸지 않는지 확인하고 개인 저장물 삭제·로그아웃으로 종료한다.
후보가 없거나 지정한 후보/선택지를 찾을 수 없으면 준비 단계가 실패한다. 통과로 처리하지 않으며 이후 답변 단계는 건너뛰고 정리를 시도한다.
분석 폴링과 분기는 전체 Runner에서 작동한다. 개별 요청 실행으로는 준비·분기·정리가 자동 연결되지 않는다.

## 실제 답변 모드

`select`와 `reject`는 반드시 소모해도 되는 테스트 데이터에서 실행한다.
후보와 확정 장소는 개인 저장물에 종속되지 않고 **공용 콘텐츠에 연결**되어 같은 영상을 저장한 다른 회원에게도 반영된다.
개인 저장물을 삭제하거나 새 테스트 회원으로 로그인해도 답한 후보는 복원되지 않는다.
같은 후보로 실제 답변 모드를 반복하면 준비 단계에서 실패한다. 다음 미응답 후보 또는 다른 테스트 영상을 지정한다.
이 컬렉션은 DB 후보 생성·상태 초기화·기존 답변 되돌리기를 수행하지 않는다.

- `select`: 선택지 ID 제출 → 204 → 대상 후보만 제거 → 선택한 이름·주소·좌표의 장소 반영 → A·B 지오펜스 반영.
- `reject`: `selectedOptionId: null` 제출 → 204 → 대상 후보만 제거 → 기존 장소와 지오펜스 유지.
- 두 모드 모두: 같은 후보 재제출 404 → 상세 그대로 유지 → A 저장 삭제 시 A 지오펜스만 제거 → B 장소 유지 → B 저장 삭제 및 로그아웃.

`null`은 정상적인 “후보 중에 없음” 답변이다. 현재 구현에서는 `{}`도 필드가 null로 읽혀 후보를 닫을 수 있으므로 잘못된 입력 테스트에 넣지 않는다.
실제 카카오 검증이 실패하면 404 또는 502가 발생할 수 있으며, 기대값 204를 완화하지 않는다.
기존 장소 재사용이 가능하므로 장소 수가 무조건 1 증가한다고 가정하지 않는다. 선택한 장소의 이름·주소·좌표와 기존 장소 보존을 함께 검사한다.

## 검증 항목

36개 요청 파일이 있고, `validate`에서는 실제 답변 후 시나리오를 건너뛴다.

- 인증: 세션 없음·잘못된 세션 401.
- 소유권: B도 같은 영상을 저장했더라도 A의 개인 저장 ID로 답하면 404.
- 없는 저장물·후보 404, 잘못된 저장 UUID 400.
- 없는 선택지·빈 선택지·잘못된 JSON 400 및 API 오류 코드 검증.
- 오류 요청 후 후보·장소·상세 변경 없음.
- 실제 답변, 같은 콘텐츠의 다른 회원 반영, 중복 제출 및 삭제 후 지오펜스 반영.

지오펜스 비교는 Android 한도 이내인 99개 이하 장소 데이터가 전제다. 동시 답변 경쟁, 실제 OS 지오펜스 등록·푸시 수신은 검증하지 않는다.
회원과 공용 콘텐츠·분석·답변 결과는 남을 수 있다. 중간 취소나 네트워크 단절로 정리가 실행되지 않으면 Runner 로그에서 생성한 저장 요청을 확인해 해당 테스트 회원으로 삭제해야 한다.

## CLI

Bruno CLI 4.3.0을 설치한 환경에서 이 폴더로 이동해 실행한다.

```powershell
New-Item -ItemType Directory -Force reports
bru run --env local --noproxy --reporter-junit reports/validate.xml --reporter-skip-all-headers --reporter-skip-body
```

실제 답변은 전용 데이터 확인 후 아래처럼 실행한다. GUI에서는 `confirmAction`만 변경해도 된다.

```powershell
bru run --env local --env-var confirmAction=select --noproxy --reporter-junit reports/select.xml --reporter-skip-all-headers --reporter-skip-body
bru run --env local --env-var confirmAction=reject --noproxy --reporter-junit reports/reject.xml --reporter-skip-all-headers --reporter-skip-body
```

세션 토큰은 실행 중 변수에만 저장한다. 보고서 폴더는 Git에서 제외한다. `--verbose`나 토큰 출력 코드를 추가하지 않는다.
