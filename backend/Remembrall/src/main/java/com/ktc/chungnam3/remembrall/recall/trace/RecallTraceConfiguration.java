package com.ktc.chungnam3.remembrall.recall.trace;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RecallTraceProperties.class)
public class RecallTraceConfiguration {
    @Bean
    RecallTraceService recallTraceService(JdbcTemplate jdbc, PlatformTransactionManager transactionManager,
                                           Clock clock, ObjectMapper mapper, RecallTraceProperties properties) {
        return new RecallTraceService(jdbc, transactionManager, clock, mapper, properties);
    }
}
