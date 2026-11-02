package com.ktc.chungnam3.remembrall.save;

import com.ktc.chungnam3.remembrall.auth.security.AuthenticatedMember;
import com.ktc.chungnam3.remembrall.save.confirm.ConfirmAnswerService;
import com.ktc.chungnam3.remembrall.save.dto.ConfirmAnswerRequestDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 되묻기(NEEDS_CONFIRMATION) 후보에 대한 답변 제출 - {@code personalSaveId} 기준 경로로 받아
 * {@link ConfirmAnswerService}에서 호출한 회원이 실제로 그 저장물의 주인인지 확인한다(PR 리뷰로
 * 발견된 버그 수정 - 예전엔 contentId만 받아서 로그인한 아무나 남의 저장물에 답변을 제출할 수 있었음).
 */
@RestController
@RequestMapping("/api/personal-saves/{personalSaveId}/places/{candidateId}")
public class ConfirmAnswerController {

    private final ConfirmAnswerService confirmAnswerService;

    public ConfirmAnswerController(ConfirmAnswerService confirmAnswerService) {
        this.confirmAnswerService = confirmAnswerService;
    }

    @PostMapping("/confirm")
    public ResponseEntity<Void> confirm(
            @AuthenticationPrincipal AuthenticatedMember member,
            @PathVariable UUID personalSaveId,
            @PathVariable String candidateId,
            @RequestBody ConfirmAnswerRequestDto request
    ) {
        confirmAnswerService.answer(member.memberId(), personalSaveId, candidateId, request.selectedOptionId());
        return ResponseEntity.noContent().build();
    }
}
