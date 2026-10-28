package com.ktc.chungnam3.remembrall.recall.service;

import com.ktc.chungnam3.remembrall.domain.recallexecution.RecallExecutionStatus;
import com.ktc.chungnam3.remembrall.domain.recallexecution.RecallExecutionResult;
import com.ktc.chungnam3.remembrall.notification.config.NotificationProperties;
import com.ktc.chungnam3.remembrall.recall.agent.RecallAgentResult;
import com.ktc.chungnam3.remembrall.recall.agent.RecallAgent;
import com.ktc.chungnam3.remembrall.recall.agent.LlmRecallAgent;
import com.ktc.chungnam3.remembrall.recall.agent.RecallAgentInput;
import com.ktc.chungnam3.remembrall.recall.trace.RecallTraceCollector;
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
import java.util.concurrent.atomic.AtomicReference;

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
        return execute(executionId, null);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public UUID execute(UUID executionId, RecallTraceCollector trace) throws Exception {
        if (trace == null) {
            var input = inputs.prepare(executionId);
            return complete(executionId, agent.execute(executionId, input));
        }
        var preparing = trace.begin(RecallTraceCollector.Step.CANDIDATE_PREPARATION, "recall-candidates");
        RecallAgentInput input = null;
        Throwable preparingError = null;
        try {
            input = inputs.prepare(executionId);
        } catch (RuntimeException | Error exception) {
            preparingError = exception;
            throw exception;
        } finally {
            trace.candidates(preparing, input, preparingError);
        }
        if (agent.recordsModelCalls()) {
            return complete(executionId, agent.executeWithStatistics(executionId, input, trace).result(), trace);
        }
        String modelName = agent.modelName();
        var calling = trace.begin(RecallTraceCollector.Step.MODEL_CALL, modelName);
        RecallAgent.Execution invocation = null;
        Throwable modelError = null;
        try {
            invocation = agent.executeWithStatistics(executionId, input);
        } catch (LlmRecallAgent.CallException exception) {
            var failed = exception.invocation();
            if (failed != null) {
                invocation = new RecallAgent.Execution(failed.result(), failed.responseTimeMillis(),
                        failed.inputTokens(), failed.outputTokens());
            }
            modelError = exception;
            throw exception;
        } catch (Exception | Error exception) {
            modelError = exception;
            throw exception;
        } finally {
            trace.model(calling, modelName, input, invocation == null ? null : invocation.responseTimeMillis(),
                    invocation == null ? null : invocation.inputTokens(),
                    invocation == null ? null : invocation.outputTokens(), modelError);
        }
        return complete(executionId, invocation == null ? null : invocation.result(), trace);
    }

    public boolean completeNoAction(UUID executionId) {
        return Boolean.TRUE.equals(transactions.execute(status ->
                repository.completeNoAction(executionId, clock.instant()) == 1));
    }

    public UUID complete(UUID executionId, RecallAgentResult result) {
        return complete(executionId, result, null);
    }

    public UUID complete(UUID executionId, RecallAgentResult result, RecallTraceCollector trace) {
        var creation = new AtomicReference<NotificationCreation>();
        Throwable completionError = null;
        try {
            return transactions.execute(status -> {
                var execution = repository.findById(executionId).orElse(null);
                if (execution == null || execution.getStatus() != RecallExecutionStatus.RUNNING) {
                    return null;
                }
                Instant now = clock.instant();
                if (result == null || result.result() == null) {
                    if (trace != null) {
                        trace.validation(trace.begin(RecallTraceCollector.Step.VALIDATION, "proposal"), false,
                                new IllegalArgumentException("INVALID_AGENT_RESULT"));
                    }
                    repository.fail(executionId, RecallExecutionStatus.RUNNING.name(), "INVALID_AGENT_RESULT", now);
                    return null;
                }
                if (result.result() == RecallExecutionResult.NO_ACTION) {
                    if (trace != null) {
                        trace.validation(trace.begin(RecallTraceCollector.Step.VALIDATION, "no-action"), true, null);
                    }
                    repository.completeNoActionWithSummary(executionId, result.decisionSummary(), now);
                    return null;
                }
                var trigger = triggers.findById(execution.getTriggerId()).orElse(null);
                if (trigger == null) {
                    return null;
                }
                UUID memberId = trigger.getMemberId();
                if (!validProposal(memberId, trigger.getPlaceId(), result, trace)) {
                    repository.fail(executionId, RecallExecutionStatus.RUNNING.name(), "INVALID_PROPOSAL", now);
                    return null;
                }
                if (repository.completePropose(executionId, result.proposalType().name(),
                        mapper.writeValueAsString(result.proposalPayload()), result.decisionSummary(), now) != 1) {
                    return null;
                }
                UUID notificationId = UUID.randomUUID();
                if (trace != null) {
                    creation.set(new NotificationCreation(notificationId,
                            trace.begin(RecallTraceCollector.Step.NOTIFICATION_CREATE, "notification")));
                }
                notifications.insertPending(notificationId, executionId, memberId,
                        truncate(result.title(), properties.maxTitleLength()),
                        truncate(result.body(), properties.maxBodyLength()), now);
                return notificationId;
            });
        } catch (RuntimeException | Error exception) {
            completionError = exception;
            throw exception;
        } finally {
            var notification = creation.get();
            if (notification != null) {
                trace.notification(notification.span(), notification.id(),
                        completionError == null ? "PENDING" : null, null, completionError);
            }
        }
    }

    private boolean validProposal(UUID memberId, UUID triggerPlaceId, RecallAgentResult result, RecallTraceCollector trace) {
        var validating = trace == null ? null : trace.begin(RecallTraceCollector.Step.VALIDATION, "proposal");
        boolean valid = false;
        Throwable error = null;
        try {
            valid = validator.valid(memberId, triggerPlaceId, result);
            return valid;
        } catch (RuntimeException | Error exception) {
            error = exception;
            throw exception;
        } finally {
            if (trace != null) {
                trace.validation(validating, valid, error);
            }
        }
    }

    private record NotificationCreation(UUID id, RecallTraceCollector.Span span) {}

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
