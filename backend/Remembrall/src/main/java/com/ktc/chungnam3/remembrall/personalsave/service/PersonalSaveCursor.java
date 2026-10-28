package com.ktc.chungnam3.remembrall.personalsave.service;

import com.ktc.chungnam3.remembrall.common.exception.ApiException;
import com.ktc.chungnam3.remembrall.common.exception.ErrorCode;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

public record PersonalSaveCursor(Instant savedAt, UUID id) {
    public String encode() {
        String value = "v1|" + savedAt.truncatedTo(ChronoUnit.MICROS) + "|" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    public static PersonalSaveCursor decode(String value) {
        if (value == null) {
            return null;
        }
        try {
            if (value.isBlank() || value.length() > 200) {
                throw new IllegalArgumentException();
            }
            String[] parts = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8).split("\\|", -1);
            if (parts.length != 3 || !parts[0].equals("v1")) {
                throw new IllegalArgumentException();
            }
            var cursor = new PersonalSaveCursor(Instant.parse(parts[1]), UUID.fromString(parts[2]));
            if (!cursor.encode().equals(value)) {
                throw new IllegalArgumentException();
            }
            return cursor;
        } catch (IllegalArgumentException | java.time.DateTimeException exception) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }
    }
}
