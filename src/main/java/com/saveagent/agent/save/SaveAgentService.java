package com.saveagent.agent.save;

import com.saveagent.agent.save.dto.AgentResult;
import com.saveagent.agent.save.dto.ConfirmRequest;
import com.saveagent.agent.save.dto.ContentAnalysis;
import com.saveagent.agent.save.dto.Status;
import com.saveagent.agent.save.dto.TriggerSpec;

import java.util.List;
import java.util.Optional;

/**
 * 저장 에이전트 진입점. 분류 -> 되묻기 판정 -> 트리거 설계 순서로 조율한다.
 * <p>
 * 이 서비스는 입력(ContentAnalysis)을 받아 결과(AgentResult)를 돌려주기만 한다.
 * DB 저장, 지도/캘린더 API 호출, 트리거 발동 시 후보 선별은 모두 다른 담당의 몫이다.
 */
public class SaveAgentService {

    private final ClassifierService classifierService;
    private final TriggerDesigner triggerDesigner;
    private final ConfirmPolicy confirmPolicy;

    public SaveAgentService(ClassifierService classifierService,
                             TriggerDesigner triggerDesigner,
                             ConfirmPolicy confirmPolicy) {
        this.classifierService = classifierService;
        this.triggerDesigner = triggerDesigner;
        this.confirmPolicy = confirmPolicy;
    }

    /**
     * @param analysis     수집·요약 담당이 넘겨준 분석 결과
     * @param alreadyAsked 이 콘텐츠에 대해 이미 한 번 되물었는지 여부.
     *                     저장 에이전트는 상태를 갖지 않으므로 상위 저장 로직이 관리해서 넘겨줘야 한다.
     */
    public AgentResult process(ContentAnalysis analysis, boolean alreadyAsked) {
        ClassificationResult classification = classifierService.classify(analysis);

        Optional<ConfirmRequest> confirmRequest = confirmPolicy.evaluate(classification, alreadyAsked);
        if (confirmRequest.isPresent()) {
            // 되묻기가 필요하면 대기하지 않고 즉시 종료한다.
            // 사용자가 답하면 상위 로직이 이 서비스를 다시 호출한다.
            return new AgentResult(
                    Status.NEEDS_CONFIRM,
                    classification.category(),
                    List.of(),
                    classification.confidence(),
                    confirmRequest.get()
            );
        }

        List<TriggerSpec> triggers = triggerDesigner.design(analysis);

        return new AgentResult(
                Status.DONE,
                classification.category(),
                triggers,
                classification.confidence(),
                null
        );
    }
}
