package com.saveagent.agent.save;

import com.saveagent.agent.save.dto.Category;

import java.util.List;

/**
 * ClassifierService의 내부 판단 결과. 상위 저장 로직에 노출되는 DTO가 아니라
 * ConfirmPolicy / SaveAgentService 조율에만 쓰는 내부 계약이다.
 */
public record ClassificationResult(
        Category category,
        double confidence,
        List<String> ambiguousCandidates, // 되묻기용 후보 (2~3개로 좁혀졌을 때만 채워짐, 그 외엔 빈 리스트)
        String modelGuess                 // 되묻기가 필요할 때 남기는 원래 판단값 (개선용 로그)
) {}
