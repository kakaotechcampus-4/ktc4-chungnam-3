package com.ktc.chungnam3.remembrall.extraction.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;


public record TemporalInfoDto(
        String subject,
        String originalText,
        List<String> relatedPlaceCandidateIds,
        LocalDate startDate,
        LocalDate endDate,
        LocalTime startTime,
        LocalTime endTime,
        List<EvidenceDto> evidence,
        List<String> uncertainties
) {
}