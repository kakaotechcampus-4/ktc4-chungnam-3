package com.ktc.chungnam3.remembrall.extraction.dto;

import java.util.List;

public record PlaceCandidateDto(
        String candidateId,
        String name,
        String branchName,
        String regionHint,
        String description,
        List<EvidenceDto> evidence,
        List<String> uncertainties

) {
}