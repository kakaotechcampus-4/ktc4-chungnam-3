package com.ktc.chungnam3.remembrall.domain.content;

public enum ContentAnalysisStatus {
    PENDING,
    ANALYZING,
    SUCCESS,
    PARTIAL,
    FAILED;

    public boolean isTerminal() {
        return this == SUCCESS || this == PARTIAL || this == FAILED;
    }
}
