package com.ktc.chungnam3.remembrall.save.dto;

public record SavePipelineResultDto(
        Status status,

        String summary,

        ResolvedPlaceDto place,

        ConfirmRequestDto confirm

) {
    public enum Status {
        PLACE_RESOLVED,
        NEEDS_CONFIRMATION,
        NO_PLACE
    }
}
