package com.ktc.chungnam3.remembrall.personalsave.service;

import com.ktc.chungnam3.remembrall.repository.ContentRepository;
import com.ktc.chungnam3.remembrall.save.SavePipelineService;
import com.ktc.chungnam3.remembrall.save.dto.SavePipelineRequestDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;

@Service
public class PersonalSaveAnalysisService {
    private static final Logger log = LoggerFactory.getLogger(PersonalSaveAnalysisService.class);

    private final ThreadPoolTaskExecutor executor;
    private final ContentRepository contents;
    private final SavePipelineService pipeline;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public PersonalSaveAnalysisService(
            @Qualifier("personalSaveAnalysisExecutor") ThreadPoolTaskExecutor executor,
            ContentRepository contents, SavePipelineService pipeline, Clock clock,
            PlatformTransactionManager transactionManager) {
        this.executor = executor;
        this.contents = contents;
        this.pipeline = pipeline;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * 대기열에 들어갔지만 아직 분석 스레드가 꺼내지 않은 contentId. {@link StaleAnalysisRecoveryScheduler}가
     * 이 건들의 {@code analysis_started_at}을 갱신해서, 대기열에서 기다린 시간 때문에 시작도 전에 FAILED가
     * 되지 않게 한다(멘토 리뷰, 2026-11-07). 서버가 재시작되면 비어서, 유실된 대기 건은 원래대로 복구된다.
     * ponytail: 메모리 집합이라 서버 1대 전제 - 여러 대로 늘리면 DB에 "대기 중" 상태를 둬야 한다.
     */
    private final Set<UUID> queued = ConcurrentHashMap.newKeySet();

    public void submit(UUID contentId, String videoId) {
        queued.add(contentId);
        try {
            executor.execute(() -> analyze(contentId, videoId));
        } catch (RejectedExecutionException exception) {
            queued.remove(contentId);
            transactions.executeWithoutResult(transaction -> contents.releaseAnalysisClaim(contentId, clock.instant()));
            log.warn("Analysis submission rejected; claim released: contentId={}", contentId);
        }
    }

    public Set<UUID> queuedContentIds() {
        return Set.copyOf(queued);
    }

    private void analyze(UUID contentId, String videoId) {
        queued.remove(contentId);
        try {
            Integer started = transactions.execute(transaction -> contents.refreshAnalysisStartedAt(contentId, clock.instant()));
            if (!Integer.valueOf(1).equals(started)) {
                return;
            }
            pipeline.saveAndPersist(contentId, new SavePipelineRequestDto(sourceUrl(videoId)));
        } catch (Exception exception) {
            log.error("Analysis task failed: contentId={}", contentId, exception);
        }
    }

    public static String sourceUrl(String videoId) {
        return "https://www.youtube.com/watch?v=" + videoId;
    }
}
