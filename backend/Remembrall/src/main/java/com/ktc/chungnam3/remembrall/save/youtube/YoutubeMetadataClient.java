package com.ktc.chungnam3.remembrall.save.youtube;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

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

    public YoutubeMetadataClient(@Value("${youtube.api.key}") String apiKey) {
        this.apiKey = apiKey;
        this.restClient = RestClient.create(BASE_URL);
    }

    public record VideoInfo(String videoId, String title, String description,
                             List<String> tags, String channelId, int durationSec) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record VideoListResponse(List<VideoItem> items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record VideoItem(Snippet snippet, ContentDetails contentDetails) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Snippet(String title, String description, List<String> tags, String channelId) {
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

    /** 비공개/삭제된 영상이면 예외를 던진다 (호출하는 쪽에서 FailureStage.METADATA_FETCH로 처리). */
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
            throw new IllegalStateException("영상을 찾을 수 없음 (비공개/삭제됨): " + videoId);
        }

        VideoItem item = response.items().get(0);
        Snippet snippet = item.snippet();
        return new VideoInfo(
                videoId,
                snippet.title(),
                snippet.description(),
                snippet.tags() != null ? snippet.tags() : List.of(),
                snippet.channelId(),
                parseDurationSeconds(item.contentDetails().duration())
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
            // 댓글 사용 중지 등으로 403/404가 날 수 있음 - 정상적인 케이스로 취급
            return List.of();
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
