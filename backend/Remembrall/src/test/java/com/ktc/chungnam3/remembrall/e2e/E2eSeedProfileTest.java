package com.ktc.chungnam3.remembrall.e2e;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class E2eSeedProfileTest {
    @ParameterizedTest
    @ValueSource(strings = {"", "test", "prod"})
    void noE2eBeansAreRegisteredOutsideE2e(String profile) {
        new ApplicationContextRunner()
                .withPropertyValues("spring.profiles.active=" + profile)
                .withUserConfiguration(E2eSeedService.class, E2eSeedController.class, E2eAgentInputRecorder.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(E2eSeedService.class);
                    assertThat(context).doesNotHaveBean(E2eSeedController.class);
                    assertThat(context).doesNotHaveBean(E2eAgentInputRecorder.class);
                });
    }
}
