package com.ktc.chungnam3.remembrall.auth.token;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class SessionTokenGeneratorTest {

    private final SessionTokenGenerator generator = new SessionTokenGenerator();

    @Test
    void generatesRandom256BitToken() {
        String first = generator.generate();
        String second = generator.generate();

        assertThat(Base64.getUrlDecoder().decode(first)).hasSize(32);
        assertThat(Base64.getUrlDecoder().decode(second)).hasSize(32);
        assertThat(first).isNotEqualTo(second);
    }
}
