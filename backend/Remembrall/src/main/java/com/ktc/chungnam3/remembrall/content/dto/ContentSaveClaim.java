package com.ktc.chungnam3.remembrall.content.dto;

import com.ktc.chungnam3.remembrall.domain.content.ContentAnalysisStatus;

import java.util.UUID;

public record ContentSaveClaim(
        UUID contentId,
        UUID personalSaveId,
        boolean personalSaveCreated,
        ContentAnalysisStatus analysisStatus,
        boolean analysisClaimed
) {
}
