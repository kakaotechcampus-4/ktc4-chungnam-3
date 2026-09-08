package com.saveagent.agent.save;

import com.saveagent.agent.save.dto.ContentAnalysis;
import com.saveagent.agent.save.dto.PlaceCandidate;
import com.saveagent.agent.save.dto.ScheduleCandidate;
import com.saveagent.agent.save.dto.TriggerSpec;
import com.saveagent.agent.save.dto.TriggerType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TriggerDesignerTest {

    private final TriggerDesigner triggerDesigner = new TriggerDesigner();

    @Test
    void 좌표가_있으면_LOCATION_트리거가_생성된다() {
        ContentAnalysis analysis = analysisOf(List.of(placeWithCoords(36.32, 127.42)), null);

        List<TriggerSpec> triggers = triggerDesigner.design(analysis);

        assertEquals(1, triggers.size());
        assertEquals(TriggerType.LOCATION, triggers.get(0).type());
        assertEquals(36.32, triggers.get(0).params().get("lat"));
        assertEquals(127.42, triggers.get(0).params().get("lng"));
    }

    @Test
    void 기간이_있고_연도가_확인되면_SCHEDULE_트리거가_생성된다() {
        ScheduleCandidate schedule = new ScheduleCandidate(
                "대전 빵축제", LocalDate.of(2026, 7, 18), LocalDate.of(2026, 7, 27), true);
        ContentAnalysis analysis = analysisOf(List.of(), schedule);

        List<TriggerSpec> triggers = triggerDesigner.design(analysis);

        assertEquals(1, triggers.size());
        assertEquals(TriggerType.SCHEDULE, triggers.get(0).type());
    }

    @Test
    void 좌표와_기간이_모두_있으면_두_트리거가_모두_생성된다() {
        ScheduleCandidate schedule = new ScheduleCandidate(
                "대전 빵축제", LocalDate.of(2026, 7, 18), LocalDate.of(2026, 7, 27), true);
        ContentAnalysis analysis = analysisOf(List.of(placeWithCoords(36.32, 127.42)), schedule);

        List<TriggerSpec> triggers = triggerDesigner.design(analysis);

        assertEquals(2, triggers.size());
        assertTrue(triggers.stream().anyMatch(t -> t.type() == TriggerType.LOCATION));
        assertTrue(triggers.stream().anyMatch(t -> t.type() == TriggerType.SCHEDULE));
    }

    @Test
    void 좌표와_기간이_모두_없으면_트리거가_0개다() {
        // 이 설계의 핵심 경로: 정보가 없으면 트리거를 지어내지 않고 0개를 반환한다 (정상 경로, 실패 아님).
        ContentAnalysis analysis = analysisOf(List.of(), null);

        List<TriggerSpec> triggers = triggerDesigner.design(analysis);

        assertEquals(0, triggers.size());
    }

    @Test
    void 좌표가_없는_장소는_트리거를_만들지_않는다() {
        PlaceCandidate placeWithoutCoords =
                new PlaceCandidate("○○로스터리", "대흥동 ○○로스터리", "대전 중구 대흥동", null, null);
        ContentAnalysis analysis = analysisOf(List.of(placeWithoutCoords), null);

        List<TriggerSpec> triggers = triggerDesigner.design(analysis);

        assertEquals(0, triggers.size());
    }

    @Test
    void 기간은_있지만_연도가_불확실하면_SCHEDULE_트리거를_만들지_않는다() {
        ScheduleCandidate schedule = new ScheduleCandidate(
                "빵축제", LocalDate.of(2026, 7, 18), LocalDate.of(2026, 7, 27), false);
        ContentAnalysis analysis = analysisOf(List.of(), schedule);

        List<TriggerSpec> triggers = triggerDesigner.design(analysis);

        assertEquals(0, triggers.size());
    }

    private PlaceCandidate placeWithCoords(double lat, double lng) {
        return new PlaceCandidate("테스트 장소", "raw", "대전", lat, lng);
    }

    private ContentAnalysis analysisOf(List<PlaceCandidate> places, ScheduleCandidate schedule) {
        return new ContentAnalysis("video-1", "테스트 제목", "테스트 요약", List.of(), places, schedule);
    }
}
