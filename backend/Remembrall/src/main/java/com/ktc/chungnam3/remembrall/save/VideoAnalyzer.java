package com.ktc.chungnam3.remembrall.save;

import com.ktc.chungnam3.remembrall.extraction.dto.YouTubeContentExtractionResultDto;

/**
 * 유튜브 URL 하나로 영상 분석을 수행하는 창구. {@code VideoContentAnalyzer}(Module A 소유,
 * save/analysis/)가 실제 구현체다 - 그 클래스는 건드리지 않고 메서드 참조(analyzer::analyze)로
 * 연결한다. 테스트에서는 실제 Gemini/YouTube 호출 없이 이 인터페이스를 람다로 구현해 고정 결과를
 * 준다({@link com.ktc.chungnam3.remembrall.save.place.PlaceLookup}과 같은 이유 - 팀 관행: 별도
 * 모킹 프레임워크 안 씀).
 */
public interface VideoAnalyzer {
    YouTubeContentExtractionResultDto analyze(String youtubeUrl);
}
