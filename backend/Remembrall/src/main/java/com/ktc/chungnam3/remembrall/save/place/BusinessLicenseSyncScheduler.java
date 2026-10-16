package com.ktc.chungnam3.remembrall.save.place;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

/**
 * 매일 변경분만 받아와서 {@link BusinessLicenseIngestService}로 반영한다. data.go.kr Open API가
 * "D-2까지 변경분"을 주는 구조라, 이틀 전 날짜를 기준으로 조회한다. {@code @EnableScheduling}은
 * {@link SchedulingConfig}에 분리해뒀다.
 */
@Slf4j
@Component
public class BusinessLicenseSyncScheduler {

    private static final int CHANGE_LIST_LAG_DAYS = 2;

    private final BusinessLicenseSource source;
    private final BusinessLicenseIngestService ingestService;
    private final Clock clock;

    public BusinessLicenseSyncScheduler(BusinessLicenseSource source, BusinessLicenseIngestService ingestService,
                                         Clock clock) {
        this.source = source;
        this.ingestService = ingestService;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 4 * * *")
    public void syncDailyChanges() {
        LocalDate since = LocalDate.now(clock).minusDays(CHANGE_LIST_LAG_DAYS);
        log.info("지방행정인허가 변경분 동기화 시작: {} 이후", since);
        int ingested = ingestService.ingest(source.fetchChangesSince(since));
        log.info("지방행정인허가 변경분 동기화 완료: {}건", ingested);
    }
}
