package com.ktc.chungnam3.remembrall.e2e;

import com.ktc.chungnam3.remembrall.recall.agent.RecallAgent;
import com.ktc.chungnam3.remembrall.recall.agent.RecallAgentInput;
import com.ktc.chungnam3.remembrall.recall.agent.RecallAgentResult;
import com.ktc.chungnam3.remembrall.recall.trace.RecallTraceCollector;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
@Profile("e2e")
public class E2eAgentInputRecorder implements BeanPostProcessor {
    private final Map<UUID, RecallAgentInput> inputs = Collections.synchronizedMap(new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, RecallAgentInput> eldest) {
            return size() > 100;
        }
    });

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (!(bean instanceof RecallAgent delegate)) {
            return bean;
        }
        return new RecordingAgent(delegate, inputs);
    }

    public Optional<RecallAgentInput> find(UUID executionId) {
        return Optional.ofNullable(inputs.get(executionId));
    }

    private record RecordingAgent(RecallAgent delegate, Map<UUID, RecallAgentInput> inputs)
            implements RecallAgent, AutoCloseable {
        @Override
        public String version() {
            return delegate.version();
        }

        @Override
        public RecallAgentResult execute(UUID executionId, RecallAgentInput input) throws Exception {
            inputs.put(executionId, input);
            return delegate.execute(executionId, input);
        }

        @Override
        public Execution executeWithStatistics(UUID executionId, RecallAgentInput input) throws Exception {
            inputs.put(executionId, input);
            return delegate.executeWithStatistics(executionId, input);
        }

        @Override
        public String modelName() {
            return delegate.modelName();
        }

        @Override
        public boolean recordsModelCalls() { return delegate.recordsModelCalls(); }

        @Override
        public Execution executeWithStatistics(UUID executionId, RecallAgentInput input, RecallTraceCollector trace) throws Exception {
            inputs.put(executionId, input);
            return delegate.executeWithStatistics(executionId, input, trace);
        }

        @Override
        public void close() throws Exception {
            if (delegate instanceof AutoCloseable resources) {
                resources.close();
            }
        }
    }
}
