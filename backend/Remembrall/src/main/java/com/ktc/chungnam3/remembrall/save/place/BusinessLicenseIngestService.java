package com.ktc.chungnam3.remembrall.save.place;

import com.ktc.chungnam3.remembrall.repository.BusinessLicenseRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * {@link BusinessLicenseBulkLoader}(최초 1회 적재)와 {@link BusinessLicenseSyncScheduler}(매일 델타
 * 동기화)가 공유하는 적재 로직. 영업상태가 "영업중"이 아닌 행(폐업·휴업)은 저장하지 않고, 이미
 * 저장돼 있었다면 지운다(리뷰 반영 - 폐업 전환이 델타 동기화로 들어오면 기존 행을 삭제해야
 * {@code findWithinRadius}의 "이 테이블엔 영업중인 곳만 있다"는 전제가 계속 성립한다). 이 판단을
 * 한 곳에만 둬서, 데이터셋마다 조금씩 다른 상태 표기 문자열을 여기서만 관리하면 된다.
 */
@Slf4j
@Service
public class BusinessLicenseIngestService {

    // 행정안전부 표준데이터 공통 표기 - 실제 응답 확인되면 재검증 필요(place-resolution-history 스킬 참고).
    private static final Set<String> OPERATING_STATUSES = Set.of("영업", "영업/정상", "정상");

    private final BusinessLicenseRepository repository;
    private final Clock clock;

    public BusinessLicenseIngestService(BusinessLicenseRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    // @DataJpaTest가 테스트 메서드를 통째로 트랜잭션으로 감싸주는 바람에, 네이티브 @Modifying 쿼리인
    // upsert()에 트랜잭션이 실제로 필요하다는 게 테스트로는 안 드러났었다(2026-10-08, 실제 Spring
    // 컨텍스트로 BusinessLicenseBulkLoader를 처음 돌려보다가 TransactionRequiredException으로 발견).
    @Transactional
    public int ingest(List<BusinessLicenseRecord> records) {
        Instant now = clock.instant();
        int ingested = 0;
        for (BusinessLicenseRecord record : records) {
            if (!OPERATING_STATUSES.contains(record.licenseStatus())) {
                repository.deleteBySourceId(record.sourceId());
                log.info("지방행정인허가 폐업 반영: sourceId={} status={}", record.sourceId(), record.licenseStatus());
                continue;
            }
            repository.upsert(
                    UUID.randomUUID(),
                    record.sourceId(),
                    record.businessName(),
                    record.category(),
                    record.licenseStatus(),
                    record.roadAddress(),
                    record.latitude(),
                    record.longitude(),
                    now
            );
            ingested++;
        }
        log.info("지방행정인허가 적재 완료: 입력 {}건 중 {}건 저장(영업중만)", records.size(), ingested);
        return ingested;
    }
}
