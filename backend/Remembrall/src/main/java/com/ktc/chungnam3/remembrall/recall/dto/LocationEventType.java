package com.ktc.chungnam3.remembrall.recall.dto;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum LocationEventType {
    ENTER,
    DWELL;

    @JsonCreator
    public static LocationEventType from(String value) {
        return valueOf(value);
    }
}
