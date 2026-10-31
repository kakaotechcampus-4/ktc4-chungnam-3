package com.ktc.chungnam3.remembrall.recall.weather;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.StreamSupport;

public final class KmaWeatherClient implements WeatherClient {
    private static final Logger log = LoggerFactory.getLogger(KmaWeatherClient.class);
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HHmm");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    private final WeatherProperties properties;
    private final RestClient client;
    private final ConcurrentHashMap<Key, List<JsonNode>> cache = new ConcurrentHashMap<>();

    public KmaWeatherClient(WeatherProperties properties, RestClient client) {
        this.properties = properties;
        this.client = client;
    }

    @Override
    public Weather read(double latitude, double longitude, Instant now) {
        try {
            if (properties.serviceKey() == null || properties.serviceKey().isBlank()) {
                return Weather.unavailable(now);
            }
            var grid = KmaGrid.from(latitude, longitude);
            var observation = KmaPublicationTime.observation(now);
            var forecast = KmaPublicationTime.forecast(now);
            var observed = cached("getUltraSrtNcst", grid, observation);
            var predicted = cached("getVilageFcst", grid, forecast);
            var current = new Current(precipitation((int) observedValue(observed, "PTY")),
                    observedValue(observed, "T1H"), observation.toInstant(), "KMA_ULTRA_SHORT_OBSERVATION");
            var skyRow = predicted.stream().filter(row -> "SKY".equals(row.path("category").asText()))
                    .min(Comparator.comparingLong(row -> Math.abs(forecastAt(row).getEpochSecond() - now.getEpochSecond())))
                    .orElseThrow();
            var sky = new SkyCondition(sky(skyRow.path("fcstValue").asInt()), forecastAt(skyRow),
                    forecast.toInstant(), "KMA_SHORT_FORECAST");
            var probabilities = predicted.stream().filter(row -> "POP".equals(row.path("category").asText()))
                    .filter(row -> forecastAt(row).isAfter(now)
                            && !forecastAt(row).isAfter(now.plusSeconds(properties.forecastHours() * 3600L)))
                    .sorted(Comparator.comparing(KmaWeatherClient::forecastAt))
                    .map(row -> new RainProbability(forecastAt(row), probability(row), forecast.toInstant(),
                            "KMA_SHORT_FORECAST")).toList();
            if (probabilities.isEmpty()) throw new IllegalStateException("Missing precipitation forecast");
            return new Weather(true, current, sky, probabilities, now, "KMA", null);
        } catch (Exception exception) {
            log.warn("Recall weather unavailable: {}", exception.getClass().getSimpleName());
            return Weather.unavailable(now);
        }
    }

    private List<JsonNode> cached(String operation, KmaGrid grid, ZonedDateTime base) {
        var key = new Key(operation, grid, base.toInstant());
        if (!cache.containsKey(key) && cache.size() >= properties.maxCacheEntries()) {
            cache.keySet().stream().min(Comparator.comparing(Key::base)).ifPresent(cache::remove);
        }
        return cache.computeIfAbsent(key, ignored -> fetch(operation, grid, base));
    }

    private List<JsonNode> fetch(String operation, KmaGrid grid, ZonedDateTime base) {
        String key = properties.serviceKey().contains("%")
                ? URLDecoder.decode(properties.serviceKey(), StandardCharsets.UTF_8) : properties.serviceKey();
        String body = client.get().uri(builder -> builder.path("/" + operation)
                .queryParam("serviceKey", "{key}").queryParam("pageNo", 1).queryParam("numOfRows", 1000)
                .queryParam("dataType", "JSON").queryParam("base_date", DATE.format(base))
                .queryParam("base_time", TIME.format(base)).queryParam("nx", grid.x()).queryParam("ny", grid.y())
                .build(key)).retrieve().body(String.class);
        var response = MAPPER.readTree(body).path("response");
        if (!"00".equals(response.path("header").path("resultCode").asText())) {
            throw new IllegalStateException("KMA response unavailable");
        }
        var items = response.path("body").path("items").path("item");
        if (!items.isArray() || items.isEmpty()) throw new IllegalStateException("Missing KMA items");
        return StreamSupport.stream(items.spliterator(), false).toList();
    }

    private static Instant forecastAt(JsonNode row) {
        return LocalDateTime.parse(row.path("fcstDate").asText() + row.path("fcstTime").asText(), DATE_TIME)
                .atZone(KmaPublicationTime.SEOUL).toInstant();
    }

    private static double observedValue(List<JsonNode> rows, String category) {
        double value = rows.stream().filter(row -> category.equals(row.path("category").asText()))
                .mapToDouble(row -> Double.parseDouble(row.path("obsrValue").asText())).findFirst().orElseThrow();
        if (!Double.isFinite(value) || value <= -90) throw new IllegalStateException("Invalid KMA observation");
        return value;
    }

    private static int probability(JsonNode row) {
        int value = Integer.parseInt(row.path("fcstValue").asText());
        if (value < 0 || value > 100) throw new IllegalStateException("Invalid KMA probability");
        return value;
    }

    private static Precipitation precipitation(int value) {
        return switch (value) {
            case 0 -> Precipitation.NONE;
            case 1 -> Precipitation.RAIN;
            case 2 -> Precipitation.RAIN_AND_SNOW;
            case 3 -> Precipitation.SNOW;
            case 4 -> Precipitation.SHOWER;
            case 5 -> Precipitation.DRIZZLE;
            case 6 -> Precipitation.DRIZZLE_AND_SNOW;
            case 7 -> Precipitation.SNOW_FLURRIES;
            default -> throw new IllegalStateException("Unknown KMA precipitation");
        };
    }

    private static Sky sky(int value) {
        return switch (value) {
            case 1 -> Sky.CLEAR;
            case 3 -> Sky.PARTLY_CLOUDY;
            case 4 -> Sky.OVERCAST;
            default -> throw new IllegalStateException("Unknown KMA sky");
        };
    }

    private record Key(String operation, KmaGrid grid, Instant base) {}
}
