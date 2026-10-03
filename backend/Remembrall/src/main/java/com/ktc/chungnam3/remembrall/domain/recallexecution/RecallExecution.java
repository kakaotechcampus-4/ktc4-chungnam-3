package com.ktc.chungnam3.remembrall.domain.recallexecution;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Getter
@Entity
@Table(name = "recall_execution", uniqueConstraints = @UniqueConstraint(
        name = "uk_recall_execution_trigger_event", columnNames = {"trigger_id", "trigger_event_id"}
))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecallExecution {

    @Id
    private UUID id;

    @Column(name = "trigger_id", nullable = false)
    private UUID triggerId;

    @Column(name = "trigger_event_id", nullable = false)
    private UUID triggerEventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RecallExecutionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", length = 20)
    private RecallExecutionResult result;

    @Enumerated(EnumType.STRING)
    @Column(name = "proposal_type", length = 30)
    private ProposalType proposalType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "proposal_payload", columnDefinition = "jsonb")
    private Map<String, Object> proposalPayload;

    @Column(name = "decision_summary", columnDefinition = "text")
    private String decisionSummary;

    @Column(name = "failure_code", length = 50)
    private String failureCode;

    @Column(name = "agent_version", nullable = false, length = 50)
    private String agentVersion;

    @Column(name = "event_occurred_at", nullable = false)
    private Instant eventOccurredAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
