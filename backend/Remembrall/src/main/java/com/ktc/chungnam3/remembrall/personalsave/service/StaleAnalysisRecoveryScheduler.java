package com.ktc.chungnam3.remembrall.personalsave.service;

import com.ktc.chungnam3.remembrall.personalsave.config.PersonalSaveProperties;
import com.ktc.chungnam3.remembrall.repository.ContentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * ANALYZING 멈춤 복구 - 분석 중 예외가 로그만 남기고 삼켜지거나 서버가 재시작되면
 * {@code analysis_status}가 ANALYZING에 영구히 남는다(원인 불문). 1분마다
 * {@code analysis_started_at}가 오래된 행만 FAILED + EXTRACTION_API_ERROR로 전환해서,
 * 기존 재시도 경로({@link ContentRepository#claimAnalysis})가 다음 저장 요청(또는 앱의
 * "다시 시도")에서 자연스럽게 다시 집어가게 한다.
 */
@Slf4j
@Component
public class StaleAnalysisRecoveryScheduler {

    private final ContentRepository contents;
    private final PersonalSaveAnalysisService analysis;
    private final PersonalSaveProperties properties;
    private final Clock clock;

    public StaleAnalysisRecoveryScheduler(ContentRepository contents, PersonalSaveAnalysisService analysis,
                                          PersonalSaveProperties properties, Clock clock) {
        this.contents = contents;
        this.analysis = analysis;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void failStaleAnalyses() {
        Instant now = clock.instant();
        // 아직 대기열에 있는 건은 시작 시각을 지금으로 갱신해 판정에서 뺀다 - 분석이 실제로 시작되면
        // refreshAnalysisStartedAt이 시작 시각으로 다시 찍으므로 5분은 실제 분석 시간만 센다(멘토 리뷰).
        analysis.queuedContentIds().forEach(id -> contents.refreshAnalysisStartedAt(id, now));
        Instant threshold = now.minus(properties.staleAnalyzingTimeout());
        int failed = contents.failStaleAnalyzing(threshold, now);
        if (failed > 0) {
            log.warn("ANALYZING 멈춤 복구: {}건 FAILED로 전환 (threshold={})", failed, threshold);
        }
    }
}
