package com.ktc.chungnam3.remembrall.save.analysis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktc.chungnam3.remembrall.extraction.dto.AnalysisMetadataDto;
import com.ktc.chungnam3.remembrall.extraction.dto.PlaceCandidateDto;
import com.ktc.chungnam3.remembrall.extraction.dto.TemporalInfoDto;
import com.ktc.chungnam3.remembrall.extraction.dto.YouTubeContentExtractionResultDto;
import com.ktc.chungnam3.remembrall.extraction.type.ExtractionFailureCode;
import com.ktc.chungnam3.remembrall.extraction.type.ExtractionStatus;
import com.ktc.chungnam3.remembrall.save.youtube.YoutubeMetadataClient;
import com.ktc.chungnam3.remembrall.save.youtube.YoutubeUrlParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 유튜브 영상 URL을 Gemini에 직접 넘겨서(영상 자체를 분석) {@link YouTubeContentExtractionResultDto}를 만든다.
 * <p>
 * 좌표(위치 검증)는 여기서 하지 않는다 - 이 단계는 영상에서 신호(가게 이름, 지역 힌트, 기간)만
 * 뽑아내는 게 책임이고, 장소검색 API로 실제 검증하는 건 다음 단계(장소 탐색·검증)의 몫이다.
 * <p>
 * Spring AI의 {@code GoogleGenAiChatModel}은 내부적으로 {@code com.google.genai:google-genai}
 * SDK를 그대로 감싸고 있고, URI 기반 {@link Media}는 SDK의 {@code Part.fromUri(...)}로 변환된다
 * (jar 디컴파일로 확인함). 유튜브 URL을 영상으로 직접 넘기는 것도 이 경로로 동작한다.
 */
@Slf4j
@Component
public class VideoContentAnalyzer {

    // TODO: 협의 필요 - 실제 사용할 모델. GEMINI_3_6_FLASH는 Vertex AI(us-central1)에서 404로 확인돼
    //  아직 Vertex엔 안 풀린 것으로 보임(2026-10-06 실측). 3.x 계열이 전반적으로 preview라 안 될 가능성이
    //  있어, 안정적으로 되는 2.5 Flash로 우선 내림.
    private static final GoogleGenAiChatModel.ChatModel MODEL = GoogleGenAiChatModel.ChatModel.GEMINI_2_5_FLASH;

    private static final String ANALYSIS_VERSION = "v1";
    private static final String PROMPT_VERSION = "v5";

    private static final String PROMPT_TEMPLATE = """
            너는 여행·맛집 유튜브 영상에서 정보를 추출하는 분석가다.
            반드시 아래 규칙을 지켜서 정해진 JSON 형식으로만 응답해라.

            영상 게시일: %s

            [참고 정보 — 유튜브가 제공하는 부가 텍스트다. 영상 내용 판단이 우선이지만, 아래 텍스트에도
            상호명이 자주 등장하니 함께 참고해라]
            제목: %s
            설명: %s
            태그: %s
            채널 운영자(영상 게시자 본인)가 남긴 댓글:
            %s

            - 설명이나 댓글에 있는 해시태그(#으로 시작하는 단어)도 상호명 후보로 간주해라. 단, 지역명·감정
              표현처럼 상호명이 아닌 해시태그는 무시해라.
            - summary는 영상의 핵심 주제, 소개 대상, 주요 특징과 영상에서 명시된 추천 상황·제약을 간결하게 적어라.
              장소가 없어도 요약은 남겨라.
            - category에는 카페/맛집/관광지/축제/술집/쇼핑/기타 중 하나만 골라 담아라. 영상 전체의 category는
              영상의 주된 성격을 기준으로 고르고, placeCandidates 각각의 category는 그 장소 하나하나의 업종을
              기준으로 따로 고른다(같은 영상 안에서도 장소마다 다를 수 있다). 애매하면 기타로 둬라.
            - 장소 정보가 없으면 placeCandidates를 빈 배열로 두고 절대 추측하지 마라.
            - 장소 이름은 상호명(name)·지점명(branchName)·지역 단서(regionHint)로 나눠 적어라.
              예: "대전 성심당 본점"이면 name="성심당", branchName="본점", regionHint="대전".
              지점이나 지역이 확인되지 않으면 해당 필드는 빈 문자열로 둬라.
            - regionHint에는 시·도(예: 서울특별시, 대전광역시)를 반드시 포함해라. 구·동까지 확인되면
              "대전 중구"처럼 시·도 뒤에 이어서 적되, 시·도 자체를 빼지는 마라 - 장소 검색 단계가
              시·도 단위로만 지역을 대조하기 때문이다. 구·동까지도 불확실하면 시·도까지만 적어라.
            - 좌표는 추출하지 않는다 (이 단계의 책임이 아니다).
            - 여러 장소가 있으면 각각 분리해서 반환해라.
            - relatedPlaceNames에는 이 기간과 연결되는 placeCandidates의 name을 그대로 적어라. 관계가 없으면 빈 배열로 둬라.
            - startDate/endDate에 연도가 없는 날짜가 나오면(예: "10월 3일까지") 위 영상 게시일과 같은 연도로 채워라.
              월·일 자체를 확인할 수 없으면 빈 문자열로 두고 절대 추정하지 마라.
            - startTime/endTime을 확인할 수 없으면 빈 문자열로 둬라. 절대 추정하지 마라.
            """;

    private static final String RESPONSE_SCHEMA_JSON = """
            {
              "type": "object",
              "properties": {
                "summary": { "type": "string" },
                "category": {
                  "type": "string",
                  "enum": ["카페", "맛집", "관광지", "축제", "술집", "쇼핑", "기타"],
                  "description": "영상 전체 내용에 맞는 분류 하나"
                },
                "placeCandidates": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "name": { "type": "string" },
                      "branchName": { "type": "string" },
                      "regionHint": { "type": "string" },
                      "description": { "type": "string" },
                      "category": {
                        "type": "string",
                        "enum": ["카페", "맛집", "관광지", "축제", "술집", "쇼핑", "기타"],
                        "description": "이 장소 하나에 맞는 분류 하나"
                      }
                    },
                    "required": ["name", "branchName", "regionHint", "description", "category"]
                  }
                },
                "temporalInfos": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "subject": { "type": "string" },
                      "originalText": { "type": "string" },
                      "relatedPlaceNames": { "type": "array", "items": { "type": "string" } },
                      "startDate": { "type": "string", "description": "YYYY-MM-DD, 모르면 빈 문자열" },
                      "endDate": { "type": "string", "description": "YYYY-MM-DD, 모르면 빈 문자열" },
                      "startTime": { "type": "string", "description": "HH:MM, 모르면 빈 문자열" },
                      "endTime": { "type": "string", "description": "HH:MM, 모르면 빈 문자열" }
                    },
                    "required": ["subject", "originalText", "relatedPlaceNames", "startDate", "endDate", "startTime", "endTime"]
                  }
                }
              },
              "required": ["summary", "category", "placeCandidates", "temporalInfos"]
            }
            """;

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record LlmResult(String summary, String category, List<LlmPlace> placeCandidates,
                              List<LlmTemporal> temporalInfos) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record LlmPlace(String name, String branchName, String regionHint, String description, String category) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record LlmTemporal(String subject, String originalText, List<String> relatedPlaceNames,
                                String startDate, String endDate, String startTime, String endTime) {
    }

    private final ChatModel chatModel;
    private final YoutubeMetadataClient youtubeMetadataClient;
    // Spring Boot 4.1의 자동 구성 ObjectMapper 빈은 Jackson 3(tools.jackson.*) 타입이라
    // Jackson 2 API(com.fasterxml.jackson.*)로 직접 파싱하기 위해 별도 인스턴스를 쓴다.
    private final ObjectMapper objectMapper = new ObjectMapper();

    public VideoContentAnalyzer(ChatModel chatModel, YoutubeMetadataClient youtubeMetadataClient) {
        this.chatModel = chatModel;
        this.youtubeMetadataClient = youtubeMetadataClient;
    }

    /**
     * 유튜브 URL 하나로 메타데이터 조회(제목·설명·태그·운영자 댓글)와 영상 분석을 모두 수행한다.
     * 게시일도 메타데이터 조회에서 얻으므로 별도 파라미터로 받지 않는다.
     * <p>
     * URL 형식 자체가 잘못된 경우(videoId를 못 찾음)는 호출 전에 걸러졌어야 할 사전 조건 위반으로 보고
     * 예외를 던진다. 그 외의 실패(메타데이터 조회/Gemini 호출/응답 파싱)는 단계별로 잡아서
     * {@code status: FAILED}와 {@link ExtractionFailureCode}가 채워진 정상적인 결과로 반환한다 — 이
     * 메서드를 부르는 쪽이 예외 처리 없이도 실패를 다룰 수 있게 하기 위해서다. 실패 원인 상세(메시지 등)는
     * 계약(저장담당 요구 사항.md)에 더는 포함되지 않으므로 로그로만 남긴다.
     */
    public YouTubeContentExtractionResultDto analyze(String youtubeUrl) {
        String videoId = YoutubeUrlParser.extractVideoId(youtubeUrl)
                .orElseThrow(() -> new IllegalArgumentException(
                        "유튜브 URL에서 videoId를 찾을 수 없음: " + youtubeUrl));

        AnalysisMetadataDto metadata = new AnalysisMetadataDto(ANALYSIS_VERSION, MODEL.getValue(), PROMPT_VERSION);

        YoutubeMetadataClient.VideoInfo videoInfo;
        List<String> authorComments;
        try {
            videoInfo = youtubeMetadataClient.getVideoInfo(videoId);
            authorComments = youtubeMetadataClient.getAuthorComments(videoId, videoInfo.channelId());
        } catch (YoutubeMetadataClient.VideoUnavailableException e) {
            log.info("원본 영상 접근 불가 확인됨: {}", videoId, e);
            return failedResult(metadata, null, ExtractionFailureCode.VIDEO_UNAVAILABLE);
        } catch (Exception e) {
            log.warn("메타데이터 조회 실패: {}", videoId, e);
            return failedResult(metadata, null, ExtractionFailureCode.EXTRACTION_API_ERROR);
        }

        UserMessage userMessage = UserMessage.builder()
                .text(PROMPT_TEMPLATE.formatted(
                        videoInfo.publishedAt(),
                        videoInfo.title(),
                        videoInfo.description(),
                        joinOrNone(videoInfo.tags()),
                        joinComments(authorComments)
                ))
                .media(Media.builder()
                        .mimeType(MimeType.valueOf("video/mp4"))
                        .data(URI.create(youtubeUrl))
                        .build())
                .build();

        GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder()
                .model(MODEL)
                .responseMimeType("application/json")
                .responseSchema(RESPONSE_SCHEMA_JSON)
                .build();

        Prompt prompt = Prompt.builder()
                .messages(userMessage)
                .chatOptions(options)
                .build();

        long startedAt = System.currentTimeMillis();
        ChatResponse response;
        try {
            response = chatModel.call(prompt);
        } catch (Exception e) {
            // 유튜브는 멀쩡한데 Gemini만 영상을 못 읽는 경우(이전엔 VIDEO_ACCESS)를 구분하고 싶지만,
            // 지금 받는 에러 메시지("Failed to generate content" 등)만으로는 일반적인 호출 실패와
            // 구분할 방법이 없어 EXTRACTION_API_ERROR로 통일한다 (실측 2026-10-06: 같은 영상도
            // 재시도마다 성공/실패가 갈려, 영상 문제가 아니라 호출 자체의 확률적 불안정성으로 보임).
            log.warn("Gemini 호출 실패: {}", videoId, e);
            return failedResult(metadata, videoInfo.title(), ExtractionFailureCode.EXTRACTION_API_ERROR);
        }

        Usage usage = response.getMetadata().getUsage();
        log.info("Gemini 호출 완료: videoId={} durationSec={} elapsedMs={} promptTokens={} completionTokens={} totalTokens={}",
                videoId, videoInfo.durationSec(), System.currentTimeMillis() - startedAt,
                usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens());

        String json = response.getResult().getOutput().getText();

        try {
            LlmResult result = objectMapper.readValue(json, LlmResult.class);
            return toExtractionResult(metadata, videoInfo, result);
        } catch (Exception e) {
            log.warn("Gemini 응답 파싱 실패: videoId={} raw json: {}", videoId, json, e);
            return failedResult(metadata, videoInfo.title(), ExtractionFailureCode.EXTRACTION_RESULT_ERROR);
        }
    }

    private YouTubeContentExtractionResultDto failedResult(
            AnalysisMetadataDto metadata, String title, ExtractionFailureCode failureCode) {
        return new YouTubeContentExtractionResultDto(
                ExtractionStatus.FAILED,
                metadata,
                title,
                null,
                null,
                List.of(),
                List.of(),
                failureCode
        );
    }

    private YouTubeContentExtractionResultDto toExtractionResult(
            AnalysisMetadataDto metadata, YoutubeMetadataClient.VideoInfo videoInfo, LlmResult result) {
        List<PlaceCandidateDto> places = new ArrayList<>();
        // LLM은 우리가 나중에 붙일 candidateId를 알 수 없으므로, 장소 이름으로 식별하게 하고
        // 여기서 candidateId를 직접 채번한 뒤 temporalInfos의 relatedPlaceNames를 이름으로 매칭한다.
        Map<String, String> nameToCandidateId = new java.util.HashMap<>();

        for (int i = 0; i < result.placeCandidates().size(); i++) {
            LlmPlace p = result.placeCandidates().get(i);
            String candidateId = "p" + (i + 1);
            nameToCandidateId.put(p.name(), candidateId);

            places.add(new PlaceCandidateDto(
                    candidateId,
                    p.name(),
                    blankToNull(p.branchName()),
                    blankToNull(p.regionHint()),
                    p.description(),
                    p.category()
            ));
        }

        List<TemporalInfoDto> temporalInfos = result.temporalInfos().stream()
                .map(t -> new TemporalInfoDto(
                        t.subject(),
                        t.originalText(),
                        t.relatedPlaceNames().stream()
                                .map(nameToCandidateId::get)
                                .filter(java.util.Objects::nonNull)
                                .toList(),
                        parseDateOrNull(t.startDate()),
                        parseDateOrNull(t.endDate()),
                        parseTimeOrNull(t.startTime()),
                        parseTimeOrNull(t.endTime())
                ))
                .toList();

        return new YouTubeContentExtractionResultDto(
                ExtractionStatus.SUCCESS,
                metadata,
                videoInfo.title(),
                result.summary(),
                result.category(),
                places,
                temporalInfos,
                null
        );
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private String joinOrNone(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "없음";
        }
        // 외부 API 응답 리스트라 null 원소가 섞여 있을 수 있어 미리 걸러낸다 (리뷰 반영).
        List<String> filtered = values.stream().filter(java.util.Objects::nonNull).toList();
        return filtered.isEmpty() ? "없음" : String.join(", ", filtered);
    }

    private String joinComments(List<String> comments) {
        if (comments == null || comments.isEmpty()) {
            return "없음";
        }
        List<String> filtered = comments.stream().filter(java.util.Objects::nonNull).toList();
        if (filtered.isEmpty()) {
            return "없음";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < filtered.size(); i++) {
            sb.append(i + 1).append(". ").append(filtered.get(i)).append('\n');
        }
        return sb.toString();
    }

    private LocalDate parseDateOrNull(String value) {
        if (blankToNull(value) == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            log.info("날짜 형식 확인 필요: {}", value);
            return null;
        }
    }

    private LocalTime parseTimeOrNull(String value) {
        if (blankToNull(value) == null) {
            return null;
        }
        try {
            return LocalTime.parse(value);
        } catch (DateTimeParseException e) {
            log.info("시간 형식 확인 필요: {}", value);
            return null;
        }
    }
}
