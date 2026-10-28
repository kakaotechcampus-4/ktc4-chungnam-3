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
 * 되묻기(NEEDS_CONFIRMATION) 후보에 대한 답변 제출 - {@code /api/personal-saves}가 아직 없어서
 * (담당 팀원 작업 중), 이미 응답에 내려가는 contentId로 바로 받는다. 그 엔드포인트가 생기면 경로를
 * 거기 맞춰 옮길지는 담당자와 조율 필요.
 */
@RestController
@RequestMapping("/api/contents/{contentId}/places/{candidateId}")
public class ConfirmAnswerController {

    private final ConfirmAnswerService confirmAnswerService;

    public ConfirmAnswerController(ConfirmAnswerService confirmAnswerService) {
        this.confirmAnswerService = confirmAnswerService;
    }

    @PostMapping("/confirm")
    public ResponseEntity<Void> confirm(
            @AuthenticationPrincipal AuthenticatedMember member,
            @PathVariable UUID contentId,
            @PathVariable String candidateId,
            @RequestBody ConfirmAnswerRequestDto request
    ) {
        confirmAnswerService.answer(contentId, candidateId, request.selectedOptionId());
        return ResponseEntity.noContent().build();
    }
}
