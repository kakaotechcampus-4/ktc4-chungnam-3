# 프론트가 필요로 하는 API 요구사항

프론트 화면·백그라운드 코드가 서버에 기대하는 데이터와 요청을 정리한 문서다.
기준은 `develop` `14e7ffe` 의 프론트 코드와 백엔드 코드다.

- 엔드포인트 URL·HTTP 메서드는 **제안**이다. 설계는 백엔드가 정한다.
- 필드 이름은 화면 기준이다. 백엔드에 이미 있는 필드와 대응되면 "백엔드 대응" 칸에 적는다.
- **표시값**은 서버가 원본 값(시각·거리 등)을 주고 프론트가 문자열로 만드는 것을 제안한다는 뜻이다.
- (추측) 은 코드에 근거가 없는 추정이다.
- 화면 번호(01~09)는 Figma Main 페이지 기준이다.

## 0. 요구사항이 나온 곳

| 위치 | 나온 것 |
|---|---|
| `src/shared/api/mock/` | 화면이 읽는 데이터 모양. 문자열로 합쳐진 표시값이 많아 원본 필드로 다시 나눴다 |
| `src/features/**` props 타입 | 목 데이터가 맞추는 실제 기준 (`NearbyCardProps`, `MemoryCardProps`, `AlbumCellProps`, `SourceHeaderProps`, `DetailSheetProps`, `SaveResultScreen` 내부 타입) |
| `src/features/**` 의 동작 없는 버튼 | 쓰기 요청 |
| `src/app/navigation/linking.ts` | 라우트 파라미터 `resultId`, `placeId` (전역 ID 필요) |
| `src/shared/external-links/`, `src/shared/storage/` | 좌표·원본 URL 필요, 토큰 로컬 저장 |
| `src/runtime/**` | 공유 제출, 지오펜스 목록·진입 보고, 알림 페이로드, 오프라인 재전송 |
| `src/domain/*.ts` | 미확정 앱 모델 (Content, Place, Proposal, Notification, ExecutionTrace) |
| `STRUCTURE.md` 미확정 표 | 결정이 필요한 질문 |

### 백엔드에 이미 있는 것 (`develop`)

- 인증 `AuthController`
  - `POST /api/auth/kakao` — `{ kakaoAccessToken }` → `TokenResponse`
  - `POST /api/auth/refresh` — `{ refreshToken }` → `TokenResponse`
  - `POST /api/auth/logout` — JWT → 204
  - `TokenResponse { accessToken, refreshToken, accessTokenExpiresAt, refreshTokenExpiresAt }`
  - 이 두 경로와 `/actuator/health` 를 뺀 모든 요청은 인증이 필요하다.
- `Device.fcmToken` 컬럼. 등록·갱신 API 는 없다.
- 저장 판정 DTO (Controller 는 없다)
  - `SavePipelineResultDto { status: PLACE_RESOLVED | NEEDS_CONFIRMATION | NO_PLACE, summary, place, confirm }`
  - `ConfirmRequestDto { question, candidates }`
  - `ResolvedPlaceDto { candidateId, name, branchName, address, lat, lng }`
  - 판정 규칙: 검색 결과 1건이면 확정, 2~3건이면 되묻기, 그 외는 NO_PLACE. 되묻기는 콘텐츠당 최대 1회.
- 추출 DTO `YouTubeContentExtractionResultDto` 와 enum `ExtractionStatus`, `EvidenceSource`, `FailureStage`
- 에러 응답 `ApiErrorResponse { code, message }`

## 1. 읽기: 화면별 필드

### 01 근처 · 다시 꺼낸 곳

출처: `features/proposal/ProposalScreen.tsx`, `components/NearbyCard.tsx`, `components/MemoryCard.tsx`, `shared/api/mock/nearby.ts`

| 필드 | 타입 | null | 예시 | 백엔드 대응 |
|---|---|---|---|---|
| area | string | ✗ | "대흥동" | 없음. 현재 위치의 동 이름. 역지오코딩 주체 미정 |
| items[].contentId | string | ✗ | — | 없음 (전역 ID 필요) |
| items[].placeId | string | ○ | "sungsimdang" | 없음. `candidateId` 는 전역 키로 쓸 수 없다 |
| items[].placeName | string | ✗ | "성심당 본점" | `ResolvedPlaceDto.name` + `branchName` |
| items[].savedAt | ISO datetime | ✗ | → "3주 전" (표시값) | 없음 |
| items[].walkMinutes 또는 distanceMeters | number | ○ | → "도보 9분", "지금 도보 4분" | 없음. 좌표를 주면 프론트가 계산하는 방법도 있다 |
| items[].category | enum | ○ | → "카페" | 없음 (enum 미정) |
| items[].summary | string | ✗ | "튀김소보로는 오전에 가면 줄이 짧다는 영상" | `SavePipelineResultDto.summary` 또는 추출 결과 `summary` |
| items[].tags | string[] | ✗ (빈 배열) | ["줄서는", "오전"] | 없음 (분위기 태그) |
| items[].thumbnailUrl | string | ○ | YouTube 썸네일 | 없음 (백엔드 내부에 videoId 는 있다) |
| items[].isNearNow | boolean | ✗ | NearbyCard(지금 가까운 곳) / MemoryCard 구분 | 없음 (추측: 제안 또는 지오펜스 진입 결과) |
| totalCount | number | ✗ | "잊고 있던 곳 N개를 찾았어요" | 없음 |

- 썸네일 바램 단계(fade)와 그룹("지금 가까운 곳 / 몇 주 전 / 몇 달 전")은 `savedAt` · `isNearNow` 로 프론트가 계산할 수 있다. (추측)
- 필터 칩 목록은 category enum 에서 만든다.

### 06 근처 · 빈 상태

출처: `features/proposal/components/NearbyEmpty.tsx`, `shared/api/mock/nearby.ts` (`nearbyEmptyMock`)

| 필드 | 타입 | null | 예시 | 백엔드 대응 |
|---|---|---|---|---|
| area | string | ✗ | "유성구 봉명동" | 없음 |
| nearest.areaName | string | ○ | "대흥동" | 없음 |
| nearest.distanceMeters | number | ○ | → "2.4km" | 없음 |
| nearest.thumbnailUrls | string[] | ✗ | 3장 | 없음 (추측: 가장 가까운 지역의 저장물 썸네일) |

- 저장물이 하나도 없으면 `nearest` 는 null 이다. 이 경우의 화면은 디자인에 없다.

### 07 전체 기억 · 시간순 앨범 / 07b 빈 상태

출처: `features/archive/ArchiveScreen.tsx`, `components/AlbumCell.tsx`, `shared/api/mock/archive.ts`

| 필드 | 타입 | null | 예시 | 백엔드 대응 |
|---|---|---|---|---|
| items[].contentId | string | ✗ | 저장 결과 모달 라우팅 (`resultId`) | 없음 |
| items[].placeId | string | ○ | 상세 라우팅 (`placeId`) | 없음 |
| items[].placeName | string | ○ | "성심당 본점" | `ResolvedPlaceDto.name` · `branchName` |
| items[].videoTitle | string | ○ | "대전 빵집 3곳" (확인 필요 셀) | 없음 (백엔드 내부 `VideoInfo.title`) |
| items[].status | enum | ○ | analyzing / needsConfirmation / noPlace / partial / failed | 일부 대응: `SavePipelineResultDto.Status`, `ExtractionStatus` |
| items[].savedAt | ISO | ✗ | 섹션 "이번 주 / 지난 몇 주 / 올해 봄" | 없음 |
| items[].thumbnailUrl | string | ○ | — | 없음 |
| totalCount | number | ✗ | "모두 N곳" | 없음. 페이지 단위 조회면 필수 |

- 셀 라벨은 `placeName` → 없으면 `videoTitle` → 둘 다 없으면 "방금 저장한 영상"(분석 중). (추측)
- 07b 는 `totalCount = 0` 일 때다. 읽을 필드가 없다.
- 상태 배지: `status` 가 있을 때만 표시한다. 라벨·색 매핑은 프론트 `shared/ui/Badge.tsx` 에 있다.

### 02~05 저장 결과 (확인 필요 · 분석 중 · 장소 없음 · 분석 실패)

출처: `features/save-result/SaveResultScreen.tsx`, `components/SourceHeader.tsx`, `components/PlaceOption.tsx`, `shared/api/mock/saveResults.ts`

| 필드 | 타입 | null | 예시 | 백엔드 대응 |
|---|---|---|---|---|
| state | enum | ✗ | analyzing / needsConfirmation / noPlace / failed | NEEDS_CONFIRMATION · NO_PLACE 는 `SavePipelineResultDto.Status`. 분석 중은 없음. 실패는 `ExtractionStatus.FAILED` + `FailureInfoDto` |
| source.thumbnailUrl | string | ○ | 05 는 null | 없음 |
| source.savedAt | ISO | ✗ | → "방금 저장" | 없음 |
| source.platform | enum | ✗ | → "YouTube Shorts" | 없음 (프론트 `LinkParser.platform` 은 "YOUTUBE") |
| source.videoTitle | string | ○ | "대전 가면 꼭 들르는 빵집 3곳". 05 는 null | 없음 (백엔드 내부 `VideoInfo.title`) |
| source.channelHandle | string | ○ | "@daejeon.bread" | 없음. `VideoInfo` 에는 channelId 만 있다 |
| source.url | string | ✗ | 05 에서 제목 대신 표시 | 공유 원본 URL |
| candidates[] | array | needsConfirmation 에서만 | 2~3개 | `ConfirmRequestDto.candidates` |
| candidates[].id | string | ✗ | — | `candidateId` ("원본id-1" 형식. 결과 안에서만 유효) |
| candidates[].name | string | ✗ | "성심당 본점" | `name` + `branchName` |
| candidates[].address | string | ✗ | "대전 중구 은행동" | `address` (LocationIQ displayName. 형식 차이 → 미정 7) |
| failure.retryable | boolean | failed 에서만 | — | 없음. `FailureInfoDto.stage` 로 추정은 가능하다 |

- 02 안내의 후보 개수("두 곳을 찾았어요")는 `candidates.length` 로 프론트가 만든다.

### 08 장소 상세 · 지도

출처: `features/content-detail/ContentDetailScreen.tsx`, `components/DetailSheet.tsx`, `shared/api/mock/placeDetails.ts`

| 필드 | 타입 | null | 예시 | 백엔드 대응 |
|---|---|---|---|---|
| placeName | string | ✗ | "성심당 본점" | `ResolvedPlaceDto.name` · `branchName` |
| shortAddress | string | ✗ | "대전 중구 은행동" | `address` (형식 미정) |
| walkMinutes | number | ○ | → "도보 4분" | 없음 |
| savedAt | ISO | ✗ | → "3주 전" | 없음 |
| note | string | ✗ | "튀김소보로는 오전에 가면 줄이 짧다. 부추빵도 같이 사라고 함." | 추측: `summary` 또는 `PlaceCandidateDto.description` |
| tags | string[] | ✗ | ["줄서는", "오전", "빵집"] | 없음 |
| thumbnailUrl | string | ○ | — | 없음 |
| source.channelHandle · platform | string · enum | ○ | "@daejeon.bread · YouTube Shorts" | 없음 |
| source.url | string | ✗ | "원본 영상 다시 보기" 에서 연다 | 공유 원본 URL |
| lat · lng | number | ✗ | "길 안내 시작", 지도 중심 | `ResolvedPlaceDto.lat` · `lng` |
| nearbyMarkers[] | { placeId, lat, lng, thumbnailUrl, savedAt } | ✗ (빈 배열) | 지도 위 다른 저장 장소 | 없음. 지도 실구현 때 필요 |

### 디자인이 없는 화면

- `features/execution-trace/` — 디자인 없음. 모양은 `domain/executionTrace.ts` 에만 있다.
- `features/map-view/` — 디자인 없음. 08 지도와 역할이 겹친다.
- `features/onboarding/` — 디자인 없음. 카카오 로그인과 권한 안내가 들어갈 자리로 보인다.

## 2. 쓰기: 버튼·동작별

| 동작 (화면) | 보낼 것 | 돌려받을 것 | 엔드포인트 제안 |
|---|---|---|---|
| 공유 수신 (runtime/share) | 원본 URL, platform | contentId, 초기 state(analyzing) | `POST /api/contents` |
| "{장소}으로 저장" (02) | contentId, 선택한 candidateId | 확정된 place (`ResolvedPlaceDto`), 저장 완료 state | `POST /api/contents/{id}/confirm` |
| "여기 없어요, 직접 찾을게요" (02) · "장소 직접 붙이기" (04) | 검색어 → 고른 장소 | 검색 결과 목록 → 확정 place | `GET /api/places/search?q=`, `POST /api/contents/{id}/place` (다음 화면 디자인 없음) |
| "장소 없이 보관할게요" (04) | contentId | 저장 완료 (장소 없음) | `POST /api/contents/{id}/keep-without-place` |
| "다시 시도" (05) | contentId | state = analyzing | `POST /api/contents/{id}/retry` |
| "이 장소가 아니에요" (08) | contentId 또는 placeId | 추측: 되묻기(02) 재진입 또는 장소 해제 | 미정 (디자인 흐름 없음) |
| 필터 칩 선택 (01) | category | 걸러진 목록 | 01 읽기 API 의 쿼리 파라미터 |
| "오늘은 그만 알리기" (09 알림 액션) | placeId 또는 proposalId | 성공 여부 | `POST /api/proposals/{id}/snooze` (추측) |
| 로그인 (onboarding, 디자인 없음) | 카카오 access token | `TokenResponse` | 이미 있음: `POST /api/auth/kakao` |
| 토큰 갱신 · 로그아웃 | refreshToken / JWT | `TokenResponse` / 204 | 이미 있음: `/api/auth/refresh`, `/api/auth/logout` |

서버 요청이 없는 버튼:

- 02 "나중에 고를게요", 03 "닫고 기다릴게요", 05 "나중에 할게요" — 닫기만 한다. (02 는 미정 6 참고)
- 08 "원본 영상 다시 보기", "길 안내 시작" — 외부 앱을 연다. 읽기의 `source.url`, `lat` · `lng` 만 쓴다.
- 06 "대흥동 기억 보기" — 목적지가 디자인에 없다.

## 3. 백그라운드 (runtime)

| 파일 | 요청 | 보낼 것 → 받을 것 | 비고 |
|---|---|---|---|
| `runtime/notifications/*` | FCM 토큰 등록·갱신 | fcmToken → 204 | `Device.fcmToken` 은 있고 API 가 없다. 제안: `PUT /api/devices/me/fcm-token` |
| `runtime/notifications/router.ts` | 알림 페이로드 (푸시 수신) | 수신: `kind`(saveResult / proposal), `title`, `body`, `targetId`(contentId 또는 placeId), `imageUrl`, `actions` | 모양은 `domain/notification.ts`. 09 알림은 "지도 보기"(→ 08) · "오늘은 그만 알리기" 액션이 있다 |
| `runtime/share/*`, `runtime/background/pendingQueue.ts` | 공유 제출 (2장 첫 줄) | URL → contentId | 앱 UI 없이 보낸다. 오프라인이면 큐에 넣고 다시 보낸다. 중복 제출 처리 미정 |
| `runtime/geofence/geofenceManager.ts` `syncFences` | 펜스 목록 | → [{ fenceId, lat, lng, radiusMeters, placeId }] | 제안: `GET /api/geofences`. Android 는 앱당 펜스 100개 제한 → 누가 고를지 미정 |
| `runtime/geofence/geofenceManager.ts` `reportEnter` | 진입 보고 | fenceId, 발생 시각 → 204 | 제안: `POST /api/geofences/{id}/enter`. 서버가 무시했는지 앱이 알 방법 필요 (STRUCTURE.md 미확정 표) |
| `runtime/permissions/permissionWatcher.ts` | 없음 | — | 추측: 위치 권한이 회수되면 서버에 알려 제안 대상에서 뺄지 결정 필요 |

## 4. 미정: 백엔드와 정할 질문

1. **전역 ID.** 저장물(contentId)·장소(placeId)의 전역 ID 체계. 라우팅 파라미터 `resultId` 가 contentId 와 같은지. `candidateId` 는 결과 안에서만 유효하다.
2. **상태 하나로 합칠지.** 판정 enum 에 분석 중·실패가 없다. `analyzing | placeResolved | needsConfirmation | noPlace | failed` 같은 단일 상태 필드로 합칠지.
3. **바로 확정(PLACE_RESOLVED)일 때.** 02~05 중 해당 화면이 없다. 알림만 보낼지.
4. **완료 통지 방식.** 폴링 / SSE / 푸시. 03 → 02·04·05 전환이 여기에 막혀 있다.
5. **실패 처리.** 재시도 가능 여부(`retryable`)를 서버가 줄지. 비공개·삭제 영상(`VIDEO_ACCESS` · `METADATA_FETCH`)의 링크를 보관할지. 05 문구는 "링크는 그대로 보관" 이다.
6. **되묻기 미응답.** `ConfirmPolicy` TODO 에 "이미 물어봤는데 미응답이면 상위 지역으로 저장" 이 있다. 앱이 그 결과를 어떻게 아는지, 02 "나중에 고를게요" 가 서버에 알려야 하는 동작인지.
7. **후보 표시 형식.** `ResolvedPlaceDto.address` 는 LocationIQ displayName 인데 화면은 "대전 중구 은행동" 같은 짧은 한글 주소를 쓴다. name · branchName 을 누가 합칠지.
8. **되묻기 문구.** `ConfirmRequestDto.question` 은 "어느 장소가 맞을까요?", 디자인 02 는 "이 영상, 어디였을까요?". 문구를 서버와 앱 중 누가 가질지.
9. **카테고리와 태그.** 카테고리 enum 값(팀 합의: 고정 enum 소수 + 분위기 태그 다수, 값 미정)과 분위기 태그를 어디서 만드는지. 지금 추출 결과에는 둘 다 없다.
10. **거리 · 도보 시간 · 현재 동 이름.** 서버가 계산하려면 앱이 현재 위치를 보내야 하고, 앱이 계산하려면 목록에 좌표가 있어야 한다. 도보 시간은 경로 API 가 필요할 수 있다.
11. **"근처에 오면 색이 돌아온다" 기준.** `isNearNow` 와 01 "지금 가까운 곳" 을 서버 제안으로 받을지, 앱의 펜스 상태로 판단할지. 제안 유형 가설 5개(STRUCTURE.md)와 01 목록의 관계.
12. **목록 조회.** 페이지 단위 여부, 총개수 제공, 정렬 기준(저장 시각인지).
13. **알림.** FCM 토큰 등록 API, 알림 페이로드의 `kind` 목록.
14. **지오펜스.** 펜스 개수 제한(100)을 누가 관리할지, 진입 보고의 결과를 어떻게 알려줄지.
15. **인증 흐름.** 앱의 카카오 로그인 방식(SDK), 토큰 저장 위치(`shared/storage`). 목 데이터에서 실데이터로 넘어갈 때 개발용 토큰을 얻는 방법.
16. **에러 형식.** `ApiErrorResponse { code, message }` 를 모든 API 에 쓸지.
17. **추출 DTO 노출 여부.** 앱이 `YouTubeContentExtractionResultDto` 를 HTTP 로 직접 받는지, `SavePipelineResultDto` 만 받는지.

## 5. 프론트 쪽 정리 필요 (백엔드 확인 불필요)

- `src/domain/extraction/extractionResult.ts` 의 `PlaceCandidateDto.suggestedOrder` 는 백엔드에서 빠졌다. 백엔드와 다시 맞춘다.
- `STRUCTURE.md` 의 "backend 에는 Controller 가 하나도 없다" 전제를 인증 API 가 생긴 현재 상태로 고친다.
