package com.ktc.chungnam3.remembrall.extraction.dto;

import com.ktc.chungnam3.remembrall.extraction.type.EvidenceSource;

public record EvidenceDto(
        EvidenceSource source,
        String detail
) {
}