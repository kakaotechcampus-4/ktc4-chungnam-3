package com.ktc.chungnam3.remembrall.recall.weather;

import java.time.Instant;
import java.util.List;

public interface WeatherClient {
    Weather read(double latitude, double longitude, Instant now);

    enum Precipitation { NONE, RAIN, RAIN_AND_SNOW, SNOW, SHOWER, DRIZZLE, DRIZZLE_AND_SNOW, SNOW_FLURRIES }
    enum Sky { CLEAR, PARTLY_CLOUDY, OVERCAST }

    record Current(Precipitation precipitation, double temperatureCelsius, Instant referenceAt, String source) {}
    record SkyCondition(Sky sky, Instant validAt, Instant referenceAt, String source) {}
    record RainProbability(Instant forecastAt, int percent, Instant referenceAt, String source) {}
    record Weather(boolean available, Current current, SkyCondition sky, List<RainProbability> rainProbabilities,
                   Instant checkedAt, String source, String error) {
        public static Weather unavailable(Instant now) {
            return new Weather(false, null, null, List.of(), now, "KMA", "WEATHER_UNAVAILABLE");
        }
    }
}
