package com.saveagent.agent.save;

import com.saveagent.agent.save.dto.Category;
import com.saveagent.agent.save.dto.ContentAnalysis;

import java.util.List;

/**
 * 카테고리 분류 + 애매함 판정 (LLM 사용).
 * <p>
 * 트리거 판단은 이 클래스의 책임이 아니다 - 그건 규칙 기반인 TriggerDesigner가 한다.
 * LLM 응답이 형식에 안 맞으면 1회 재시도하고, 그래도 실패하면 카테고리는 ETC(기타),
 * 확신도는 0으로 안전하게 폴백한다.
 */
public class ClassifierService {

    private final LlmClient llmClient;

    public ClassifierService(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    public ClassificationResult classify(ContentAnalysis analysis) {
        String prompt = buildPrompt(analysis);

        ClassificationResult result = tryClassifyOnce(prompt);
        if (result != null) {
            return result;
        }

        // 형식이 안 맞으면 1회 재시도.
        result = tryClassifyOnce(prompt);
        if (result != null) {
            return result;
        }

        // 재시도도 실패 -> 카테고리 기타, 확신도 0으로 폴백 (트리거는 상위에서 별도로 0개 처리됨).
        return new ClassificationResult(Category.ETC, 0.0, List.of(), null);
    }

    private ClassificationResult tryClassifyOnce(String prompt) {
        try {
            String rawResponse = llmClient.complete(prompt);
            return parseResponse(rawResponse);
        } catch (RuntimeException e) {
            // TODO: 협의 필요 - 파싱/호출 실패 시 로깅 정책 (모니터링용 로그 남길지 등).
            return null;
        }
    }

    private ClassificationResult parseResponse(String rawResponse) {
        // TODO: 구현 필요 - LLM 응답(JSON)을 파싱해서 ClassificationResult로 변환해야 한다.
        //  LLM 연동 방식(Spring AI vs 직접 HTTP, 응답 JSON 스키마)이 아직 미정이라
        //  우선 인터페이스 구조만 잡아두고 구현은 스텁으로 남겨둔다.
        throw new UnsupportedOperationException("LLM 응답 파싱 미구현 - LlmClient 구현체와 함께 확정 예정");
    }

    private String buildPrompt(ContentAnalysis analysis) {
        // TODO: 협의 필요 - 실제 프롬프트 템플릿. 아래는 설계 문서의 필수 규칙만 우선 반영한 초안이다.
        return """
                다음 규칙을 반드시 지켜서 정해진 JSON 형식으로만 응답하세요.
                - 장소·기간 정보가 없으면 빈 값으로 두고 추측하지 않는다.
                - 지역이 불확실하면 상위 단위로만 적는다 (구를 모르면 "대전"까지).
                - 카테고리가 애매하면 "기타"로 두고 확신도를 낮게 준다.

                카테고리는 다음 중 하나입니다: 맛집, 관광지, 행사, 숙소, 기타

                제목: %s
                요약: %s
                태그: %s
                """.formatted(analysis.title(), analysis.summary(), analysis.tags());
    }
}
