package com.ktc.chungnam3.remembrall.recall.tool;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ktc.chungnam3.remembrall.recall.agent.ProposalSource;
import com.ktc.chungnam3.remembrall.recall.weather.WeatherClient;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class RecallToolResults {
    private RecallToolResults() {}

    public record SearchResult(List<PlaceCandidate> places, List<ContentCandidate> contents, String error) {}

    public record PlaceCandidate(UUID placeId, String name, double distanceMeters, List<ContentCandidate> contents) {}

    public record ContentCandidate(UUID contentId, ProposalSource source, String title, String category,
                                   String summary, @JsonInclude(JsonInclude.Include.NON_NULL) Instant savedAt) {}

    public enum SearchSource { PERSONAL, PUBLIC, ALL }

    public record VisitConditionsResult(List<PlaceConditions> places, WeatherClient.Weather weather,
                                        List<TargetError> errors, String error) {}

    public record PlaceConditions(UUID placeId, String name, List<OperatingCondition> operatingConditions,
                                  RecentProposals recentProposals) {}

    public record OperatingCondition(UUID contentId, ProposalSource source, String title, String summary,
                                     String description, Instant referenceAt, String referenceTimeBasis) {}

    public record RecentProposals(String source, Instant referenceAt, Instant since,
                                   boolean hasRecentProposal, List<RecentProposal> notifications) {}

    public record RecentProposal(Instant sentAt, boolean opened, Instant openedAt) {}

    public record TargetError(UUID id, String error) {}
}
