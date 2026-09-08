package com.saveagent.agent.save.dto;

import java.util.List;

/**
 * 되묻기 요청. status == NEEDS_CONFIRM 일 때만 채워진다.
 */
public record ConfirmRequest(
        String question,       // "대전 관련 영상인데 지역이 어딘가요?"
        List<String> candidates, // ["중구", "유성구"] - 2~3개로 좁혀진 후보
        String modelGuess       // 에이전트가 원래 판단했던 값 (개선용 로그)
) {}
