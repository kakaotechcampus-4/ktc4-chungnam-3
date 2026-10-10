package com.ktc.chungnam3.remembrall.save.confirm;

import org.springframework.stereotype.Component;

/**
 * 되묻기는 콘텐츠당 최대 1회 (저장 파이프라인.md 3장 "핵심 원칙"). 파이프라인은 무상태라
 * "이미 물어봤는지"는 매 호출마다 Spring이 넘겨준 값을 그대로 따른다.
 * <p>
 * 이미 물어봤는데 미응답이면(이 메서드가 false를 반환하면) "상위 지역으로 저장"하기로 돼 있는데
 * (저장 파이프라인.md 4장), 그 저장 로직은 {@link com.ktc.chungnam3.remembrall.save.SavePipelineService
 * #resolveBroaderRegion}에서 처리한다.
 */
@Component
public class ConfirmPolicy {

    public boolean shouldAsk(boolean alreadyAsked) {
        return !alreadyAsked;
    }
}
