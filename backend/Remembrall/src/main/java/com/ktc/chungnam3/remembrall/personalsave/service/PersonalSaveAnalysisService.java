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
import java.util.UUID;
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

    public void submit(UUID contentId, String videoId) {
        try {
            executor.execute(() -> analyze(contentId, videoId));
        } catch (RejectedExecutionException exception) {
            transactions.executeWithoutResult(transaction -> contents.releaseAnalysisClaim(contentId, clock.instant()));
            log.warn("Analysis submission rejected; claim released: contentId={}", contentId);
        }
    }

    private void analyze(UUID contentId, String videoId) {
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
