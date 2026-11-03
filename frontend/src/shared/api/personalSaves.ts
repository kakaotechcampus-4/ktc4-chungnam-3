// 저장물 API. POST · GET /api/personal-saves, GET /api/personal-saves/places, GET · DELETE /api/personal-saves/{personalSaveId},
// POST /api/personal-saves/{personalSaveId}/places/{candidateId}/confirm(되묻기 답변).
// 응답은 백엔드 PersonalSaveResponse 그대로. 실패는 클라이언트의 ApiError 를 그대로 던진다.
// 저장은 새로 저장하면 201, 이미 저장한 영상이면 200 이고 본문은 같다. 링크에서 영상 id 를 못 뽑으면 400 INVALID_REQUEST.
// 분석은 서버에서 비동기로 진행된다. 결과는 상세 · 목록을 다시 읽어 확인한다(완료 통지 없음).
// 목 모드(API 주소 없음)에서는 서버를 부르지 않고 personalSavesMock 의 메모리 저장소를 쓴다.
import { request, requestWithStatus } from "./client";
import { isApiConfigured } from "./config";
import {
    mockConfirmPlace,
    mockDeletePersonalSave,
    mockFetchPersonalSave,
    mockFetchPersonalSaves,
    mockFetchSavedPlaces,
    mockSavePersonalSave,
} from "./personalSavesMock";

export type AnalysisStatus =
    | "PENDING"
    | "ANALYZING"
    | "SUCCESS"
    | "PARTIAL"
    | "FAILED";

export type AnalysisFailureCode =
    | "VIDEO_UNAVAILABLE"
    | "EXTRACTION_API_ERROR"
    | "EXTRACTION_RESULT_ERROR"
    | "PLACE_SEARCH_API_ERROR"
    | "PLACE_VERIFICATION_API_ERROR";

// id 는 UUID 문자열, 시각은 ISO-8601 UTC.
export type PersonalSaveResponse = {
    personalSaveId: string;
    contentId: string;
    analysisStatus: AnalysisStatus;
    savedAt: string;
};

// 목록 항목. summary 는 서버가 200자에서 자른다. analysisFailureCode 는 PARTIAL · FAILED 일 때만 채워진다.
// representativePlaceName 은 연결된 장소 중 하나의 이름(장소가 없으면 null), pendingConfirmCount 는 답하지 않은 되묻기 후보 수.
export type PersonalSaveItemResponse = {
    personalSaveId: string;
    contentId: string;
    videoId: string;
    title: string | null;
    summary: string | null;
    analysisStatus: AnalysisStatus;
    analysisFailureCode: AnalysisFailureCode | null;
    placeCount: number;
    representativePlaceName: string | null;
    pendingConfirmCount: number;
    savedAt: string;
};

// 최신 저장순. 마지막 페이지면 nextCursor 는 null.
export type PersonalSavePageResponse = {
    items: PersonalSaveItemResponse[];
    nextCursor: string | null;
};

export type PersonalSavePlaceResponse = {
    placeId: string;
    name: string;
    address: string | null;
    latitude: number;
    longitude: number;
    description: string | null;
};

// 되묻기 후보의 선택지. candidateId 는 답할 때 selectedOptionId 로 보낸다.
// name 은 영상에서 뽑은 이름이라 선택지끼리 같을 수 있다(주소만 다름). branchName · address 는 경로에 따라 비어 있다.
// address 는 출처마다 형식이 다르다(LocationIQ 는 "이름, 도로, 동, 구, 시, 우편번호, 대한민국").
export type ConfirmOptionResponse = {
    candidateId: string;
    name: string | null;
    branchName: string | null;
    address: string | null;
    lat: number;
    lng: number;
    geocodingProvider: string;
    geocodingPlaceId: string | null;
    verificationPlaceId: string | null;
};

// 답하지 않은(PENDING) 되묻기 후보. candidateId 는 답변 경로의 {candidateId}. question 은 화면이 쓰지 않는다(고정 문구).
export type ConfirmCandidateResponse = {
    candidateId: string;
    question: string | null;
    options: ConfirmOptionResponse[];
};

// sourceUrl 은 항상 watch 형식(https://www.youtube.com/watch?v=…). 장소 · 되묻기 후보가 없으면 각각 빈 배열.
// analysisFailureCode 는 PARTIAL · FAILED 일 때만 채워진다. confirmCandidates 는 답하지 않은 후보만 온다.
export type PersonalSaveDetailResponse = {
    personalSaveId: string;
    contentId: string;
    videoId: string;
    sourceUrl: string;
    title: string | null;
    summary: string | null;
    category: string | null;
    analysisStatus: AnalysisStatus;
    analysisFailureCode: AnalysisFailureCode | null;
    savedAt: string;
    places: PersonalSavePlaceResponse[];
    confirmCandidates: ConfirmCandidateResponse[];
};

// 내 장소 목록 항목. 장소와 저장물 한 쌍마다 한 항목이라 같은 placeId 가 여러 번 올 수 있다.
// videoId · savedAt 은 그 저장물의 영상 id · 저장 시각이다.
export type SavedPlacePinResponse = {
    placeId: string;
    name: string;
    address: string | null;
    latitude: number;
    longitude: number;
    personalSaveId: string;
    videoId: string;
    savedAt: string;
};

// created: 새로 저장했다(201). false 면 이미 저장한 영상이다(200).
export async function savePersonalSave(
    url: string,
): Promise<{ save: PersonalSaveResponse; created: boolean }> {
    if (!isApiConfigured) return mockSavePersonalSave(url);
    const { status, body } = await requestWithStatus<PersonalSaveResponse>(
        "/api/personal-saves",
        { method: "POST", body: { url } },
    );
    return { save: body, created: status === 201 };
}

export function fetchPersonalSave(
    personalSaveId: string,
): Promise<PersonalSaveDetailResponse> {
    if (!isApiConfigured) return mockFetchPersonalSave(personalSaveId);
    return request<PersonalSaveDetailResponse>(
        `/api/personal-saves/${encodeURIComponent(personalSaveId)}`,
    );
}

// size 는 서버 기본 20, 1~100 밖이면 400. cursor 는 이전 응답의 nextCursor 를 그대로 넘긴다.
export function fetchPersonalSaves(
    params: { size?: number; cursor?: string } = {},
): Promise<PersonalSavePageResponse> {
    if (!isApiConfigured) return mockFetchPersonalSaves(params);
    const query: string[] = [];
    if (params.size != null) query.push(`size=${params.size}`);
    if (params.cursor != null) {
        query.push(`cursor=${encodeURIComponent(params.cursor)}`);
    }
    const suffix = query.length > 0 ? `?${query.join("&")}` : "";
    return request<PersonalSavePageResponse>(`/api/personal-saves${suffix}`);
}

// 내 저장물의 모든 장소. 최신 저장순, 페이지 없음. 썸네일 · 저장 시각은 없어 목록과 personalSaveId 로 묶는다.
export function fetchSavedPlaces(): Promise<SavedPlacePinResponse[]> {
    if (!isApiConfigured) return mockFetchSavedPlaces();
    return request<SavedPlacePinResponse[]>("/api/personal-saves/places");
}

// 되묻기 후보에 답한다. 204. 서버가 고른 선택지를 카카오로 확인한 뒤 장소를 붙이고 후보를 닫는다.
// 404: 내 저장물이 아니거나 후보가 이미 처리됐거나 고른 장소를 카카오에서 확인하지 못함. 400: 그 후보의 선택지가 아님. 502: 카카오 호출 실패.
export function confirmPlace(
    personalSaveId: string,
    candidateId: string,
    selectedOptionId: string,
): Promise<void> {
    if (!isApiConfigured) {
        return mockConfirmPlace(personalSaveId, candidateId, selectedOptionId);
    }
    return request<void>(
        `/api/personal-saves/${encodeURIComponent(personalSaveId)}/places/${encodeURIComponent(candidateId)}/confirm`,
        { method: "POST", body: { selectedOptionId } },
    );
}

export function deletePersonalSave(personalSaveId: string): Promise<void> {
    if (!isApiConfigured) return mockDeletePersonalSave(personalSaveId);
    return request<void>(
        `/api/personal-saves/${encodeURIComponent(personalSaveId)}`,
        { method: "DELETE" },
    );
}
