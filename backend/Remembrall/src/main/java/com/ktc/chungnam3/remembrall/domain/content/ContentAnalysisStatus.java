package com.ktc.chungnam3.remembrall.domain.content;

public enum ContentAnalysisStatus {
    PENDING,
    ANALYZING,
    COMPLETED,
    PARTIAL_SUCCESS,
    FAILED;

    public boolean isTerminal() {
        return this == COMPLETED || this == PARTIAL_SUCCESS || this == FAILED;
    }
}
