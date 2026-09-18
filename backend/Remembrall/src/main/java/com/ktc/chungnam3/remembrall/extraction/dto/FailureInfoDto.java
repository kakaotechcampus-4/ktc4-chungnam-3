package com.ktc.chungnam3.remembrall.extraction.dto;

import com.ktc.chungnam3.remembrall.extraction.type.FailureStage;

public record FailureInfoDto(
        FailureStage stage,

        Integer httpStatus,

        String errorType,

        String message,

        String extractedScope

) {
}