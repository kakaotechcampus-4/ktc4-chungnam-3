package com.ktc.chungnam3.remembrall.personalsave;

import com.ktc.chungnam3.remembrall.common.exception.ApiException;
import com.ktc.chungnam3.remembrall.common.exception.ErrorCode;
import com.ktc.chungnam3.remembrall.personalsave.service.PersonalSaveCursor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PersonalSaveCursorTest {
    @Test
    void roundTripsDatabasePrecisionAndUuidWithoutExposingRawCursor() {
        var cursor = new PersonalSaveCursor(Instant.parse("2026-10-18T01:02:03.123456Z"), UUID.randomUUID());

        assertThat(PersonalSaveCursor.decode(cursor.encode())).isEqualTo(cursor);
        assertThat(cursor.encode()).doesNotContain("|", "=", cursor.id().toString());
        assertThat(PersonalSaveCursor.decode(null)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "v2|2026-10-18T00:00:00Z|00000000-0000-0000-0000-000000000001",
            "v1|bad-date|00000000-0000-0000-0000-000000000001", "v1|2026-10-18T00:00:00Z|bad-id",
            "v1|2026-10-18T00:00:00Z|0-0-0-0-1", "v1|2026-10-18T00:00:00Z|00000000-0000-0000-0000-000000000001|extra"})
    void rejectsMalformedAndNonCanonicalCursors(String raw) {
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> PersonalSaveCursor.decode(value)).isInstanceOfSatisfying(ApiException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
    }
}
