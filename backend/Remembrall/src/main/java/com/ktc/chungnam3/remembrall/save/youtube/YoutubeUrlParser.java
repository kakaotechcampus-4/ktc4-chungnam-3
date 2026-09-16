package com.ktc.chungnam3.remembrall.save.youtube;

import java.net.URI;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 유튜브 URL(watch/youtu.be/shorts/embed 등)에서 video_id를 뽑아낸다.
 */
public final class YoutubeUrlParser {

    private static final Pattern QUERY_V = Pattern.compile("(?:^|&)v=([^&]+)");
    private static final Pattern PATH_SEGMENT = Pattern.compile("^/(shorts|embed|live)/([^/?]+)");

    private YoutubeUrlParser() {
    }

    public static Optional<String> extractVideoId(String url) {
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }

        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
        String path = uri.getPath() == null ? "" : uri.getPath();

        if (host.equals("youtu.be")) {
            String id = path.replaceFirst("^/", "").split("/")[0];
            return id.isBlank() ? Optional.empty() : Optional.of(id);
        }

        if (host.endsWith("youtube.com")) {
            if (path.equals("/watch") && uri.getQuery() != null) {
                Matcher m = QUERY_V.matcher(uri.getQuery());
                if (m.find()) {
                    return Optional.of(m.group(1));
                }
            }
            Matcher m = PATH_SEGMENT.matcher(path);
            if (m.find()) {
                return Optional.of(m.group(2));
            }
        }

        return Optional.empty();
    }
}
