package com.ktc.chungnam3.remembrall.save.place;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** {@link BusinessLicenseSyncScheduler}의 {@code @Scheduled}를 실제로 동작시키기 위해 필요하다 -
 * 프로젝트 전체에 아직 {@code @EnableScheduling}이 없어서 여기서 켠다. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
