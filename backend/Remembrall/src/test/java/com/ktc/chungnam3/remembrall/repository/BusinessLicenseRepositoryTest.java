package com.ktc.chungnam3.remembrall.repository;

import com.ktc.chungnam3.remembrall.domain.localdata.BusinessLicense;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE;

/**
 * V13 마이그레이션(business_license 테이블)이 실제 Postgres에서 upsert/반경검색까지 정확히
 * 동작하는지 확인한다 - 좌표는 2026-10-08 실측으로 검증된 실제 변환값(스타벅스 리저브광화문)을 쓴다.
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
class BusinessLicenseRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-10-08T00:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres")
    );

    @Autowired
    private BusinessLicenseRepository repository;

    @Test
    void 저장한_업소를_반경검색으로_찾는다() {
        // 광화문(실측 검증된 좌표) 기준 200m 안 - 스타벅스 리저브광화문
        repository.upsert(UUID.randomUUID(), "mng-1", "스타벅스 리저브광화문", "커피전문점", "영업/정상",
                "서울특별시 종로구 세종대로 178", 37.5723, 126.9778, NOW);
        // 반경 밖 - 부산 (멀리 떨어진 좌표)
        repository.upsert(UUID.randomUUID(), "mng-2", "스타벅스 부산서면점", "커피전문점", "영업/정상",
                "부산광역시 부산진구 서면", 35.1580, 129.0599, NOW);

        var nearby = repository.findWithinRadius(37.5723, 126.9778, 200);

        assertThat(nearby).extracting(BusinessLicense::getBusinessName)
                .containsExactly("스타벅스 리저브광화문");
    }

    @Test
    void 같은_source_id로_다시_upsert하면_id는_유지되고_내용만_갱신된다() {
        UUID id = UUID.randomUUID();
        repository.upsert(id, "mng-dup", "맥도날드 태평빌딩점", "패스트푸드", "영업/정상",
                "서울특별시 중구 세종대로 55", 37.564, 126.977, NOW);

        // source_id 동일, id는 새 값으로 넣어봐도(충돌 시 EXCLUDED에 id가 없어 기존 id 유지돼야 함)
        repository.upsert(UUID.randomUUID(), "mng-dup", "맥도날드 태평빌딩점(수정)", "패스트푸드", "폐업",
                "서울특별시 중구 세종대로 55", 37.564, 126.977, NOW.plusSeconds(60));

        var all = repository.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getId()).isEqualTo(id);
        assertThat(all.get(0).getBusinessName()).isEqualTo("맥도날드 태평빌딩점(수정)");
        assertThat(all.get(0).getLicenseStatus()).isEqualTo("폐업");
    }
}
