package com.ktc.chungnam3.remembrall.domain.confirm;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import com.ktc.chungnam3.remembrall.domain.content.Content;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * 되묻기(NEEDS_CONFIRMATION) 후보 하나를 영속화한 행 - {@code save.dto.ConfirmRequestDto}(질문+후보
 * 리스트)를 그대로 JSON 직렬화해 {@link #confirmRequest}에 저장한다. 조회 전용 데이터라 JPA 관계로
 * 안 쪼개고 텍스트 그대로 둔다(읽는 쪽에서 역직렬화).
 */
@Getter
@Entity
@Table(name = "confirm_candidate", uniqueConstraints = @UniqueConstraint(
        name = "uk_confirm_candidate_content_candidate",
        columnNames = {"content_id", "candidate_id"}
))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConfirmCandidate {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "content_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_confirm_candidate_content")
    )
    private Content content;

    @Column(name = "candidate_id", nullable = false, length = 50)
    private String candidateId;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "confirm_request", nullable = false, columnDefinition = "text")
    private String confirmRequest;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ConfirmCandidateStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "answered_at")
    private Instant answeredAt;
}
