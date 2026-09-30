package com.ktc.chungnam3.remembrall.auth.token;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SessionTokenHasherTest {

    private final SessionTokenHasher hasher = new SessionTokenHasher();

    @Test
    void hashesTokenWithSha256() {
        assertThat(hasher.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
