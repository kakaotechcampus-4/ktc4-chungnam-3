package com.ktc.chungnam3.remembrall.recall.service;

import com.ktc.chungnam3.remembrall.domain.recallexecution.RecallExecutionStatus;
import com.ktc.chungnam3.remembrall.domain.recallexecution.RecallExecutionResult;
import com.ktc.chungnam3.remembrall.notification.config.NotificationProperties;
import com.ktc.chungnam3.remembrall.recall.agent.RecallAgentResult;
import com.ktc.chungnam3.remembrall.recall.agent.RecallAgent;
import com.ktc.chungnam3.remembrall.repository.NotificationRepository;
import com.ktc.chungnam3.remembrall.repository.TriggerRepository;
import com.ktc.chungnam3.remembrall.repository.RecallExecutionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class RecallExecutionService {

    private final RecallExecutionRepository repository;
    private final Clock clock;
    private final TransactionTemplate transactions;
    private final TriggerRepository triggers;
    private final NotificationRepository notifications;
    private final ProposalValidator validator;
    private final NotificationProperties properties;
    private final ObjectMapper mapper;
    private final RecallAgent agent;
    private final RecallAgentInputService inputs;

    public RecallExecutionService(RecallExecutionRepository repository, Clock clock,
                                  PlatformTransactionManager transactionManager, TriggerRepository triggers,
                                  NotificationRepository notifications, ProposalValidator validator,
                                  NotificationProperties properties, ObjectMapper mapper,
                                  RecallAgent agent, RecallAgentInputService inputs) {
        this.repository = repository;
        this.clock = clock;
        this.triggers = triggers;
        this.notifications = notifications;
        this.validator = validator;
        this.properties = properties;
        this.mapper = mapper;
        this.agent = agent;
        this.inputs = inputs;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public boolean start(UUID executionId) {
        return Boolean.TRUE.equals(transactions.execute(status -> repository.start(executionId, clock.instant()) == 1));
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public UUID execute(UUID executionId) throws Exception {
        var input = inputs.prepare(executionId);
        return complete(executionId, agent.execute(executionId, input));
    }

    public boolean completeNoAction(UUID executionId) {
        return Boolean.TRUE.equals(transactions.execute(status ->
                repository.completeNoAction(executionId, clock.instant()) == 1));
    }

    public UUID complete(UUID executionId, RecallAgentResult result) {
        return transactions.execute(status -> {
            var execution = repository.findById(executionId).orElse(null);
            if (execution == null || execution.getStatus() != RecallExecutionStatus.RUNNING) {
                return null;
            }
            Instant now = clock.instant();
            if (result == null || result.result() == null) {
                repository.fail(executionId, RecallExecutionStatus.RUNNING.name(), "INVALID_AGENT_RESULT", now);
                return null;
            }
            if (result.result() == RecallExecutionResult.NO_ACTION) {
                repository.completeNoAction(executionId, now);
                return null;
            }
            var trigger = triggers.findById(execution.getTriggerId()).orElse(null);
            if (trigger == null) {
                return null;
            }
            UUID memberId = trigger.getMemberId();
            if (!validator.valid(memberId, trigger.getPlaceId(), result)) {
                repository.fail(executionId, RecallExecutionStatus.RUNNING.name(), "INVALID_PROPOSAL", now);
                return null;
            }
            if (repository.completePropose(executionId, result.proposalType().name(),
                    mapper.writeValueAsString(result.proposalPayload()), result.decisionSummary(), now) != 1) {
                return null;
            }
            UUID notificationId = UUID.randomUUID();
            notifications.insertPending(notificationId, executionId, memberId,
                    truncate(result.title(), properties.maxTitleLength()),
                    truncate(result.body(), properties.maxBodyLength()), now);
            return notificationId;
        });
    }

    private String truncate(String text, int maximum) {
        int length = text.codePointCount(0, text.length());
        return length <= maximum ? text : text.substring(0, text.offsetByCodePoints(0, maximum));
    }

    public void fail(UUID executionId, String failureCode) {
        transactions.executeWithoutResult(status -> {
            Instant now = clock.instant();
            if (repository.fail(executionId, RecallExecutionStatus.PENDING.name(), failureCode, now) == 0) {
                repository.fail(executionId, RecallExecutionStatus.RUNNING.name(), failureCode, now);
            }
        });
    }
}
