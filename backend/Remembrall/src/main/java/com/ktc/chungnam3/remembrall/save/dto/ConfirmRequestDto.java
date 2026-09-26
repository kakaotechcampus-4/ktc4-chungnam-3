package com.ktc.chungnam3.remembrall.save.dto;

import java.util.List;

public record ConfirmRequestDto(
        String question,

        List<ResolvedPlaceDto> candidates

) {
}
