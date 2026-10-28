package com.ktc.chungnam3.remembrall.recall.evaluation;

import com.ktc.chungnam3.remembrall.consent.config.ConsentConfiguration;
import com.ktc.chungnam3.remembrall.consent.service.MemberConsentService;
import com.ktc.chungnam3.remembrall.content.embedding.VertexEmbeddingClient;
import com.ktc.chungnam3.remembrall.content.service.ContentAnalysisResultService;
import com.ktc.chungnam3.remembrall.content.service.ContentPersistenceService;
import com.ktc.chungnam3.remembrall.recall.config.RecallCandidateProperties;
import com.ktc.chungnam3.remembrall.recall.config.RecallProperties;
import com.ktc.chungnam3.remembrall.recall.config.RecallToolProperties;
import com.ktc.chungnam3.remembrall.recall.service.ProposalValidator;
import com.ktc.chungnam3.remembrall.recall.service.RecallAgentInputService;
import com.ktc.chungnam3.remembrall.recall.service.WalkingTimeCalculator;
import com.ktc.chungnam3.remembrall.recall.trace.RecallTraceConfiguration;
import com.ktc.chungnam3.remembrall.recall.tool.RecallToolBinding;
import com.ktc.chungnam3.remembrall.recall.tool.RecallToolService;
import com.ktc.chungnam3.remembrall.recall.tool.RecallToolRepository;
import com.ktc.chungnam3.remembrall.repository.MemberRepository;
import com.ktc.chungnam3.remembrall.repository.PublicCandidateRepository;
import com.ktc.chungnam3.remembrall.repository.RecallCandidateRepository;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@TestComponent
@EnableAutoConfiguration
@EntityScan("com.ktc.chungnam3.remembrall.domain")
@EnableJpaRepositories(basePackageClasses = MemberRepository.class)
@EnableConfigurationProperties({RecallCandidateProperties.class, RecallProperties.class, RecallToolProperties.class})
@Import({ContentPersistenceService.class, ContentAnalysisResultService.class, VertexEmbeddingClient.class,
        MemberConsentService.class, ConsentConfiguration.class, RecallAgentInputService.class,
        RecallCandidateRepository.class, PublicCandidateRepository.class, ProposalValidator.class,
        WalkingTimeCalculator.class, SeedEvaluationSeeder.class, SeedEvaluationRunner.class,
        RecallTraceConfiguration.class, RecallToolRepository.class, RecallToolService.class, RecallToolBinding.class})
public class SeedEvaluationConfiguration {
    @Bean
    @ServiceConnection
    PostgreSQLContainer seedEvaluationPostgres() {
        return new PostgreSQLContainer(
                DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));
    }

    @Bean
    Clock seedEvaluationClock() {
        return new EvaluationClock();
    }

    @Bean
    FakeWeatherClient evaluationWeatherClient() {
        return new FakeWeatherClient();
    }
}
