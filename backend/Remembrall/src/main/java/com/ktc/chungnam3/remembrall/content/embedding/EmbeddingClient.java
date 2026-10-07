package com.ktc.chungnam3.remembrall.content.embedding;

public interface EmbeddingClient {

    enum TaskType {
        RETRIEVAL_DOCUMENT,
        RETRIEVAL_QUERY
    }

    record Result(float[] vector, String model) {
    }

    Result embed(String text, TaskType taskType);

    default Result embedDocument(String text) {
        return embed(text, TaskType.RETRIEVAL_DOCUMENT);
    }

    default Result embedQuery(String text) {
        return embed(text, TaskType.RETRIEVAL_QUERY);
    }
}
