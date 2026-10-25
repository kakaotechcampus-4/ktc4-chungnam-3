package com.ktc.chungnam3.remembrall.recall.agent;

import com.ktc.chungnam3.remembrall.recall.trace.RecallTraceCollector;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.model.ChatResponse;

public final class RecallModelCallAdvisor implements CallAdvisor {
    private final RecallAgentSession session;
    private final String model;
    private final RecallAgentInput input;

    public RecallModelCallAdvisor(RecallAgentSession session, String model, RecallAgentInput input) {
        this.session = session;
        this.model = model;
        this.input = input;
    }

    @Override
    public String getName() { return "Recall model call trace and execution budget"; }

    @Override
    public int getOrder() { return ToolCallingAdvisor.DEFAULT_ORDER + 100; }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        session.beforeModel();
        var trace = session.trace();
        var span = trace == null ? null : trace.begin(RecallTraceCollector.Step.MODEL_CALL, model);
        long started = System.nanoTime();
        ChatResponse response = null;
        Throwable error = null;
        try {
            var result = chain.nextCall(request);
            response = result == null ? null : result.chatResponse();
            session.account(response);
            return result;
        } catch (RuntimeException | Error exception) {
            session.account(null);
            error = exception;
            throw exception;
        } finally {
            if (trace != null) {
                var usage = response == null ? null : response.getMetadata().getUsage();
                boolean known = usage != null && !(usage instanceof EmptyUsage);
                trace.model(span, model, input, (System.nanoTime() - started) / 1_000_000.0,
                        known ? usage.getPromptTokens() : null, known ? usage.getCompletionTokens() : null, error);
            }
            if (error == null) {
                session.checkTime();
            }
        }
    }
}
