package com.ktc.chungnam3.remembrall.personalsave.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PersonalSaveProperties.class)
public class PersonalSaveConfiguration {
    @Bean
    ThreadPoolTaskExecutor personalSaveAnalysisExecutor(PersonalSaveProperties properties) {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.analysisConcurrency());
        executor.setMaxPoolSize(properties.analysisConcurrency());
        executor.setQueueCapacity(properties.analysisQueueCapacity());
        executor.setThreadNamePrefix("personal-save-analysis-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }
}
