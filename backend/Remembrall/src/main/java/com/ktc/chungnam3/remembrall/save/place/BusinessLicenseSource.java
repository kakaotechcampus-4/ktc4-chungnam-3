package com.ktc.chungnam3.remembrall.save.place;

import java.time.LocalDate;
import java.util.List;

/**
 * 지방행정인허가 Open API 호출 경계. 실제 구현체(data.go.kr REST 호출 + JSON 필드 매핑)는 아직
 * 작성 전이다 - data.go.kr 인증키 문제를 먼저 해결하고, 이 팀의 기존 관행대로 실제 응답을 1회성
 * 스크래치 테스트로 먼저 확인한 뒤에 정식 구현체를 붙인다(필드명을 추측해서 미리 짜지 않음 -
 * `DataportalStoreClient`/`VWorldSearchClient` 작업 때도 항상 이 순서로 했음).
 * <p>
 * {@link BusinessLicenseBulkLoader}/{@link BusinessLicenseSyncScheduler}는 이 인터페이스에만
 * 의존하므로, 구현체가 없어도(프로필로 안 활성화되는 한) 컴파일·테스트에는 영향 없다.
 */
public interface BusinessLicenseSource {

    /** 전체(전국) 데이터 - 최초 1회 적재용. */
    List<BusinessLicenseRecord> fetchAll();

    /** 지정한 날짜 이후 변경분만 - 매일 델타 동기화용(data.go.kr이 "D-2까지 변경분" 형태로 제공). */
    List<BusinessLicenseRecord> fetchChangesSince(LocalDate since);
}
