package com.ktc.chungnam3.remembrall.extraction.dto;

import com.ktc.chungnam3.remembrall.extraction.type.ExtractionFailureCode;
import com.ktc.chungnam3.remembrall.extraction.type.ExtractionStatus;

import java.util.List;

public record YouTubeContentExtractionResultDto(
        ExtractionStatus status,
        AnalysisMetadataDto analysisMetadata,
        String title,
        String summary,
        String category,
        List<PlaceCandidateDto> placeCandidates,
        List<TemporalInfoDto> temporalInfos,
        ExtractionFailureCode failureCode
) {
}
