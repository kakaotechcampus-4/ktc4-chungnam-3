package com.ktc.chungnam3.remembrall.recall.service;

import com.ktc.chungnam3.remembrall.domain.trigger.Trigger;
import com.ktc.chungnam3.remembrall.recall.agent.RecallAgent;
import com.ktc.chungnam3.remembrall.recall.config.RecallProperties;
import com.ktc.chungnam3.remembrall.recall.dto.LocationEventRequest;
import com.ktc.chungnam3.remembrall.repository.RecallExecutionRepository;
import com.ktc.chungnam3.remembrall.repository.TriggerRepository;
import com.ktc.chungnam3.remembrall.repository.MemberRepository;
import com.ktc.chungnam3.remembrall.notification.service.NotificationDeliveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class LocationEventService {

    private static final Logger log = LoggerFactory.getLogger(LocationEventService.class);
    private static final String AGENT_FAILURE_CODE = "AGENT_EXECUTION_ERROR";

    private final TriggerRepository triggerRepository;
    private final RecallExecutionRepository executionRepository;
    private final RecallExecutionService executionService;
    private final RecallAgent agent;
    private final RecallProperties properties;
    private final Clock clock;
    private final TransactionTemplate transactions;
    private final MemberRepository members;
    private final NotificationDeliveryService delivery;

    public LocationEventService(TriggerRepository triggerRepository, RecallExecutionRepository executionRepository,
                                RecallExecutionService executionService, RecallAgent agent, RecallProperties properties,
                                Clock clock, PlatformTransactionManager transactionManager,
                                MemberRepository members, NotificationDeliveryService delivery) {
        this.triggerRepository = triggerRepository;
        this.executionRepository = executionRepository;
        this.executionService = executionService;
        this.agent = agent;
        this.properties = properties;
        this.clock = clock;
        this.members = members;
        this.delivery = delivery;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void accept(UUID memberId, LocationEventRequest event) {
        UUID executionId = transactions.execute(status -> createPending(memberId, event));
        if (executionId == null) {
            return;
        }

        try {
            if (!executionService.start(executionId)) {
                return;
            }
            UUID notificationId = executionService.complete(executionId, agent.execute(executionId, event));
            if (notificationId != null) {
                delivery.send(notificationId);
            }
        } catch (Exception exception) {
            log.warn("Recall agent failed for execution {}", executionId, exception);
            executionService.fail(executionId, AGENT_FAILURE_CODE);
        }
    }

    private UUID createPending(UUID memberId, LocationEventRequest event) {
        if (members.findByIdForUpdate(memberId).isEmpty()) {
            return null;
        }
        Trigger trigger = triggerRepository.findByMemberIdAndPlaceId(memberId, event.placeId())
                .orElse(null);
        if (trigger == null) {
            return null;
        }

        Instant now = clock.instant();
        if (executionRepository.existsByTriggerIdAndTriggerEventId(trigger.getId(), event.triggerEventId())) {
            return null;
        }
        if (event.eventOccurredAt().isBefore(now.minus(properties.maxEventAge()))) {
            return null;
        }
        if (executionRepository.existsRecentByMemberId(memberId, now.minus(properties.memberCooldown()))) {
            return null;
        }

        UUID executionId = UUID.randomUUID();
        int inserted = executionRepository.insertPendingIfAbsent(
                executionId, trigger.getId(), event.triggerEventId(), properties.agentVersion(), event.eventOccurredAt(), now);
        return inserted == 1 ? executionId : null;
    }
}
