package com.ktc.chungnam3.remembrall.recall.weather;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KmaWeatherClientTest {
    private static final Instant NOW = Instant.parse("2026-10-10T01:25:00Z");

    @Test
    void officialProjectionReferencePointAndSeoulGridMatch() {
        assertThat(KmaGrid.from(38, 126)).isEqualTo(new KmaGrid(43, 136));
        assertThat(KmaGrid.from(37.5665, 126.9780)).isEqualTo(new KmaGrid(60, 127));
    }

    @ParameterizedTest
    @CsvSource({
            "2026-10-10T00:05:00+09:00,2026-10-09T23:50+09:00,2026-10-09T23:00+09:00",
            "2026-10-10T02:09:59+09:00,2026-10-10T01:50+09:00,2026-10-09T23:00+09:00",
            "2026-10-10T02:10:00+09:00,2026-10-10T02:00+09:00,2026-10-10T02:00+09:00",
            "2026-10-10T10:49:00+09:00,2026-10-10T10:30+09:00,2026-10-10T08:00+09:00",
            "2026-10-10T23:10:00+09:00,2026-10-10T23:00+09:00,2026-10-10T23:00+09:00"
    })
    void publicationTimesUseKstAndPublishedDataOnly(String now, String observation, String forecast) {
        Instant instant = java.time.OffsetDateTime.parse(now).toInstant();
        assertThat(KmaPublicationTime.observation(instant).toOffsetDateTime())
                .isEqualTo(java.time.OffsetDateTime.parse(observation));
        assertThat(KmaPublicationTime.forecast(instant).toOffsetDateTime())
                .isEqualTo(java.time.OffsetDateTime.parse(forecast));
    }

    @Test
    void sameGridAndPublicationFetchEachEndpointOnceEvenConcurrently() throws Exception {
        var builder = RestClient.builder().baseUrl("https://weather.example");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(containsString("/getUltraSrtNcst?")))
                .andExpect(queryParam("base_time", "1010"))
                .andExpect(queryParam("nx", "60")).andExpect(queryParam("ny", "127"))
                .andRespond(withSuccess(envelope("""
                        {"category":"PTY","obsrValue":"1"},{"category":"T1H","obsrValue":"18.5"}
                        """), MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("/getVilageFcst?"))).andExpect(queryParam("base_time", "0800"))
                .andRespond(withSuccess(envelope("""
                        {"category":"SKY","fcstDate":"20261010","fcstTime":"1000","fcstValue":"4"},
                        {"category":"POP","fcstDate":"20261010","fcstTime":"1100","fcstValue":"80"},
                        {"category":"POP","fcstDate":"20261010","fcstTime":"1200","fcstValue":"70"}
                        """), MediaType.APPLICATION_JSON));
        var client = new KmaWeatherClient(properties("test-key"), builder.build());
        try (var threads = Executors.newFixedThreadPool(2)) {
            var first = threads.submit(() -> client.read(37.5665, 126.9780, NOW));
            var second = threads.submit(() -> client.read(37.5665, 126.9780, NOW));
            assertThat(first.get().current().precipitation()).isEqualTo(WeatherClient.Precipitation.RAIN);
            assertThat(second.get().sky().sky()).isEqualTo(WeatherClient.Sky.OVERCAST);
        }
        var cached = client.read(37.56651, 126.97801, NOW.plusSeconds(60));
        assertThat(cached.available()).isTrue();
        assertThat(cached.current().temperatureCelsius()).isEqualTo(18.5);
        assertThat(cached.rainProbabilities()).extracting(WeatherClient.RainProbability::percent).containsExactly(80, 70);
        assertThat(cached.current().referenceAt()).isEqualTo(Instant.parse("2026-10-10T01:10:00Z"));
        assertThat(cached.sky().referenceAt()).isEqualTo(Instant.parse("2026-10-09T23:00:00Z"));
        server.verify();
    }

    @Test
    void missingKeyOrInvalidResponseProducesUnavailableWithoutLeakingDetails() {
        var builder = RestClient.builder().baseUrl("https://weather.example");
        var server = MockRestServiceServer.bindTo(builder).build();
        assertThat(new KmaWeatherClient(properties(""), builder.build()).read(37.5, 127, NOW).available()).isFalse();
        server.expect(requestTo(containsString("getUltraSrtNcst")))
                .andRespond(withSuccess("{\"response\":{\"header\":{\"resultCode\":\"30\",\"resultMsg\":\"secret\"}}}", MediaType.APPLICATION_JSON));
        var result = new KmaWeatherClient(properties("key"), builder.build()).read(37.5, 127, NOW);
        assertThat(result.error()).isEqualTo("WEATHER_UNAVAILABLE").doesNotContain("secret");
        server.verify();
    }

    private WeatherProperties properties(String key) {
        return new WeatherProperties("https://weather.example", key, Duration.ofSeconds(2), Duration.ofSeconds(3), 6, 512);
    }

    private String envelope(String items) {
        return "{\"response\":{\"header\":{\"resultCode\":\"00\"},\"body\":{\"items\":{\"item\":[" + items + "]}}}}";
    }
}
