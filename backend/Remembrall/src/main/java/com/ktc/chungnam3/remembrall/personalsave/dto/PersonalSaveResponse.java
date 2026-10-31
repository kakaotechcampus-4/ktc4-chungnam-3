package com.ktc.chungnam3.remembrall.personalsave.dto;

import com.ktc.chungnam3.remembrall.content.dto.ContentSaveClaim;
import com.ktc.chungnam3.remembrall.domain.content.ContentAnalysisStatus;
import com.ktc.chungnam3.remembrall.save.dto.ResolvedPlaceDto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PersonalSaveResponse(UUID personalSaveId, UUID contentId,
                                   ContentAnalysisStatus analysisStatus, Instant savedAt) {
    public static PersonalSaveResponse from(ContentSaveClaim claim) {
        return new PersonalSaveResponse(claim.personalSaveId(), claim.contentId(), claim.analysisStatus(), claim.savedAt());
    }

    public record Page(List<Item> items, String nextCursor) {
    }

    public record Item(UUID personalSaveId, UUID contentId, String videoId, String title, String summary,
                       ContentAnalysisStatus analysisStatus, long placeCount, Instant savedAt) {
    }

    public record Detail(UUID personalSaveId, UUID contentId, String videoId, String sourceUrl, String title,
                         String summary, String category, ContentAnalysisStatus analysisStatus,
                         String analysisFailureCode, Instant savedAt, List<Place> places,
                         List<ConfirmCandidate> confirmCandidates) {
    }

    public record Place(UUID placeId, String name, String address, double latitude, double longitude,
                        String description) {
    }

    /**
     * {@code candidateId}는 {@code ConfirmAnswerController} 답변 제출 경로의 path variable로 그대로
     * 쓰는 값, {@code options[].candidateId()}는 그중 고르는 selectedOptionId에 해당한다.
     */
    public record ConfirmCandidate(String candidateId, String question, List<ResolvedPlaceDto> options) {
    }
}
