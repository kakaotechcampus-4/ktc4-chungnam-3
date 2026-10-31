package com.ktc.chungnam3.remembrall.save.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * {@code POST /api/contents} 요청 본문. {@link SavePipelineRequestDto}와 따로 두는 이유: 전자는
 * REST 계약, 후자는 서비스 내부 입력이라 - 지금은 필드가 같지만 계층이 다르므로 합치지 않는다.
 */
public record SaveContentRequestDto(

        @NotBlank
        String youtubeUrl

) {
}
