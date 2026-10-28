package com.ktc.chungnam3.remembrall.personalsave;

import com.ktc.chungnam3.remembrall.personalsave.config.PersonalSaveConfiguration;
import com.ktc.chungnam3.remembrall.personalsave.config.PersonalSaveProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.assertj.core.api.Assertions.assertThat;

class PersonalSaveConfigurationTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(PersonalSaveConfiguration.class);

    @Test
    void defaultsUseBoundedDedicatedExecutorAndConfiguredPagination() {
        context.run(application -> {
            assertThat(application).hasNotFailed();
            var properties = application.getBean(PersonalSaveProperties.class);
            assertThat(properties.defaultPageSize()).isEqualTo(20);
            assertThat(properties.maxPageSize()).isEqualTo(100);
            assertThat(properties.maxSummaryLength()).isEqualTo(200);
            var executor = application.getBean("personalSaveAnalysisExecutor", ThreadPoolTaskExecutor.class);
            assertThat(executor.getCorePoolSize()).isEqualTo(2);
            assertThat(executor.getMaxPoolSize()).isEqualTo(2);
            assertThat(executor.getThreadPoolExecutor().getQueue().remainingCapacity()).isEqualTo(100);
        });
    }

    @Test
    void settingsOverrideExecutorAndPaginationDefaults() {
        context.withPropertyValues("personal-save.analysis-concurrency=3", "personal-save.analysis-queue-capacity=7",
                "personal-save.default-page-size=5", "personal-save.max-page-size=10", "personal-save.max-summary-length=30")
                .run(application -> {
                    assertThat(application).hasNotFailed();
                    var properties = application.getBean(PersonalSaveProperties.class);
                    assertThat(properties.defaultPageSize()).isEqualTo(5);
                    assertThat(properties.maxPageSize()).isEqualTo(10);
                    assertThat(properties.maxSummaryLength()).isEqualTo(30);
                    var executor = application.getBean("personalSaveAnalysisExecutor", ThreadPoolTaskExecutor.class);
                    assertThat(executor.getCorePoolSize()).isEqualTo(3);
                    assertThat(executor.getMaxPoolSize()).isEqualTo(3);
                    assertThat(executor.getThreadPoolExecutor().getQueue().remainingCapacity()).isEqualTo(7);
                });
    }

    @Test
    void defaultPageSizeCannotExceedMaximum() {
        context.withPropertyValues("personal-save.default-page-size=11", "personal-save.max-page-size=10")
                .run(application -> assertThat(application).hasFailed());
    }
}
