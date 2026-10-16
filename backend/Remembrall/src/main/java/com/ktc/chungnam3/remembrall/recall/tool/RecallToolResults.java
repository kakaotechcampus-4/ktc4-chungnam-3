package com.ktc.chungnam3.remembrall.recall.tool;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ktc.chungnam3.remembrall.recall.agent.ProposalSource;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class RecallToolResults {
    private RecallToolResults() {}

    public record SearchResult(List<PlaceCandidate> places, List<ContentCandidate> contents, String error) {}

    public record PlaceCandidate(UUID placeId, String name, double distanceMeters, List<ContentCandidate> contents) {}

    public record ContentCandidate(UUID contentId, ProposalSource source, String title, String category,
                                   String summary, @JsonInclude(JsonInclude.Include.NON_NULL) Instant savedAt) {}

    public record DetailsResult(List<ContentDetail> contents, List<TargetError> errors, String error) {}

    public record ContentDetail(UUID contentId, ProposalSource source, String title, String summary, String category,
                                List<ContentPlace> places, @JsonInclude(JsonInclude.Include.NON_NULL) Instant savedAt) {}

    public record ContentPlace(UUID placeId, String name, String description, double distanceMeters) {}

    public record DistancesResult(List<DistancePair> pairs, List<TargetError> errors, String error) {}

    public record DistancePair(UUID fromPlaceId, UUID toPlaceId, double straightLineMeters,
                               double estimatedWalkingMinutes, boolean walkingTimeEstimated) {}

    public record TargetError(UUID id, String error) {}
}
