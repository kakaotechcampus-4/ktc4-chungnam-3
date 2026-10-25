package com.ktc.chungnam3.remembrall.recall.weather;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(WeatherProperties.class)
public class WeatherConfiguration {
    @Bean
    @ConditionalOnMissingBean(WeatherClient.class)
    WeatherClient weatherClient(WeatherProperties properties) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout()).build());
        factory.setReadTimeout(properties.readTimeout());
        return new KmaWeatherClient(properties, RestClient.builder().baseUrl(properties.baseUrl())
                .requestFactory(factory).build());
    }
}
