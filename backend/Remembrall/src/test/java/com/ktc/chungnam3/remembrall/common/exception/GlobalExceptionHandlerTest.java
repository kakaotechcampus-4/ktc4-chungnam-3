package com.ktc.chungnam3.remembrall.common.exception;

import com.ktc.chungnam3.remembrall.auth.security.AuthenticatedMember;
import com.ktc.chungnam3.remembrall.personalsave.controller.PersonalSaveController;
import com.ktc.chungnam3.remembrall.personalsave.service.PersonalSaveService;
import com.ktc.chungnam3.remembrall.save.ConfirmAnswerController;
import com.ktc.chungnam3.remembrall.save.confirm.ConfirmAnswerService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {
    private PersonalSaveService saves;
    private ConfirmAnswerService answers;
    private MockMvc mvc;
    private UUID memberId;

    @BeforeEach
    void setUp() {
        saves = mock(PersonalSaveService.class);
        answers = mock(ConfirmAnswerService.class);
        mvc = MockMvcBuilders.standaloneSetup(new PersonalSaveController(saves), new ConfirmAnswerController(answers))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        memberId = UUID.randomUUID();
        var member = new AuthenticatedMember(memberId, UUID.randomUUID(), "test-session-hash");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(member, null, List.of()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @CsvSource({
            "GET, /api/personal-saves/not-a-uuid",
            "DELETE, /api/personal-saves/not-a-uuid",
            "POST, /api/personal-saves/not-a-uuid/places/p2/confirm"
    })
    void malformedUuidReturnsCommonInvalidRequestWithoutCallingServices(String method, String path) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method), path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"selectedOptionId\":\"p2-1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value(ErrorCode.INVALID_REQUEST.getMessage()));

        verifyNoInteractions(saves, answers);
    }

    @Test
    void malformedJsonStillReturnsCommonInvalidRequest() throws Exception {
        mvc.perform(post("/api/personal-saves/{id}/places/p2/confirm", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value(ErrorCode.INVALID_REQUEST.getMessage()));

        verifyNoInteractions(saves, answers);
    }

    @Test
    void validUuidWithMissingSaveStillReturnsNotFound() throws Exception {
        UUID saveId = UUID.randomUUID();
        when(saves.detail(memberId, saveId)).thenThrow(new ApiException(ErrorCode.NOT_FOUND));

        mvc.perform(get("/api/personal-saves/{id}", saveId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value(ErrorCode.NOT_FOUND.getMessage()));
    }
}
