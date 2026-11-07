package com.ktc.chungnam3.remembrall.save.youtube;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 유튜브 데이터 API v3를 직접 REST로 호출한다.
 * 공식 자바 클라이언트(google-api-services-youtube)는 OAuth 흐름 위주로 설계돼 있어
 * 공개 영상의 메타데이터/댓글만 읽는 용도에는 과하다 - API 키만 있으면 되는 REST 호출로 대체했다.
 */
@Component
public class YoutubeMetadataClient {

    private static final String BASE_URL = "https://www.googleapis.com/youtube/v3";
    private static final Pattern DURATION_PATTERN = Pattern.compile("PT(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?");

    private final RestClient restClient;
    private final String apiKey;

    /**
     * connect/read 타임아웃을 명시적으로 건다(2026-11-02 추가) - 이전엔 둘 다 무제한이라 YouTube가
     * 멈추면 분석 스레드가 끝없이 블로킹됐다(LocationIQ/공공데이터 클라이언트는 2026-10-01에 이미
     * 고쳐졌는데 이 클라이언트만 빠져 있었음). 기본값은 같은 REST 직접 호출 패턴인 LocationIQ
     * ({@link com.ktc.chungnam3.remembrall.save.place.PlaceSearchClient})와 동일하게 맞췄다.
     */
    public YoutubeMetadataClient(
            @Value("${youtube.api.key}") String apiKey,
            @Value("${youtube.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${youtube.read-timeout-ms:3000}") int readTimeoutMs) {
        this.apiKey = apiKey;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        this.restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .requestFactory(requestFactory)
                .build();
    }

    public record VideoInfo(String videoId, String title, String description,
                             List<String> tags, String channelId, int durationSec, LocalDate publishedAt) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record VideoListResponse(List<VideoItem> items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record VideoItem(Snippet snippet, ContentDetails contentDetails) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Snippet(String title, String description, List<String> tags, String channelId, String publishedAt) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ContentDetails(String duration) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CommentThreadListResponse(List<CommentThreadItem> items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CommentThreadItem(CommentThreadSnippet snippet) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CommentThreadSnippet(TopLevelCommentWrapper topLevelComment) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TopLevelCommentWrapper(CommentSnippet snippet) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CommentSnippet(String textDisplay, AuthorChannelId authorChannelId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AuthorChannelId(String value) {
    }

    /**
     * 유튜브가 "이 영상을 찾을 수 없다"고 명확히 확인해준 경우(비공개/삭제)에만 던지는 전용 예외.
     * 호출하는 쪽에서 이 예외만 따로 잡아서 SourceStatus.UNAVAILABLE로 확정한다 - 그 외 실패(네트워크
     * 오류 등)는 "접근 불가가 확인된 것"이 아니므로 이 예외를 쓰지 않는다.
     */
    public static class VideoUnavailableException extends RuntimeException {
        public VideoUnavailableException(String message) {
            super(message);
        }
    }

    /** 비공개/삭제된 영상이면 VideoUnavailableException을 던진다 (그 외 실패는 일반 예외). */
    public VideoInfo getVideoInfo(String videoId) {
        VideoListResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/videos")
                        .queryParam("part", "snippet,contentDetails")
                        .queryParam("id", videoId)
                        .queryParam("key", apiKey)
                        .build())
                .retrieve()
                .body(VideoListResponse.class);

        if (response == null || response.items() == null || response.items().isEmpty()) {
            throw new VideoUnavailableException("영상을 찾을 수 없음 (비공개/삭제됨): " + videoId);
        }

        VideoItem item = response.items().get(0);
        Snippet snippet = item.snippet();
        return new VideoInfo(
                videoId,
                snippet.title(),
                snippet.description(),
                snippet.tags() != null ? snippet.tags() : List.of(),
                snippet.channelId(),
                parseDurationSeconds(item.contentDetails().duration()),
                OffsetDateTime.parse(snippet.publishedAt()).toLocalDate()
        );
    }

    /** 채널 소유자(작성자)가 단 댓글만 골라서 반환한다. 댓글이 막혀있으면 빈 리스트를 반환한다. */
    public List<String> getAuthorComments(String videoId, String channelId) {
        try {
            CommentThreadListResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/commentThreads")
                            .queryParam("part", "snippet")
                            .queryParam("videoId", videoId)
                            .queryParam("maxResults", 50)
                            .queryParam("order", "relevance")
                            .queryParam("textFormat", "plainText")
                            .queryParam("key", apiKey)
                            .build())
                    .retrieve()
                    .body(CommentThreadListResponse.class);

            if (response == null || response.items() == null) {
                return List.of();
            }

            return response.items().stream()
                    .map(item -> item.snippet().topLevelComment().snippet())
                    .filter(snippet -> snippet.authorChannelId() != null
                            && channelId.equals(snippet.authorChannelId().value()))
                    .map(CommentSnippet::textDisplay)
                    .toList();
        } catch (HttpClientErrorException e) {
            // 댓글 기능이 꺼진 영상은 commentsDisabled로 403이 남 - 정상적인 케이스로 취급.
            // quotaExceeded, keyInvalid 등 그 외 4xx는 실제 장애이므로 그대로 다시 던진다.
            if (e.getResponseBodyAsString().contains("commentsDisabled")) {
                return List.of();
            }
            throw e;
        }
    }

    private int parseDurationSeconds(String iso8601Duration) {
        if (iso8601Duration == null) {
            return 0;
        }
        Matcher m = DURATION_PATTERN.matcher(iso8601Duration);
        if (!m.matches()) {
            return 0;
        }
        int hours = m.group(1) != null ? Integer.parseInt(m.group(1)) : 0;
        int minutes = m.group(2) != null ? Integer.parseInt(m.group(2)) : 0;
        int seconds = m.group(3) != null ? Integer.parseInt(m.group(3)) : 0;
        return hours * 3600 + minutes * 60 + seconds;
    }
}
