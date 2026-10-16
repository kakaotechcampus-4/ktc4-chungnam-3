// 지원 플랫폼 파서 레지스트리. 플랫폼 추가는 여기에 등록한다.
// 공유 텍스트(제목 등이 섞여 있어도 된다)에서 http(s) URL 을 앞에서부터 보고, 어느 파서든 처음 통과한 링크를 쓴다.

import { youtubeParser } from "./youtube";

// url 은 서버에 보낼 정규화된 주소다.
export type SharedLink = { platform: string; videoId: string; url: string };

export interface LinkParser {
    platform: string;
    // URL 하나가 지원 링크면 정규화 결과, 아니면 null.
    parse(url: string): SharedLink | null;
}

export const parsers: readonly LinkParser[] = [youtubeParser];

// ASCII URL 문자만 잇는다. 한글 등이 바로 붙어 있어도 거기서 끊긴다.
const URL_IN_TEXT = /https?:\/\/[A-Za-z0-9\-._~:/?#[\]@!$&'()*+,;=%]+/gi;

export function findSharedLink(text: string): SharedLink | null {
    for (const [url] of text.matchAll(URL_IN_TEXT)) {
        for (const parser of parsers) {
            const link = parser.parse(url);
            if (link != null) return link;
        }
    }
    return null;
}
