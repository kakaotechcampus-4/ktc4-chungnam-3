package com.saveagent.agent.save;

// TODO: 협의 필요 - LLM 호출을 Spring AI로 할지 직접 HTTP로 할지 아직 정해지지 않았다.
//  일단 인터페이스로 추상화해두고, 구현체는 나중에 갈아끼울 수 있게 한다.
/**
 * LLM 호출 인터페이스. 프롬프트를 넣으면 원문 응답(텍스트)을 그대로 돌려준다.
 * JSON 파싱 등 응답 해석은 호출하는 쪽(ClassifierService)의 책임이다.
 */
public interface LlmClient {

    String complete(String prompt);
}
