package com.saveagent.agent.save;

import com.saveagent.agent.save.dto.ConfirmRequest;

import java.util.List;
import java.util.Optional;

/**
 * 되묻기 여부 판정.
 * <p>
 * 규칙: 확신도가 임계값보다 낮고, 선택지를 2~3개로 좁힐 수 있을 때만 되묻는다.
 * 되묻기는 콘텐츠당 최대 1회이므로, 이미 한 번 물어봤다면(alreadyAsked) 다시 묻지 않는다.
 * (이미 물어봤는지 여부는 이 에이전트가 상태로 갖지 않는다 - DB 저장은 상위 로직의 책임이라
 * 호출하는 쪽에서 상태를 전달해줘야 한다.)
 */
public class ConfirmPolicy {

    // TODO: 협의 필요 - 되묻기를 트리거하는 확신도 임계값. 확정 전까지 0.5를 임시로 사용한다.
    private static final double CONFIDENCE_THRESHOLD = 0.5;

    private static final int MIN_CANDIDATES = 2;
    private static final int MAX_CANDIDATES = 3;

    public Optional<ConfirmRequest> evaluate(ClassificationResult result, boolean alreadyAsked) {
        if (alreadyAsked) {
            // 되묻기 미응답이든 응답이든, 콘텐츠당 최대 1회 원칙에 따라 다시 묻지 않는다.
            return Optional.empty();
        }

        boolean lowConfidence = result.confidence() < CONFIDENCE_THRESHOLD;
        List<String> candidates = result.ambiguousCandidates();
        boolean narrowedDown = candidates != null
                && candidates.size() >= MIN_CANDIDATES
                && candidates.size() <= MAX_CANDIDATES;

        if (!lowConfidence || !narrowedDown) {
            // 그 외에는 그대로 진행한다 (지역은 상위 단위로만 저장).
            return Optional.empty();
        }

        // TODO: 협의 필요 - 되묻기 질문 문구를 카테고리/후보에 맞게 동적으로 만들지, 고정 템플릿을 쓸지 미정.
        String question = "관련 지역이 어디인가요? 후보: " + String.join(", ", candidates);

        return Optional.of(new ConfirmRequest(question, candidates, result.modelGuess()));
    }
}
