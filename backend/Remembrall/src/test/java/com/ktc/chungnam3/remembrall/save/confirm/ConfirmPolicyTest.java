package com.ktc.chungnam3.remembrall.save.confirm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConfirmPolicyTest {

    private final ConfirmPolicy policy = new ConfirmPolicy();

    @Test
    void 이전에_안_물어봤으면_물어본다() {
        assertThat(policy.shouldAsk(false)).isTrue();
    }

    @Test
    void 이미_물어봤으면_다시_안_물어본다() {
        assertThat(policy.shouldAsk(true)).isFalse();
    }
}
