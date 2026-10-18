package com.ktc.chungnam3.remembrall.save;

import com.ktc.chungnam3.remembrall.auth.security.AuthenticatedMember;
import com.ktc.chungnam3.remembrall.common.exception.ApiException;
import com.ktc.chungnam3.remembrall.common.exception.ErrorCode;
import com.ktc.chungnam3.remembrall.content.dto.ContentSaveClaim;
import com.ktc.chungnam3.remembrall.content.service.ContentPersistenceService;
import com.ktc.chungnam3.remembrall.save.dto.SaveContentRequestDto;
import com.ktc.chungnam3.remembrall.save.dto.SaveContentResponseDto;
import com.ktc.chungnam3.remembrall.save.dto.SavePipelineRequestDto;
import com.ktc.chungnam3.remembrall.save.dto.SavePipelineResultDto;
import com.ktc.chungnam3.remembrall.save.youtube.YoutubeUrlParser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 저장 파이프라인의 REST 진입점 - "링크 저장"과 "분석→장소확정→저장"을 한 번의 요청으로 묶는다
 * (멘토 피드백: 담당자별 구현은 끝났는데 모듈 사이가 안 이어져 있다는 지적에 대한 대응, 2026-10-11).
 * 별도의 {@code /api/personal-saves} 엔드포인트가 아직 없어서, 이 컨트롤러가 그 역할까지 겸한다 -
 * 그 엔드포인트를 따로 만들 계획이 있다면 중복/충돌 방지를 위해 담당자와 조율이 필요하다.
 * <p>
 * 분석(Gemini 호출 포함)은 지금은 요청 스레드에서 동기로 실행한다(ponytail: 응답이 몇 초 걸릴 수 있음 -
 * 느려지면 그때 비동기/큐로 전환, 지금 미리 만들 필요 없음).
 */
@RestController
@RequestMapping("/api/contents")
public class SavePipelineController {

    private final ContentPersistenceService contentPersistenceService;
    private final SavePipelineService savePipelineService;

    public SavePipelineController(ContentPersistenceService contentPersistenceService,
                                   SavePipelineService savePipelineService) {
        this.contentPersistenceService = contentPersistenceService;
        this.savePipelineService = savePipelineService;
    }

    @PostMapping
    public SaveContentResponseDto save(
            @AuthenticationPrincipal AuthenticatedMember member,
            @Valid @RequestBody SaveContentRequestDto request
    ) {
        String videoId = YoutubeUrlParser.extractVideoId(request.youtubeUrl())
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_REQUEST));

        ContentSaveClaim claim = contentPersistenceService.saveAndClaim(member.memberId(), videoId);
        if (!claim.analysisClaimed()) {
            return SaveContentResponseDto.alreadyProcessed(claim.contentId());
        }

        SavePipelineResultDto result = savePipelineService.saveAndPersist(
                claim.contentId(), new SavePipelineRequestDto(request.youtubeUrl(), false));
        return SaveContentResponseDto.from(claim.contentId(), result);
    }
}
