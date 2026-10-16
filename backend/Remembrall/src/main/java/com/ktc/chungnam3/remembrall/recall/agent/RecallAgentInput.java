package com.ktc.chungnam3.remembrall.recall.agent;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record RecallAgentInput(
        Origin origin,
        List<PersonalPlace> nearbyPersonalCandidates,
        List<PublicPlace> nearbyPublicCandidates,
        List<SemanticContent> semanticCandidates,
        Situation situation
) {
    public record Origin(UUID placeId, String name, double latitude, double longitude,
                         List<PersonalContent> contents) {}

    public record PersonalContent(UUID contentId, UUID personalSaveId, String title, String summary,
                                  String category, Instant savedAt, String description) {}

    public record PublicContent(UUID contentId, String title, String summary, String category,
                                String description) {}

    public record PersonalPlace(UUID placeId, String name, double distanceMeters,
                                List<PersonalContent> contents) {}

    public record PublicPlace(UUID placeId, String name, double distanceMeters,
                              List<PublicContent> contents) {}

    public record SemanticContent(UUID contentId, ProposalSource source,
                                  @JsonInclude(JsonInclude.Include.NON_NULL) UUID personalSaveId,
                                  String title, String summary, String category,
                                  @JsonInclude(JsonInclude.Include.NON_NULL) Instant savedAt) {}

    public record Situation(LocalDateTime localDateTime, DayOfWeek dayOfWeek) {}
}
