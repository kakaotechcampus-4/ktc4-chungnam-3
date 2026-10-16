package com.ktc.chungnam3.remembrall.recall.config;

import com.ktc.chungnam3.remembrall.recall.agent.StubRecallAgent;
import com.ktc.chungnam3.remembrall.recall.agent.RecallAgent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({RecallProperties.class, RecallCandidateProperties.class, RecallToolProperties.class,
        RecallAgentProperties.class})
public class RecallConfiguration {

    @Bean
    @ConditionalOnMissingBean(RecallAgent.class)
    public RecallAgent recallAgent(RecallAgentProperties properties, Environment environment) throws Exception {
        return properties.mode() == RecallAgentProperties.Mode.STUB
                ? new StubRecallAgent() : RecallLlmAgentFactory.create(properties, environment);
    }
}
