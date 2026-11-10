package com.ktc.chungnam3.remembrall.save.place;

/**
 * 업소명에 찾는 가게 이름이 들어있는지 판단한다 - 카카오 검증({@link KakaoPlaceClient})과 공공상가정보 브랜드
 * 비교({@link PlaceResolver}의 matchesBrand)가 같은 규칙을 쓴다(2026-11-08, PR 리뷰 doheelab-coder).
 * <ul>
 *   <li>원래 글자 그대로(대소문자 무시) 들어있으면 통과 - 단어 중간이어도("원조할매순대국"의 "할매순대국").</li>
 *   <li>공백을 지워야만 맞으면 찾는 이름이 어떤 단어의 첫머리에서 시작할 때만 통과 - "오일내 둔산시청점"의
 *   "오일내둔산"은 통과, "성공 차돌박이"의 "공차"는 탈락. 공백만 지우면 짧은 브랜드가 엉뚱한 가게에 맞아,
 *   카카오 쪽은 두 가게가 한 Place로 합쳐지고 공공상가정보 쪽은 확정될 가게가 되묻기로 바뀌었다.</li>
 * </ul>
 */
final class PlaceNameMatcher {

    static boolean matches(String placeName, String name) {
        if (placeName == null) {
            return false;
        }
        if (placeName.toLowerCase().contains(name.toLowerCase())) {
            return true;
        }
        String[] words = placeName.toLowerCase().trim().split("\\s+");
        String joined = String.join("", words);
        String target = name.toLowerCase().replaceAll("\\s+", "");
        int offset = 0;
        for (String word : words) {
            if (joined.startsWith(target, offset)) {
                return true;
            }
            offset += word.length();
        }
        return false;
    }

    private PlaceNameMatcher() {
    }
}
