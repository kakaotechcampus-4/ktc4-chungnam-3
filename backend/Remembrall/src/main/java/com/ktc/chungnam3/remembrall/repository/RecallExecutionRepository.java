package com.ktc.chungnam3.remembrall.repository;

import com.ktc.chungnam3.remembrall.domain.recallexecution.RecallExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RecallExecutionRepository extends JpaRepository<RecallExecution, UUID> {

    boolean existsByTriggerIdAndTriggerEventId(UUID triggerId, UUID triggerEventId);

    @Query(value = """
            SELECT EXISTS (
                SELECT 1 FROM recall_execution execution
                JOIN trigger ON trigger.id = execution.trigger_id
                WHERE trigger.member_id = :memberId AND execution.created_at > :cutoff
            )
            """, nativeQuery = true)
    boolean existsRecentByMemberId(@Param("memberId") UUID memberId, @Param("cutoff") Instant cutoff);

    Optional<RecallExecution> findByTriggerIdAndTriggerEventId(UUID triggerId, UUID triggerEventId);

    @Modifying
    @Query(value = """
            INSERT INTO recall_execution (
                id, trigger_id, trigger_event_id, status, agent_version,
                event_occurred_at, created_at, updated_at
            )
            VALUES (:id, :triggerId, :eventId, 'PENDING', :agentVersion, :eventOccurredAt, :now, :now)
            ON CONFLICT (trigger_id, trigger_event_id) DO NOTHING
            """, nativeQuery = true)
    int insertPendingIfAbsent(
            @Param("id") UUID id,
            @Param("triggerId") UUID triggerId,
            @Param("eventId") UUID eventId,
            @Param("agentVersion") String agentVersion,
            @Param("eventOccurredAt") Instant eventOccurredAt,
            @Param("now") Instant now
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE recall_execution
               SET status = 'RUNNING', started_at = :now, updated_at = :now
             WHERE id = :id AND status = 'PENDING'
            """, nativeQuery = true)
    int start(@Param("id") UUID id, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE recall_execution
               SET status = 'COMPLETED', result = 'NO_ACTION', finished_at = :now, updated_at = :now
             WHERE id = :id AND status = 'RUNNING'
            """, nativeQuery = true)
    int completeNoAction(@Param("id") UUID id, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE recall_execution
               SET status = 'COMPLETED', result = 'PROPOSE', proposal_type = :proposalType,
                   proposal_payload = CAST(:payload AS jsonb), decision_summary = :summary,
                   finished_at = :now, updated_at = :now
             WHERE id = :id AND status = 'RUNNING'
            """, nativeQuery = true)
    int completePropose(@Param("id") UUID id, @Param("proposalType") String proposalType,
                        @Param("payload") String payload, @Param("summary") String summary,
                        @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE recall_execution
               SET status = 'FAILED', failure_code = :failureCode, finished_at = :now, updated_at = :now
             WHERE id = :id AND status = :expectedStatus
            """, nativeQuery = true)
    int fail(
            @Param("id") UUID id,
            @Param("expectedStatus") String expectedStatus,
            @Param("failureCode") String failureCode,
            @Param("now") Instant now
    );
}
