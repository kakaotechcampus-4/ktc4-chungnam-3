package com.ktc.chungnam3.remembrall.personalsave.dto;

import jakarta.validation.constraints.NotBlank;

public record PersonalSaveRequest(@NotBlank String url) {
    public record Query(Integer size, String cursor) {
    }
}
