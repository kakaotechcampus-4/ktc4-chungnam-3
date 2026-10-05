package com.ktc.chungnam3.remembrall.repository;

import com.ktc.chungnam3.remembrall.domain.notification.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    Optional<Notification> findByIdAndMemberId(UUID id, UUID memberId);

    Optional<Notification> findByExecutionId(UUID executionId);

    boolean existsByIdAndMemberId(UUID id, UUID memberId);

    @Modifying
    @Query(value = """
            INSERT INTO notification (id, execution_id, member_id, title, body, status, created_at, updated_at)
            VALUES (:id, :executionId, :memberId, :title, :body, 'PENDING', :now, :now)
            """, nativeQuery = true)
    int insertPending(@Param("id") UUID id, @Param("executionId") UUID executionId,
                      @Param("memberId") UUID memberId, @Param("title") String title,
                      @Param("body") String body, @Param("now") Instant now);

    @Modifying
    @Query(value = """
            UPDATE notification SET status = 'SENDING', updated_at = :now
             WHERE id = :id AND status = 'PENDING'
            """, nativeQuery = true)
    int claimSending(@Param("id") UUID id, @Param("now") Instant now);

    @Modifying
    @Query(value = """
            UPDATE notification SET status = 'SENT', provider_message_id = :messageId,
                   sent_at = :now, updated_at = :now
             WHERE id = :id AND status = 'SENDING'
            """, nativeQuery = true)
    int markSent(@Param("id") UUID id, @Param("messageId") String messageId, @Param("now") Instant now);

    @Modifying
    @Query(value = """
            UPDATE notification SET status = 'FAILED', failure_code = :code, updated_at = :now
             WHERE id = :id AND status = 'SENDING'
            """, nativeQuery = true)
    int markFailed(@Param("id") UUID id, @Param("code") String code, @Param("now") Instant now);

    @Modifying
    @Query(value = """
            UPDATE notification SET opened_at = :now, updated_at = :now
             WHERE id = :id AND member_id = :memberId AND opened_at IS NULL
            """, nativeQuery = true)
    int openIfUnopened(@Param("id") UUID id, @Param("memberId") UUID memberId, @Param("now") Instant now);
}
