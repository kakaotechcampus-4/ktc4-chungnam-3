package com.saveagent.agent.save;

import com.saveagent.agent.save.dto.ContentAnalysis;
import com.saveagent.agent.save.dto.PlaceCandidate;
import com.saveagent.agent.save.dto.ScheduleCandidate;
import com.saveagent.agent.save.dto.TriggerSpec;
import com.saveagent.agent.save.dto.TriggerType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 트리거 판단. 규칙 기반이며 LLM을 사용하지 않는다.
 * <p>
 * 핵심 원칙: 정보가 없으면 트리거를 만들지 않는다. 좌표·기간이 둘 다 없으면
 * 트리거 0개를 반환하는 것이 실패가 아니라 정상 경로다.
 */
public class TriggerDesigner {

    // TODO: 협의 필요 - 위치 트리거 반경(radiusM) 기본값. 설계 문서 예시의 500m를 임시로 사용한다.
    private static final int DEFAULT_RADIUS_METERS = 500;

    // TODO: 협의 필요 - 기간 트리거 알림 선행일수(notifyBeforeDays) 기본값. 설계 문서 예시의 3일을 임시로 사용한다.
    private static final int DEFAULT_NOTIFY_BEFORE_DAYS = 3;

    public List<TriggerSpec> design(ContentAnalysis analysis) {
        List<TriggerSpec> triggers = new ArrayList<>();

        triggers.addAll(designLocationTriggers(analysis.places()));

        designScheduleTrigger(analysis.schedule())
                .ifPresent(triggers::add);

        return triggers;
    }

    private List<TriggerSpec> designLocationTriggers(List<PlaceCandidate> places) {
        List<TriggerSpec> triggers = new ArrayList<>();
        if (places == null) {
            return triggers;
        }

        for (PlaceCandidate place : places) {
            if (place.lat() == null || place.lng() == null) {
                // 좌표 없음 -> 트리거를 만들지 않는다 (좌표를 지어내면 엉뚱한 곳에서 알림이 뜬다).
                // TODO: 협의 필요 - 좌표는 없고 regionText(지역 텍스트)만 있는 경우 처리 방식이 미정이다.
                //  1) 트리거를 아예 걸지 않는다  2) regionText 중심으로 넓은 반경 트리거를 건다
                //  현재는 "정보가 없으면 만들지 않는다" 원칙에 따라 1)번(트리거 미생성)으로 처리한다.
                continue;
            }

            Map<String, Object> params = new LinkedHashMap<>();
            params.put("lat", place.lat());
            params.put("lng", place.lng());
            params.put("radiusM", DEFAULT_RADIUS_METERS);

            triggers.add(new TriggerSpec(TriggerType.LOCATION, params));
        }

        return triggers;
    }

    private Optional<TriggerSpec> designScheduleTrigger(ScheduleCandidate schedule) {
        if (schedule == null) {
            return Optional.empty();
        }
        if (schedule.startDate() == null || schedule.endDate() == null) {
            return Optional.empty();
        }
        if (!schedule.yearConfirmed()) {
            // 연도가 불확실하면 트리거를 생성하지 않는다 (작년 축제가 다시 오발동하는 것을 방지).
            return Optional.empty();
        }

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("startDate", schedule.startDate());
        params.put("endDate", schedule.endDate());
        params.put("notifyBeforeDays", DEFAULT_NOTIFY_BEFORE_DAYS);

        return Optional.of(new TriggerSpec(TriggerType.SCHEDULE, params));
    }
}
