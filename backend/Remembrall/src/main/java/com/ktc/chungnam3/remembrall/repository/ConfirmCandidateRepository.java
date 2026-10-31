package com.ktc.chungnam3.remembrall.repository;

import com.ktc.chungnam3.remembrall.domain.confirm.ConfirmCandidate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConfirmCandidateRepository extends JpaRepository<ConfirmCandidate, UUID> {

    /**
     * 재분석으로 같은 후보(content_id+candidate_id)가 다시 나오면 질문/후보 목록을 최신으로
     * 갱신하고 PENDING으로 되돌린다 - 지금 분석 재시도는 FAILED 콘텐츠에서만 일어나(claimAnalysis
     * 참고) 실제로 ANSWERED 행을 덮어쓸 일은 없지만, 재실행 안전성을 위해 upsert로 둔다.
     * <p>
     * {@code @Transactional} 필요 - 호출부({@code SavePipelineService.saveConfirmCandidates})에
     * 트랜잭션이 없어 네이티브 {@code @Modifying} 쿼리가 "No active transaction" 에러로 운영에서
     * 완전히 실패하던 버그(PR 리뷰로 발견, 2026-10-29). {@code @DataJpaTest}가 테스트 전체를
     * 트랜잭션으로 감싸줘서 지금까지 테스트로는 안 드러났었다.
     */
    @Transactional
    @Modifying
    @Query(value = """
            INSERT INTO confirm_candidate (id, content_id, candidate_id, description, confirm_request, status, created_at)
            VALUES (:id, :contentId, :candidateId, :description, :confirmRequest, 'PENDING', :now)
            ON CONFLICT (content_id, candidate_id) DO UPDATE
                SET description = EXCLUDED.description,
                    confirm_request = EXCLUDED.confirm_request,
                    status = 'PENDING',
                    answered_at = NULL
            """, nativeQuery = true)
    void upsert(
            @Param("id") UUID id,
            @Param("contentId") UUID contentId,
            @Param("candidateId") String candidateId,
            @Param("description") String description,
            @Param("confirmRequest") String confirmRequest,
            @Param("now") Instant now
    );

    @Query("SELECT c FROM ConfirmCandidate c WHERE c.content.id = :contentId AND c.status = 'PENDING'")
    List<ConfirmCandidate> findPendingByContentId(@Param("contentId") UUID contentId);

    Optional<ConfirmCandidate> findByContent_IdAndCandidateId(UUID contentId, String candidateId);

    /**
     * clearAutomatically/flushAutomatically 필요 - {@code ConfirmAnswerService.answer}가 같은
     * 트랜잭션 안에서 {@code findByContent_IdAndCandidateId}로 먼저 읽은 뒤 이 네이티브 UPDATE를
     * 호출하는데, 기본값(false)이면 영속성 컨텍스트가 비워지지 않아 이후 재조회가 갱신 전 캐시된
     * 값을 돌려준다(RecallExecutionRepository의 상태 전이 쿼리들과 같은 이유).
     * <p>
     * {@code @Transactional}도 필요 - 호출부({@code ConfirmAnswerService.answer})에 트랜잭션이
     * 없어 네이티브 {@code @Modifying} 쿼리가 "No active transaction" 에러로 운영에서 완전히
     * 실패하던 버그(PR 리뷰로 발견, 2026-10-29).
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE confirm_candidate SET status = 'ANSWERED', answered_at = :answeredAt
             WHERE id = :id AND status = 'PENDING'
            """, nativeQuery = true)
    int markAnswered(@Param("id") UUID id, @Param("answeredAt") Instant answeredAt);
}
