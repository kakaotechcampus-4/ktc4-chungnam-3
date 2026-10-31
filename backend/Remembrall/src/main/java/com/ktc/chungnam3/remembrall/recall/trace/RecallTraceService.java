package com.ktc.chungnam3.remembrall.recall.trace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Clock;
import java.util.UUID;

public class RecallTraceService {
    private static final Logger log = LoggerFactory.getLogger(RecallTraceService.class);
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final ObjectMapper mapper;
    private final RecallTraceProperties properties;

    public RecallTraceService(JdbcTemplate jdbc, PlatformTransactionManager transactionManager,
                               Clock clock, ObjectMapper mapper, RecallTraceProperties properties) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.mapper = mapper;
        this.properties = properties;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public RecallTraceCollector start(UUID executionId) {
        return new RecallTraceCollector(executionId, clock, mapper, properties);
    }

    public void notificationFinished(RecallTraceCollector collector, RecallTraceCollector.Span span,
                                      UUID notificationId, Throwable error) {
        try {
            var rows = jdbc.query("SELECT status, failure_code FROM notification WHERE id = ?",
                    (rs, index) -> new NotificationState(rs.getString("status"), rs.getString("failure_code")), notificationId);
            var state = rows.isEmpty() ? new NotificationState(null, null) : rows.getFirst();
            collector.notification(span, notificationId, state.status(), state.failureCode(), error);
        } catch (RuntimeException exception) {
            collector.notification(span, notificationId, null, null, error == null ? exception : error);
            log.warn("Could not read notification state for recall trace {}", collector.executionId(), exception);
        }
    }

    public void save(RecallTraceCollector collector) {
        try {
            var entries = collector.entries();
            if (entries.isEmpty()) {
                return;
            }
            transactions.executeWithoutResult(status -> jdbc.batchUpdate("""
                    INSERT INTO recall_execution_trace
                        (id, execution_id, seq, step, name, started_at, duration_ms, success,
                         error_message, input_tokens, output_tokens, detail)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb))
                    """, entries, entries.size(), (statement, entry) -> {
                statement.setObject(1, entry.id());
                statement.setObject(2, entry.executionId());
                statement.setInt(3, entry.seq());
                statement.setString(4, entry.step().name());
                statement.setString(5, entry.name());
                statement.setTimestamp(6, Timestamp.from(entry.startedAt()));
                statement.setDouble(7, entry.durationMs());
                statement.setBoolean(8, entry.success());
                statement.setString(9, entry.errorMessage());
                statement.setObject(10, entry.inputTokens(), Types.INTEGER);
                statement.setObject(11, entry.outputTokens(), Types.INTEGER);
                statement.setString(12, mapper.writeValueAsString(entry.detail()));
            }));
        } catch (RuntimeException exception) {
            log.warn("Could not persist recall trace for execution {}", collector.executionId(), exception);
        }
    }

    private record NotificationState(String status, String failureCode) {}
}
