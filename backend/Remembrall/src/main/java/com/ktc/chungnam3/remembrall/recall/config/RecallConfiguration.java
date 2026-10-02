package com.ktc.chungnam3.remembrall.recall.config;

import com.ktc.chungnam3.remembrall.recall.agent.StubRecallAgent;
import com.ktc.chungnam3.remembrall.recall.agent.RecallAgent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RecallProperties.class)
public class RecallConfiguration {

    @Bean
    @ConditionalOnMissingBean(RecallAgent.class)
    public RecallAgent recallAgent() {
        return new StubRecallAgent();
    }
}
