package com.ktc.chungnam3.remembrall.save.place;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 전국 데이터 최초 1회 적재용. 평소 앱 구동·테스트에는 절대 안 돈다 - {@code business-license-load}
 * 프로필로만 활성화한다(예: {@code --spring.profiles.active=business-license-load}로 1회 실행 후
 * 다시 평소대로 돌림). 마이그레이션에는 안 넣는다(팀 결정 - 테스트 때마다 전체 데이터가 들어가면
 * 테스트가 느려짐).
 */
@Slf4j
@Component
@Profile("business-license-load")
public class BusinessLicenseBulkLoader implements CommandLineRunner {

    private final BusinessLicenseSource source;
    private final BusinessLicenseIngestService ingestService;

    public BusinessLicenseBulkLoader(BusinessLicenseSource source, BusinessLicenseIngestService ingestService) {
        this.source = source;
        this.ingestService = ingestService;
    }

    @Override
    public void run(String... args) {
        log.info("지방행정인허가 전국 데이터 최초 적재 시작");
        int ingested = ingestService.ingest(source.fetchAll());
        log.info("지방행정인허가 전국 데이터 최초 적재 완료: {}건", ingested);
    }
}
