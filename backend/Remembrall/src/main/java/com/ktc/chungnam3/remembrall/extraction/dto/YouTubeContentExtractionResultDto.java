package com.ktc.chungnam3.remembrall.extraction.dto;

import com.ktc.chungnam3.remembrall.extraction.type.ExtractionStatus;

import java.util.List;

public record YouTubeContentExtractionResultDto(
        ExtractionStatus status,
        AnalysisMetadataDto analysisMetadata,
        String summary,
        List<String> summaryUncertainties,
        List<PlaceCandidateDto> placeCandidates,
        List<TemporalInfoDto> temporalInfos,
        FailureInfoDto failure
) {
}
