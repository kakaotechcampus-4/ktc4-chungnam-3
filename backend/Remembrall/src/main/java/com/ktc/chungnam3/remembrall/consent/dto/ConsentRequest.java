package com.ktc.chungnam3.remembrall.consent.dto;

import jakarta.validation.constraints.NotNull;

public record ConsentRequest(@NotNull Boolean agreed, String termsVersion) {
}
