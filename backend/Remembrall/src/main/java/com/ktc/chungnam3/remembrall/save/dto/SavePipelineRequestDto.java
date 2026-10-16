package com.ktc.chungnam3.remembrall.save.dto;

/**
 * SavePipelineService 입력.
 * <p>
 * alreadyAsked: 콘텐츠 전체에 적용되는 단순 boolean으로 둔다(의도적 단순화) - 원래 "이미 물어봤는지"는
 * 장소 후보별로 다를 수 있지만, 후보별로 되묻기 이력을 추적하는 저장소가 아직 없다(API 명세.md
 * "아직 안 된 것" 참고). 필요해지면 이 필드를 바꾸거나 요청 DTO 자체를 바꾼다.
 */
public record SavePipelineRequestDto(
        String youtubeUrl,

        boolean alreadyAsked

) {
}
