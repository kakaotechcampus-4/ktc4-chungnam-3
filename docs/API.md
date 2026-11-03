# API 명세

현재 컨트롤러·DTO 기준이다. 운영 API는 아래와 같고, 개발 전용 API는 마지막 절에 구분한다.

| API | 요청 | 응답·처리 |
|---|---|---|
| `POST /api/auth/kakao` | `kakaoAccessToken` | 200, `sessionToken`, `sessionExpiresAt` |
| `POST /api/auth/guest` | UUID 형식의 `installationId` | 200, `sessionToken`, `sessionExpiresAt` |
| `POST /api/auth/logout` | 본문 없음 | 204, 현재 세션 해제 |
| `PUT /api/devices/fcm-token` | `fcmToken` | 204, 현재 기기 토큰 등록·갱신 |
| `GET /api/me/consents` | 없음 | 200, 두 동의 유형의 상태 배열 |
| `PUT /api/me/consents/{consentType}` | `agreed`, `termsVersion` | 200, 변경한 동의 상태 |
| `POST /api/personal-saves` | `url` | 신규 201 / 기존 200, `personalSaveId`, `contentId`, `analysisStatus`, `savedAt` |
| `GET /api/personal-saves` | 쿼리 `size`, `cursor` | 200, `items`, `nextCursor`; 항목 필드는 아래 저장물 절 참조 |
| `GET /api/personal-saves/{personalSaveId}` | 없음 | 200, 저장물 상세·`places`·`confirmCandidates` |
| `DELETE /api/personal-saves/{personalSaveId}` | 없음 | 204, 내 저장물 삭제; 같은 장소로 이어지는 다른 저장물이 없을 때만 Trigger 정리 |
| `GET /api/personal-saves/places` | 없음 | 200, 내 장소와 출처 `personalSaveId` 배열, 페이지네이션 없음 |
| `POST /api/personal-saves/{personalSaveId}/places/{candidateId}/confirm` | `selectedOptionId` | 204, 되묻기 답변 반영 |
| `POST /api/contents` | `youtubeUrl` | 200, 기존 동기 저장·분석 API |
| `GET /api/geofences` | 쿼리 `latitude`, `longitude`, `platform` | 200, `regions`, `resync`, `places` |
| `POST /api/location-events` | `placeId`, `triggerEventId`, `eventType`, `eventOccurredAt`, 선택 `location` | 정상 요청은 처리·게이트 결과와 무관하게 202, 본문 없음 |
| `GET /api/notifications/{notificationId}` | 없음 | 200, 내 알림 상세 |
| `POST /api/notifications/{notificationId}/open` | 없음 | 204, 최초 `openedAt` 기록 |

<<<<<<< HEAD
| API                                                    | 요청                                                                       | 응답·처리                                                                                                                                                 |
| ------------------------------------------------------ | ------------------------------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------- |
| 기존 카카오 로그인·로그아웃                                        | 기존 명세 유지                                                                 | `Member`, `Device`와 DB 세션 처리                                                                                                                          |
| `PUT /api/devices/fcm-token`                           | FCM 토큰                                                                   | 현재 기기의 토큰 등록·갱신                                                                                                                                       |
| `DELETE /api/devices/fcm-token`                        | 없음                                                                       | 현재 기기의 FCM 토큰 해제                                                                                                                                      |
| `GET /api/me/consents`                                 | 없음                                                                       | 위치기반서비스·공용 후보 활용 동의 상태 반환                                                                                                                             |
| `PUT /api/me/consents/{consentType}`                   | 동의 여부                                                                    | 해당 동의 부여·철회                                                                                                                                           |
| `DELETE /api/me`                                       | 탈퇴 요청                                                                    | 회원 및 관련 데이터 삭제 처리                                                                                                                                     |
| `POST /api/personal-saves`                             | YouTube URL                                                              | `personalSaveId`, `contentId`, `analysisStatus` 반환                                                                                                    |
| `GET /api/personal-saves`                              | 목록 조회 조건                                                                 | 내 저장물의 제목·요약·분석 상태·실패 코드·대표 장소명·연결 장소 수·미답변 되묻기 수 등 반환                                                                                                   |
| `GET /api/personal-saves/{personalSaveId}`             | 없음                                                                       | 저장물 상세와 연결된 콘텐츠·장소·답변 대기 중인 되묻기 후보(`confirmCandidates`) 반환                                                                                             |
| `DELETE /api/personal-saves/{personalSaveId}`          | 없음                                                                       | 내 저장물과 해당 Trigger 삭제                                                                                                                                  |
| `GET /api/personal-saves/places`                       | 없음                                                                       | 내가 저장한 모든 콘텐츠의 장소를 좌표·이름·주소·출처 저장물(`personalSaveId`)·`videoId`·`savedAt`과 함께 한 번에 반환 (근처 탭·지도용, 페이지네이션 없음)                                                                     |
| `POST /api/personal-saves/{personalSaveId}/places/{candidateId}/confirm` | `selectedOptionId`                                      | 되묻기 후보에 대한 답변 반영                                                                                                                                      |
| `GET /api/geofences?latitude=…&longitude=…&platform=…` | 현재 위치와 `IOS` 또는 `ANDROID`                                                | OS에 등록할 **지역·장소 지오펜스 목록** 반환. 각각 등록 ID·유형·좌표·반경을 포함하며, 장소에는 `placeId`, Android DWELL에는 지연 시간을 포함. 앱 시작·복귀, 저장물 변경, **지역 지오펜스 진입·이탈 시 재호출**하여 등록 목록 갱신 |
| `POST /api/location-events`                            | `placeId`, `triggerEventId`, `eventType`, `eventOccurredAt`, 가능하면 이벤트 위치 | **장소 지오펜스 발동 시에만 호출.** 서버가 세션 사용자와 `placeId`로 유효한 Trigger를 조회하고, 중복·실행 조건을 검사한 뒤 이벤트당 꺼내기 실행 한 건 생성. 제안이 나오면 이후 FCM으로 전달                              |
| `GET /api/notifications`                               | 목록 조회 조건                                                                 | 내 알림 목록 반환                                                                                                                                            |
| `GET /api/notifications/{id}`                          | 없음                                                                       | 제안 상세 반환                                                                                                                                              |
| `POST /api/notifications/{id}/open`                    | 없음                                                                       | `openedAt` 기록. 중복 호출은 같은 결과로 처리                                                                                                                       |
=======
>>>>>>> a6924f38dc1b60aa4e7a377f9bd7ba57fb13522a
### 공통

- 응답은 래핑하지 않는다. 처리된 API 오류는 `{ code, message }` 형식이다. 별도 핸들러가 없는 500은 Spring Boot 기본 오류 응답 경로를 사용한다.
- 카카오·게스트 로그인 외 운영 API는 `Authorization: Bearer {sessionToken}` 인증이 필요하다. 세션 누락·만료는 401 / `INVALID_SESSION`.
- 시각은 ISO-8601 Instant(일반적으로 UTC), DB 식별자는 UUID 문자열, enum은 이름 문자열이다. 되묻기의 `candidateId`·`selectedOptionId`는 UUID가 아닌 문자열이다.
- 커서 페이지네이션은 `GET /api/personal-saves`에 적용한다. 응답은 `{ items, nextCursor }`, 다음 페이지가 없으면 `nextCursor`는 null. `size` 기본 20·최대 100(설정으로 변경 가능), 커서는 앱이 해석하지 않고 그대로 전달한다.
- 잘못된 요청은 400 / `INVALID_REQUEST`, 리소스 없음·다른 회원 소유 리소스는 해당 API에서 404 / `NOT_FOUND`, 지원하지 않는 메서드는 405 / `METHOD_NOT_ALLOWED`이며 `Allow` 헤더를 보존한다.
- 현재 코드에 없는 회원 탈퇴·FCM 토큰 삭제·알림 목록 API는 이 명세에 포함하지 않는다.

### 인증·기기·동의

**`POST /api/auth/kakao`**

```
요청 { kakaoAccessToken }
응답 200 { sessionToken, sessionExpiresAt }
```

유효하지 않은 카카오 토큰은 401 / `INVALID_KAKAO_TOKEN`, 카카오 조회 장애는 502 / `KAKAO_API_ERROR`.

**`POST /api/auth/guest`**

```
요청 { installationId }
응답 200 { sessionToken, sessionExpiresAt }
```

`installationId`는 비어 있지 않은 UUID 형식 문자열이다. 같은 installationId는 같은 회원에게 새 세션을 발급하며 이전 세션은 교체된다.

**`POST /api/auth/logout`**

```
요청 본문 없음
응답 204
```

**`PUT /api/devices/fcm-token`**

```
요청 { fcmToken }
응답 204
```

`fcmToken`은 비어 있지 않은 최대 512자 문자열이다. 토큰 등록 충돌은 409 / `FCM_TOKEN_CONFLICT`.

**`GET /api/me/consents`**

```
응답 200 [{ consentType, agreed, termsVersion, currentTermsVersion, agreedAt, withdrawnAt }]
```

**`PUT /api/me/consents/{consentType}`**

```
요청 { agreed, termsVersion }
응답 200 { consentType, agreed, termsVersion, currentTermsVersion, agreedAt, withdrawnAt }
```

동의 유형은 `PUBLIC_CANDIDATE_CONTRIBUTION`, `LOCATION_BASED_SERVICE`. 동의 요청의 termsVersion은 현재 유효한 버전이어야 하며 철회 요청에는 필요 없다. currentTermsVersion은 동의 이력 유무·철회 여부와 관계없이 현재 설정값이다.

### 저장물

**`POST /api/personal-saves`**

```
요청 { url }
응답 { personalSaveId, contentId, analysisStatus, savedAt }
```

새로 저장하면 201, 이미 저장한 영상이면 200에 같은 본문.

**`GET /api/personal-saves`**

```
항목 { personalSaveId, contentId, videoId, title, summary,
       analysisStatus, analysisFailureCode, placeCount, representativePlaceName,
       pendingConfirmCount, savedAt }
```

`analysisFailureCode`는 PARTIAL·FAILED일 때만. `representativePlaceName`은 연결된 장소가 없으면 null.

**`GET /api/personal-saves/{personalSaveId}`**

```
{ personalSaveId, contentId, videoId, sourceUrl, title, summary,
  category, analysisStatus, analysisFailureCode, savedAt,
  places: [{ placeId, name, address, latitude, longitude, description }],
  confirmCandidates: [{ candidateId, question,
                         options: [{ candidateId, name, branchName, address, lat, lng,
                                     geocodingProvider, geocodingPlaceId, verificationPlaceId }] }] }
```

장소·되묻기 후보가 없으면 각각 빈 배열. `analysisFailureCode` 는 PARTIAL·FAILED일 때만.
`confirmCandidates`는 아직 답변하지 않은(PENDING) 후보만 포함한다. 후보의 `candidateId`는 아래 답변
제출 엔드포인트의 `{candidateId}`로, `options[].candidateId`는 답변 제출 시 `selectedOptionId`로
그대로 쓴다(장소 응답의 `placeId`와는 다른 값이니 혼동 주의).

**`GET /api/personal-saves/places`**

```
[{ placeId, name, address, latitude, longitude, personalSaveId, videoId, savedAt }]
```

내가 저장한 모든 콘텐츠의 장소를 한 번에 반환한다. 페이지네이션 없음.

**`POST /api/personal-saves/{personalSaveId}/places/{candidateId}/confirm`**

```
요청 { selectedOptionId }
응답 204
```

로그인한 회원이 해당 저장물의 주인이 아니면 404. `selectedOptionId`는 그 후보의 `options[].candidateId`
중 하나.

### 지오펜스·이벤트

**`GET /api/geofences?latitude=…&longitude=…&platform=…`**

```
응답 200 {
  regions: [],
  resync: { geofenceId, latitude, longitude, radiusMeters } 또는 null,
  places: [{ geofenceId, placeId, latitude, longitude, radiusMeters }]
}
```

- latitude는 -90~90, longitude는 -180~180, platform은 `IOS` 또는 `ANDROID`이며 모두 필수다.
- regions는 항상 빈 배열이다. 장소 ID는 `place:{placeId}`, 재조회 ID는 `resync`이며 `dwellDelayMs` 필드는 없다.
- 내 Trigger 중 연결된 내 콘텐츠에 유효한 것이 하나 이상인 장소를 거리·placeId 순으로 고른다. 기본 한도는 iOS 19개, Android 99개이고 장소 반경 기본값은 150m다.
- resync 중심은 요청 위치이며 반경은 가장 먼 반환 장소 거리+여유(기본 500m), 최소 반경은 기본 3000m다.
- 장소가 없거나 활성 위치기반서비스 동의가 없으면 `{ regions: [], resync: null, places: [] }`.
- 이 게이트는 활성 동의 유무를 확인하며, 동의했던 termsVersion과 현재 약관 버전의 일치는 검사하지 않는다.

**`POST /api/location-events`**

```
요청 {
  placeId, triggerEventId, eventType, eventOccurredAt,
  location: { latitude, longitude, accuracyMeters, locatedAt }
}
응답 202, 본문 없음
```

- eventType은 `ENTER`, `DWELL`만 허용한다.
- location은 생략·null 가능하나 객체가 있으면 네 필드 모두 필수다. 위경도 범위는 위와 같고 accuracyMeters는 0 이상이다.
- 현재는 동기 처리다. 실행을 선점·커밋한 뒤 요청 스레드에서 에이전트와 필요 시 FCM 처리를 마치고 202를 반환한다. 202가 비동기 처리를 뜻하지 않는다. 에이전트 예외는 FAILED로 기록하고 202를 반환한다.
- 세션 회원·placeId의 Trigger가 없거나, 이벤트가 오래됐거나(기본 30분 초과), 활성 위치 동의가 없으면 실행하지 않고 202.
- 새 이벤트는 회원의 모든 실행 상태를 포함한 쿨다운(기본 2시간)을 적용한다. 약관 버전 일치 검사는 하지 않는다.
- 중복 키는 `(trigger_id, trigger_event_id)`. 같은 실행이 PENDING·RUNNING·COMPLETED면 재실행하지 않는다.
- FAILED·TIMED_OUT이면 같은 실행을 조건부 UPDATE로 PENDING으로 돌려 다시 실행한다. 같은 이벤트 재실행에는 회원 쿨다운만 건너뛰며 Trigger·원래 이벤트 시각·활성 위치 동의 조건은 유지한다.
- 추가 재실행 횟수는 retry_count에 기록하고 `recall.max-event-retries`(환경변수 `RECALL_MAX_EVENT_RETRIES`, 기본 1)로 제한한다. 기본값은 최초 시도 1회+재실행 1회다. 상한 초과도 202, 본문 없음.
- 재실행은 기존 execution_id·created_at을 유지하고 이전 결과·오류·시작/종료 시각을 비운다. Trace는 같은 실행의 seq 뒤에 붙으며 attempt=1은 최초, attempt=2는 첫 재실행이다.
- Android FCM 알림 채널은 `notification.android-channel-id`(환경변수 `NOTIFICATION_ANDROID_CHANNEL_ID`, 기본 `nearby`)다. 데이터 페이로드는 `notificationId` 하나다.

### 알림

**`GET /api/notifications/{notificationId}`**

```
응답 200 {
  notificationId, title, body, proposalType, sentAt, openedAt,
  items: [{ contentId, source, personalSaveId, title, summary, place }]
}
```

- source는 `PERSONAL` 또는 `PUBLIC`. personalSaveId는 PERSONAL일 때만 채우고 PUBLIC은 null이다.
- place는 `{ placeId, name, address, latitude, longitude }`, 연결 장소 참조가 없는 항목이면 null.
- items는 조회 시점에 콘텐츠·장소·연결·접근 조건을 다시 읽는다. 삭제 등으로 제외되어 빈 배열이거나 원래 COURSE보다 적어질 수 있다. 생성 시 형태를 조회 응답의 항목 수로 보장하지 않는다.
- 없거나 다른 회원의 알림은 404.

**`POST /api/notifications/{notificationId}/open`**

```
요청 본문 없음
응답 204
```

이미 열린 알림이면 기존 openedAt을 유지한다. 없거나 다른 회원의 알림은 404.

### 기존 동기 저장 API

**`POST /api/contents`**

```
요청 { youtubeUrl }
응답 200 {
  contentId, alreadyProcessed, status, summary,
  places: [{
    candidateId, decision,
    resolvedPlace: { candidateId, name, branchName, address, lat, lng,
                     geocodingProvider, geocodingPlaceId, verificationPlaceId } 또는 null,
    confirmRequest: { question, candidates: [{
      candidateId, name, branchName, address, lat, lng,
      geocodingProvider, geocodingPlaceId, verificationPlaceId
    }] } 또는 null
  }],
  failureMessage
}
```

현재 코드에 남아 있는 동기 API다. 분석을 선점하지 못하면 alreadyProcessed=true, status·summary·failureMessage=null, places=[]다. status는 `SUCCESS`, `PARTIAL`, `FAILED`; 장소 decision은 `RESOLVED`, `NEEDS_CONFIRMATION`, `NO_PLACE`, `PLACE_SEARCH_FAILED`, `PLACE_VERIFICATION_FAILED`다. 비동기 저장에는 POST /api/personal-saves를 사용한다.

### 개발·인프라 전용

아래 두 /dev API는 `e2e` 프로필에서만 등록되고 세션 인증이 필요하다. 운영 기본 프로필의 API가 아니다.

**`POST /dev/e2e/seed`**

```
요청 본문 없음
응답 200 {
  memberId, publicMemberId,
  origin: { placeId, name, address, latitude, longitude, description },
  contents: [{
    contentId, personalSaveId, memberId, videoId, title, publicContribution,
    analysisStatus, analysisClaimed, outcomeApplied, embeddingFilled,
    embeddingModel, savedAt,
    places: [{ placeId, name, address, latitude, longitude, description }]
  }],
  places: [{ placeId, name, address, latitude, longitude, description }]
}
```

**`GET /dev/e2e/executions/{executionId}/input`**

```
응답 200 {
  origin: { placeId, name, latitude, longitude, contents: [PersonalContent] },
  nearbyPersonalCandidates: [{ placeId, name, distanceMeters, contents: [PersonalContent] }],
  nearbyPublicCandidates: [{ placeId, name, distanceMeters, contents: [PublicContent] }],
  semanticCandidates: [{ contentId, source, personalSaveId, title, summary, category, savedAt }],
  situation: { localDateTime, dayOfWeek }
}
PersonalContent = { contentId, personalSaveId, title, summary, category, savedAt, description }
PublicContent = { contentId, title, summary, category, description }
```

semanticCandidates의 PUBLIC 항목은 personalSaveId·savedAt 필드를 생략한다. situation.localDateTime은 Asia/Seoul 현지 시각이며 오프셋이 없는 LocalDateTime이다. 내 실행이 아니거나 기록된 입력이 없으면 404.

**`GET /actuator/health`**: 인증 없이 호출하는 Spring Boot Actuator 기본 헬스 체크. 프로젝트 DTO가 아닌 Actuator 응답을 사용한다.

### 코드 대조 근거

경로는 `backend/Remembrall/src/main/java/com/ktc/chungnam3/remembrall/` 기준이다. 아래 표는 문서 변경·재검증의 근거이며 컨트롤러 코드는 수정하지 않았다.

| 엔드포인트·변경 | 컨트롤러 | 요청·응답 DTO 및 처리 근거 |
|---|---|---|
| 카카오·게스트 로그인·로그아웃 구체화 | `auth/controller/AuthController.java` | `auth/dto/KakaoLoginRequest.java`, `GuestLoginRequest.java`, `SessionResponse.java` |
| FCM 등록 유지, 삭제 API 제거 | `device/controller/DeviceController.java` | `device/dto/FcmTokenRequest.java`; 컨트롤러에 DELETE 없음 |
| 동의 응답 currentTermsVersion 보완 | `consent/controller/MemberConsentController.java` | `consent/dto/ConsentRequest.java`, `ConsentResponse.java` |
| 회원 탈퇴 API 제거 | 대응 컨트롤러 없음 | `auth/security/SecurityConfig.java`를 포함해 매핑 대조; DELETE /api/me 없음 |
| 저장물 6개 경로 대조 | `personalsave/controller/PersonalSaveController.java`, `save/ConfirmAnswerController.java` | `personalsave/dto/PersonalSaveRequest.java`, `PersonalSaveResponse.java`, `save/dto/ConfirmAnswerRequestDto.java`, `ResolvedPlaceDto.java`; 기존 저장물 상세 절은 유지 |
| 지오펜스 resync 추가·regions=[]·dwellDelayMs 제거 | `geofence/controller/GeofenceController.java` | `geofence/dto/GeofenceQuery.java`, `GeofenceResponse.java`, `geofence/service/GeofenceService.java` |
| 이벤트 동기·실패 재실행 설명 | `recall/controller/LocationEventController.java` | `recall/dto/LocationEventRequest.java`, `LocationEventType.java`, `recall/service/LocationEventService.java` |
| 알림 목록 제거·notificationId 통일·상세 항목 필터 설명 | `notification/controller/NotificationController.java` | `notification/dto/NotificationDetail.java`, `notification/service/NotificationService.java` |
| 기존 POST /api/contents 추가 | `save/SavePipelineController.java` | `save/dto/SaveContentRequestDto.java`, `SaveContentResponseDto.java`, `PlaceOutcomeDto.java`, `ConfirmRequestDto.java`, `ResolvedPlaceDto.java` |
| 프로필 한정 개발 API 추가 | `e2e/E2eSeedController.java` | `e2e/E2eSeedService.java`의 SeedResponse·ContentView·PlaceView, `recall/agent/RecallAgentInput.java` |
| 인프라 헬스 체크 구분 | Spring Boot Actuator 자동 등록 | `auth/security/SecurityConfig.java`, `build.gradle` Actuator 의존성 |
