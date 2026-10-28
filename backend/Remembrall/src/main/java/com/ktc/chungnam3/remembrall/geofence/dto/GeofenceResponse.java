package com.ktc.chungnam3.remembrall.geofence.dto;

import java.util.List;
import java.util.UUID;

public record GeofenceResponse(List<Object> regions, ResyncGeofence resync, List<PlaceGeofence> places) {
    public static GeofenceResponse empty() {
        return new GeofenceResponse(List.of(), null, List.of());
    }

    public record ResyncGeofence(String geofenceId, double latitude, double longitude, double radiusMeters) {}

    public record PlaceGeofence(String geofenceId, UUID placeId, double latitude, double longitude, int radiusMeters) {}
}
