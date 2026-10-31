package com.ktc.chungnam3.remembrall.recall.evaluation;

import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

public record SeedEvaluationScene(String id, String split, String kind, Seed seed, Event event,
                                  JsonNode reference, JsonNode judgeNotes, List<GoodChoice> goodChoices,
                                  Weather weather, List<RecentNotification> recentNotifications,
                                  List<String> expectedTools) {
    public SeedEvaluationScene {
        goodChoices = goodChoices == null ? List.of() : List.copyOf(goodChoices);
        recentNotifications = recentNotifications == null ? List.of() : List.copyOf(recentNotifications);
        expectedTools = expectedTools == null ? List.of() : List.copyOf(expectedTools);
    }

    public SeedEvaluationScene(String id, String split, String kind, Seed seed, Event event,
                               JsonNode reference, JsonNode judgeNotes, List<GoodChoice> goodChoices,
                               Weather weather, List<RecentNotification> recentNotifications) {
        this(id, split, kind, seed, event, reference, judgeNotes, goodChoices, weather, recentNotifications, List.of());
    }

    public SeedEvaluationScene(String id, String split, String kind, Seed seed, Event event,
                               JsonNode reference, JsonNode judgeNotes, List<GoodChoice> goodChoices) {
        this(id, split, kind, seed, event, reference, judgeNotes, goodChoices, null, List.of());
    }

    public SeedEvaluationScene(String id, String split, String kind, Seed seed, Event event,
                               JsonNode reference, JsonNode judgeNotes) {
        this(id, split, kind, seed, event, reference, judgeNotes, List.of());
    }

    public record GoodChoice(String result, String proposalType, List<String> places, Boolean ordered,
                             List<String> videos) {
        public GoodChoice {
            ordered = Boolean.TRUE.equals(ordered);
        }
    }

    public record Seed(Map<String, Place> places, List<Save> personalSaves, List<Contributor> contributors) {}

    public record Place(String name, String address, double latitude, double longitude) {}

    public record Save(String videoId, String title, String summary, String category, String savedAt,
                       List<PlaceLink> places) {}

    public record PlaceLink(String placeKey, String description) {}

    public record Contributor(boolean agreed, List<Save> saves) {}

    public record Event(String placeKey, String occurredAt) {}

    public record Weather(com.ktc.chungnam3.remembrall.recall.weather.WeatherClient.Precipitation precipitation,
                           Double temperatureCelsius,
                           com.ktc.chungnam3.remembrall.recall.weather.WeatherClient.Sky sky,
                           List<Integer> rainProbabilities) {}

    public record RecentNotification(String placeKey, String sentAt, boolean opened) {}
}
