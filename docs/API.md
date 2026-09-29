
| API                                                    | 요청                                                                       | 응답·처리                                                                                                                                                 |
| ------------------------------------------------------ | ------------------------------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------- |
| 기존 카카오 로그인·로그아웃                                        | 기존 명세 유지                                                                 | `Member`, `Device`와 DB 세션 처리                                                                                                                          |
| `PUT /api/devices/fcm-token`                           | FCM 토큰                                                                   | 현재 기기의 토큰 등록·갱신                                                                                                                                       |
| `DELETE /api/devices/fcm-token`                        | 없음                                                                       | 현재 기기의 FCM 토큰 해제                                                                                                                                      |
| `GET /api/me/consents`                                 | 없음                                                                       | 위치기반서비스·공용 후보 활용 동의 상태 반환                                                                                                                             |
| `PUT /api/me/consents/{consentType}`                   | 동의 여부                                                                    | 해당 동의 부여·철회                                                                                                                                           |
| `DELETE /api/me`                                       | 탈퇴 요청                                                                    | 회원 및 관련 데이터 삭제 처리                                                                                                                                     |
| `POST /api/personal-saves`                             | YouTube URL                                                              | `personalSaveId`, `contentId`, `analysisStatus` 반환                                                                                                    |
| `GET /api/personal-saves`                              | 목록 조회 조건                                                                 | 내 저장물의 제목·요약·분석 상태·연결 장소 등 반환                                                                                                                         |
| `GET /api/personal-saves/{personalSaveId}`             | 없음                                                                       | 저장물 상세와 연결된 콘텐츠·장소 반환                                                                                                                                 |
| `DELETE /api/personal-saves/{personalSaveId}`          | 없음                                                                       | 내 저장물과 해당 Trigger 삭제                                                                                                                                  |
| `GET /api/geofences?latitude=…&longitude=…&platform=…` | 현재 위치와 `IOS` 또는 `ANDROID`                                                | OS에 등록할 **지역·장소 지오펜스 목록** 반환. 각각 등록 ID·유형·좌표·반경을 포함하며, 장소에는 `placeId`, Android DWELL에는 지연 시간을 포함. 앱 시작·복귀, 저장물 변경, **지역 지오펜스 진입·이탈 시 재호출**하여 등록 목록 갱신 |
| `POST /api/location-events`                            | `placeId`, `triggerEventId`, `eventType`, `eventOccurredAt`, 가능하면 이벤트 위치 | **장소 지오펜스 발동 시에만 호출.** 서버가 세션 사용자와 `placeId`로 유효한 Trigger를 조회하고, 중복·실행 조건을 검사한 뒤 이벤트당 꺼내기 실행 한 건 생성. 제안이 나오면 이후 FCM으로 전달                              |
| `GET /api/notifications`                               | 목록 조회 조건                                                                 | 내 알림 목록 반환                                                                                                                                            |
| `GET /api/notifications/{id}`                          | 없음                                                                       | 제안 상세 반환                                                                                                                                              |
| `POST /api/notifications/{id}/open`                    | 없음                                                                       | `openedAt` 기록. 중복 호출은 같은 결과로 처리                                                                                                                       |
### 공통

- 응답은 래핑하지 않는다. 에러는 `{ code, message }`.
- 시각은 ISO-8601 UTC, ID는 UUID 문자열, enum은 이름 문자열.
- 목록은 커서 방식. `{ items, nextCursor }`, 다음 페이지가 없으면 `nextCursor` 는 null.

### 인증·기기·동의

**`PUT /api/devices/fcm-token`**

```
요청 { fcmToken }
응답 204
```

**`DELETE /api/devices/fcm-token`**

```
응답 204
```

**`GET /api/me/consents`**

```
[{ consentType, agreed, termsVersion, agreedAt, withdrawnAt }]
```

**`PUT /api/me/consents/{consentType}`**

```
요청 { agreed, termsVersion }
응답 { consentType, agreed, termsVersion, agreedAt, withdrawnAt }
```

서버는 `termsVersion` 이 해당 동의 유형의 유효한 버전인지 확인한 뒤 기록한다.

**`DELETE /api/me`**

```
응답 204
```

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
       analysisStatus, placeCount, savedAt }
```

**`GET /api/personal-saves/{personalSaveId}`**

```
{ personalSaveId, contentId, videoId, sourceUrl, title, summary,
  category, analysisStatus, analysisFailureCode, savedAt,
  places: [{ placeId, name, address, latitude, longitude, description }] }
```

장소가 없으면 빈 배열. `analysisFailureCode` 는 PARTIAL·FAILED일 때만.

### 지오펜스·이벤트

**`GET /api/geofences?latitude=…&longitude=…&platform=…`**

```
{ regions: [{ geofenceId, latitude, longitude, radiusMeters }],
  places:  [{ geofenceId, placeId, latitude, longitude, radiusMeters, dwellDelayMs }] }
```

`dwellDelayMs` 는 Android만 채우고 iOS는 null. 같은 대상은 재조회해도 같은 `geofenceId` 를 반환한다.

**`POST /api/location-events`**

```
요청 { placeId, triggerEventId, eventType, eventOccurredAt,
       location: { latitude, longitude, accuracyMeters, locatedAt } }
응답 202
```

### 알림

**`GET /api/notifications`**

```
항목 { notificationId, title, body, proposalType, sentAt, openedAt }
```

**`GET /api/notifications/{notificationId}`**

```
{ notificationId, title, body, proposalType, sentAt, openedAt,
  items: [{ contentId, source, personalSaveId, title, summary, place }] }
```

- `source` 는 `PERSONAL` 또는 `PUBLIC`.
- `personalSaveId` 는 `PERSONAL` 일 때만 채운다. 공용 후보에는 다른 사용자의 저장 정보를 포함하지 않는다.
- `place` 는 `{ placeId, name, address, latitude, longitude }`, 장소가 없는 콘텐츠면 null.
- PLACE는 항목 하나, CONTENT는 `place` 가 null인 항목 하나, COURSE는 방문 순서대로 여러 개.

**`POST /api/notifications/{notificationId}/open`**

```
응답 204
```

이미 열린 알림이면 기록된 `openedAt` 을 유지한다.
