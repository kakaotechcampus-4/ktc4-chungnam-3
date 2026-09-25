package com.ktc.chungnam3.remembrall.save.analysis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktc.chungnam3.remembrall.extraction.dto.AnalysisMetadataDto;
import com.ktc.chungnam3.remembrall.extraction.dto.EvidenceDto;
import com.ktc.chungnam3.remembrall.extraction.dto.PlaceCandidateDto;
import com.ktc.chungnam3.remembrall.extraction.dto.TemporalInfoDto;
import com.ktc.chungnam3.remembrall.extraction.dto.YouTubeContentExtractionResultDto;
import com.ktc.chungnam3.remembrall.extraction.type.EvidenceSource;
import com.ktc.chungnam3.remembrall.extraction.type.ExtractionStatus;
import org.springframework.ai.chat.messages.UserMessage;
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
@Component
public class VideoContentAnalyzer {

    // TODO: 협의 필요 - 실제 사용할 모델. 이 SDK 버전(google-genai 1.65.0)에는 3.7/3.8 계열이 아직 없어
    //  현재 시점에 쓸 수 있는 것 중 가장 최신인 3.6 Flash로 우선 잡아둔다.
    private static final GoogleGenAiChatModel.ChatModel MODEL = GoogleGenAiChatModel.ChatModel.GEMINI_3_6_FLASH;

    private static final String ANALYSIS_VERSION = "v1";
    private static final String PROMPT_VERSION = "v3";

    private static final String PROMPT_TEMPLATE = """
            너는 여행·맛집 유튜브 영상에서 정보를 추출하는 분석가다.
            반드시 아래 규칙을 지켜서 정해진 JSON 형식으로만 응답해라.

            영상 게시일: %s

            - summary는 영상의 핵심 주제, 소개 대상, 주요 특징과 영상에서 명시된 추천 상황·제약을 간결하게 적어라.
              장소가 없어도 요약은 남겨라.
            - 장소 정보가 없으면 placeCandidates를 빈 배열로 두고 절대 추측하지 마라.
            - 장소 이름은 상호명(name)·지점명(branchName)·지역 단서(regionHint)로 나눠 적어라.
              예: "대전 성심당 본점"이면 name="성심당", branchName="본점", regionHint="대전".
              지점이나 지역이 확인되지 않으면 해당 필드는 빈 문자열로 둬라.
            - regionHint에는 시·도(예: 서울특별시, 대전광역시)를 반드시 포함해라. 구·동까지 확인되면
              "대전 중구"처럼 시·도 뒤에 이어서 적되, 시·도 자체를 빼지는 마라 - 장소 검색 단계가
              시·도 단위로만 지역을 대조하기 때문이다. 구·동까지도 불확실하면 시·도까지만 적어라.
            - 좌표는 추출하지 않는다 (이 단계의 책임이 아니다).
            - 여러 장소가 있으면 각각 분리해서 반환해라.
            - evidence의 detail에는 근거 위치나 내용을 간단히 적어라 (예: "화면 자막 0:12", "설명란 첫 줄").
            - relatedPlaceNames에는 이 기간과 연결되는 placeCandidates의 name을 그대로 적어라. 관계가 없으면 빈 배열로 둬라.
            - startDate/endDate에 연도가 없는 날짜가 나오면(예: "10월 3일까지") 위 영상 게시일과 같은 연도로 채우고,
              uncertainties에 "연도 추정"이라고 적어라. 월·일 자체를 확인할 수 없으면 빈 문자열로 두고 절대 추정하지 마라.
            - startTime/endTime을 확인할 수 없으면 빈 문자열로 둬라. 절대 추정하지 마라.
            - suggestedOrder는 영상에서 방문 순서가 명시된 경우에만 1부터 채우고, 없으면 0으로 둬라.
            - 확실하게 판단하지 못한 내용은 각 항목의 uncertainties에 짧게 적어라.
            """;

    private static final String RESPONSE_SCHEMA_JSON = """
            {
              "type": "object",
              "properties": {
                "summary": { "type": "string" },
                "summaryUncertainties": { "type": "array", "items": { "type": "string" } },
                "placeCandidates": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "name": { "type": "string" },
                      "branchName": { "type": "string" },
                      "regionHint": { "type": "string" },
                      "description": { "type": "string" },
                      "suggestedOrder": { "type": "integer", "description": "0이면 순서 명시 없음" },
                      "evidence": {
                        "type": "array",
                        "items": {
                          "type": "object",
                          "properties": {
                            "source": { "type": "string", "enum": ["VIDEO_AUDIO", "VIDEO_TEXT", "VIDEO_VISUAL", "TITLE", "DESCRIPTION"] },
                            "detail": { "type": "string" }
                          },
                          "required": ["source", "detail"]
                        }
                      },
                      "uncertainties": { "type": "array", "items": { "type": "string" } }
                    },
                    "required": ["name", "branchName", "regionHint", "description", "suggestedOrder", "evidence", "uncertainties"]
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
                      "endTime": { "type": "string", "description": "HH:MM, 모르면 빈 문자열" },
                      "evidence": {
                        "type": "array",
                        "items": {
                          "type": "object",
                          "properties": {
                            "source": { "type": "string", "enum": ["VIDEO_AUDIO", "VIDEO_TEXT", "VIDEO_VISUAL", "TITLE", "DESCRIPTION"] },
                            "detail": { "type": "string" }
                          },
                          "required": ["source", "detail"]
                        }
                      },
                      "uncertainties": { "type": "array", "items": { "type": "string" } }
                    },
                    "required": ["subject", "originalText", "relatedPlaceNames", "startDate", "endDate", "startTime", "endTime", "evidence", "uncertainties"]
                  }
                }
              },
              "required": ["summary", "summaryUncertainties", "placeCandidates", "temporalInfos"]
            }
            """;

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record LlmResult(String summary, List<String> summaryUncertainties,
                              List<LlmPlace> placeCandidates, List<LlmTemporal> temporalInfos) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record LlmPlace(String name, String branchName, String regionHint, String description,
                             Integer suggestedOrder, List<LlmEvidence> evidence, List<String> uncertainties) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record LlmTemporal(String subject, String originalText, List<String> relatedPlaceNames,
                                String startDate, String endDate, String startTime, String endTime,
                                List<LlmEvidence> evidence, List<String> uncertainties) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record LlmEvidence(EvidenceSource source, String detail) {
    }

    private final ChatModel chatModel;
    // Spring Boot 4.1의 자동 구성 ObjectMapper 빈은 Jackson 3(tools.jackson.*) 타입이라
    // Jackson 2 API(com.fasterxml.jackson.*)로 직접 파싱하기 위해 별도 인스턴스를 쓴다.
    private final ObjectMapper objectMapper = new ObjectMapper();

    public VideoContentAnalyzer(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public YouTubeContentExtractionResultDto analyze(String youtubeUrl, LocalDate publishedAt) {
        UserMessage userMessage = UserMessage.builder()
                .text(PROMPT_TEMPLATE.formatted(publishedAt))
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

        ChatResponse response = chatModel.call(prompt);
        String json = response.getResult().getOutput().getText();

        AnalysisMetadataDto metadata = new AnalysisMetadataDto(ANALYSIS_VERSION, MODEL.getValue(), PROMPT_VERSION);

        try {
            LlmResult result = objectMapper.readValue(json, LlmResult.class);
            return toExtractionResult(metadata, result);
        } catch (Exception e) {
            throw new IllegalStateException("Gemini 응답 파싱 실패, raw json: " + json, e);
        }
    }

    private YouTubeContentExtractionResultDto toExtractionResult(AnalysisMetadataDto metadata, LlmResult result) {
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
                    (p.suggestedOrder() == null || p.suggestedOrder() == 0) ? null : p.suggestedOrder(),
                    toEvidenceDtos(p.evidence()),
                    p.uncertainties()
            ));
        }

        List<TemporalInfoDto> temporalInfos = result.temporalInfos().stream()
                .map(t -> {
                    List<String> uncertainties = new ArrayList<>(t.uncertainties());
                    return new TemporalInfoDto(
                            t.subject(),
                            t.originalText(),
                            t.relatedPlaceNames().stream()
                                    .map(nameToCandidateId::get)
                                    .filter(java.util.Objects::nonNull)
                                    .toList(),
                            parseDateOrNull(t.startDate(), uncertainties),
                            parseDateOrNull(t.endDate(), uncertainties),
                            parseTimeOrNull(t.startTime(), uncertainties),
                            parseTimeOrNull(t.endTime(), uncertainties),
                            toEvidenceDtos(t.evidence()),
                            uncertainties
                    );
                })
                .toList();

        return new YouTubeContentExtractionResultDto(
                ExtractionStatus.SUCCESS,
                metadata,
                result.summary(),
                result.summaryUncertainties(),
                places,
                temporalInfos,
                null
        );
    }

    private List<EvidenceDto> toEvidenceDtos(List<LlmEvidence> evidence) {
        return evidence.stream()
                .map(e -> new EvidenceDto(e.source(), e.detail()))
                .toList();
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private LocalDate parseDateOrNull(String value, List<String> uncertainties) {
        if (blankToNull(value) == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            uncertainties.add("날짜 형식 확인 필요: " + value);
            return null;
        }
    }

    private LocalTime parseTimeOrNull(String value, List<String> uncertainties) {
        if (blankToNull(value) == null) {
            return null;
        }
        try {
            return LocalTime.parse(value);
        } catch (DateTimeParseException e) {
            uncertainties.add("시간 형식 확인 필요: " + value);
            return null;
        }
    }
}
