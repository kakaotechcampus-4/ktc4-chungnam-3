package com.ktc.chungnam3.remembrall.auth.security;

import com.ktc.chungnam3.remembrall.auth.token.SessionProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(SessionProperties.class)
public class SessionConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
