package com.ktc.chungnam3.remembrall.save.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * {@code POST /api/contents} 요청 본문. {@link SavePipelineRequestDto}와 따로 두는 이유: 그쪽의
 * alreadyAsked는 파이프라인 내부 개념(되묻기 재시도 여부)이라 최초 저장 요청에는 없어야 한다 -
 * 컨트롤러가 항상 false로 채워 {@link SavePipelineRequestDto}를 만든다.
 */
public record SaveContentRequestDto(

        @NotBlank
        String youtubeUrl

) {
}
