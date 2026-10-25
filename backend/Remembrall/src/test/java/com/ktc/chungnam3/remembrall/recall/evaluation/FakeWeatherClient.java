package com.ktc.chungnam3.remembrall.recall.evaluation;

import com.ktc.chungnam3.remembrall.recall.weather.WeatherClient;

import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

public final class FakeWeatherClient implements WeatherClient {
    private volatile SeedEvaluationScene.Weather scenario;

    public void configure(SeedEvaluationScene.Weather scenario) { this.scenario = scenario; }

    @Override
    public Weather read(double latitude, double longitude, Instant now) {
        var selected = scenario;
        var precipitation = selected == null || selected.precipitation() == null ? Precipitation.NONE : selected.precipitation();
        double temperature = selected == null || selected.temperatureCelsius() == null ? 20 : selected.temperatureCelsius();
        var sky = selected == null || selected.sky() == null ? Sky.CLEAR : selected.sky();
        List<Integer> chances = selected == null || selected.rainProbabilities() == null
                ? List.of(0, 0, 0, 0, 0, 0) : selected.rainProbabilities();
        if (!Double.isFinite(temperature) || chances.stream().anyMatch(value -> value == null || value < 0 || value > 100)) {
            throw new IllegalArgumentException("Invalid scene weather");
        }
        return new Weather(true, new Current(precipitation, temperature, now, "EVALUATION_FIXED_WEATHER"),
                new SkyCondition(sky, now, now, "EVALUATION_FIXED_WEATHER"),
                IntStream.range(0, chances.size()).mapToObj(index -> new RainProbability(
                        now.plusSeconds((index + 1L) * 3600), chances.get(index), now, "EVALUATION_FIXED_WEATHER")).toList(),
                now, "EVALUATION_FIXED_WEATHER", null);
    }
}
