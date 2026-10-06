package com.ktc.chungnam3.remembrall.save.analysis;

import com.ktc.chungnam3.remembrall.extraction.dto.PlaceCandidateDto;
import com.ktc.chungnam3.remembrall.extraction.dto.YouTubeContentExtractionResultDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.ai.model.google.genai.autoconfigure.embedding.GoogleGenAiEmbeddingConnectionAutoConfiguration;
import org.springframework.ai.model.google.genai.autoconfigure.embedding.GoogleGenAiTextEmbeddingAutoConfiguration;
import org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration;

/**
 * 실제 Gemini(Vertex AI)를 호출해서 VideoContentAnalyzer가 동작하는지 눈으로 확인하는 수동 검증용 테스트.
 * DB(Docker)가 없어도 돌아가도록 전체 앱을 안 띄우고, save/analysis·save/youtube 패키지만 스캔하는
 * 별도의 작은 Spring Boot 앱으로 띄운다 (자동 구성은 그대로 적용되므로 Spring AI의 ChatModel 빈은
 * application.yaml/secrets.properties 설정 그대로 만들어진다).
 * <p>
 * 실제로 Gemini를 호출해서 비용이 발생하니 평소엔 안 돌리고, 필요할 때 수동으로만 실행한다.
 */
@SpringBootTest(
        classes = VideoContentAnalyzerLiveTest.TestApp.class,
        properties = {
                // src/test/resources/application.yaml가 메인 application.yaml을 가려서
                // secrets.properties의 gemini.*/youtube.* 값이 테스트엔 안 읽히던 문제를,
                // 메인 yaml과 같은 연결(placeholder)을 여기서 다시 선언해서 해결한다.
                // 실제 값은 여전히 secrets.properties(git-ignored)에만 있다.
                "spring.config.import=optional:file:./secrets.properties",
                "spring.ai.google.genai.vertex-ai=${gemini.vertex-ai.enabled:false}",
                "spring.ai.google.genai.project-id=${gemini.vertex-ai.project-id:}",
                "spring.ai.google.genai.location=${gemini.vertex-ai.location:us-central1}"
        }
)
class VideoContentAnalyzerLiveTest {

    @SpringBootApplication(exclude = {
            DataSourceAutoConfiguration.class,
            DataJpaRepositoriesAutoConfiguration.class,
            FlywayAutoConfiguration.class,
            GoogleGenAiEmbeddingConnectionAutoConfiguration.class,
            GoogleGenAiTextEmbeddingAutoConfiguration.class,
            PgVectorStoreAutoConfiguration.class
    })
    @ComponentScan(basePackages = {
            "com.ktc.chungnam3.remembrall.save.analysis",
            "com.ktc.chungnam3.remembrall.save.youtube"
    })
    static class TestApp {
    }

    @Autowired
    private VideoContentAnalyzer videoContentAnalyzer;

    @Test
    void 실제_영상_하나로_분석해본다() {
        String url = "https://youtube.com/shorts/kbWy8_UC0Z8?si=P8OhmHGl1qO9Hdho";

        long start = System.currentTimeMillis();
        YouTubeContentExtractionResultDto result = videoContentAnalyzer.analyze(url);
        long elapsedMs = System.currentTimeMillis() - start;

        System.out.println("=== 소요시간: " + elapsedMs + "ms ===");
        System.out.println("status: " + result.status());
        System.out.println("title: " + result.title());
        System.out.println("sourceStatus: " + result.sourceStatus());
        System.out.println("analysisMetadata: " + result.analysisMetadata());
        System.out.println("summary: " + result.summary());
        System.out.println("summaryUncertainties: " + result.summaryUncertainties());
        System.out.println("placeCandidates: " + result.placeCandidates());
        System.out.println("temporalInfos: " + result.temporalInfos());
        System.out.println("failure: " + result.failure());
    }

    /**
     * urls.txt에 있던 나머지 9건을 한 번에 돌려서 품질·소요시간을 측정한다.
     * 영상 하나가 실패해도 나머지는 계속 진행하도록 건별로 예외를 잡는다.
     */
    @Test
    void 나머지_9건_품질_측정() {
        List<String> urls = List.of(
                "https://youtube.com/shorts/k5vAzApbvMw?si=Ls_qKTlFWWwm_IJg",
                "https://youtube.com/shorts/Xnr01vva2p8?si=YUZrKFQA7st0pZLk",
                "https://youtube.com/shorts/gVG5xCH9fBA?si=2twQGlK0ns9dHB1s",
                "https://youtube.com/shorts/I07MzdoSfb8?si=c4-AitYODKenfpk4",
                "https://youtube.com/shorts/IjTjXoxf_44?si=X2ppE88xWq7pep7c",
                "https://youtube.com/shorts/JoaaM4aE6eg?si=a6p8W0ei3WCe9V97",
                "https://youtube.com/shorts/NRKqiFU9dI8?si=u7Z9mJIwEYOTaHDJ",
                "https://youtube.com/shorts/vikptJdj-ck?si=njdLXUlPwN243B3E",
                "https://youtube.com/shorts/gVFp3SrF5X8?si=5E8fWEEKblUNVcZA"
        );

        for (String url : urls) {
            System.out.println("\n================================================");
            System.out.println("URL: " + url);
            long start = System.currentTimeMillis();
            try {
                YouTubeContentExtractionResultDto result = videoContentAnalyzer.analyze(url);
                long elapsedMs = System.currentTimeMillis() - start;

                List<String> placeNames = result.placeCandidates().stream()
                        .map(p -> p.name() + (p.branchName() == null ? "" : " " + p.branchName()))
                        .toList();

                System.out.println("소요시간: " + elapsedMs + "ms");
                System.out.println("status: " + result.status());
                System.out.println("summary: " + result.summary());
                System.out.println("장소 " + placeNames.size() + "건: " + placeNames);
                System.out.println("temporalInfos: " + result.temporalInfos().size() + "건");
            } catch (Exception e) {
                long elapsedMs = System.currentTimeMillis() - start;
                System.out.println("실패 (소요시간 " + elapsedMs + "ms): " + e.getMessage());
            }
        }
    }

    /**
     * 지난 실행에서 "Failed to generate content"로 실패했던 2건을 다시 돌려서,
     * 그 영상 자체가 항상 안 되는 건지(VIDEO_ACCESS) 아니면 일시적 문제였는지 구분한다.
     */
    @Test
    void 실패했던_2건_재시도() {
        List<String> urls = List.of(
                "https://youtube.com/shorts/Xnr01vva2p8?si=YUZrKFQA7st0pZLk",
                "https://youtube.com/shorts/JoaaM4aE6eg?si=a6p8W0ei3WCe9V97"
        );

        for (String url : urls) {
            System.out.println("\n================================================");
            System.out.println("URL: " + url);

            int maxAttempts = 3;
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                long start = System.currentTimeMillis();
                try {
                    YouTubeContentExtractionResultDto result = videoContentAnalyzer.analyze(url);
                    long elapsedMs = System.currentTimeMillis() - start;
                    List<String> placeNames = result.placeCandidates().stream()
                            .map(p -> p.name() + (p.branchName() == null ? "" : " (" + p.branchName() + ")"))
                            .toList();
                    System.out.println(attempt + "번째 시도 성공! 소요시간: " + elapsedMs + "ms");
                    System.out.println("summary: " + result.summary());
                    System.out.println("장소(이름+지점명): " + placeNames);
                    break;
                } catch (Exception e) {
                    long elapsedMs = System.currentTimeMillis() - start;
                    System.out.println(attempt + "번째 시도 실패 (소요시간 " + elapsedMs + "ms): " + e.getMessage());
                    if (attempt == maxAttempts) {
                        System.out.println("=> " + maxAttempts + "번 다 실패");
                    }
                }
            }
        }
    }
}
