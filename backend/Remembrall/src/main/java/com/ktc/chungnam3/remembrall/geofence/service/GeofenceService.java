package com.ktc.chungnam3.remembrall.geofence.service;

import com.ktc.chungnam3.remembrall.common.geo.GeoDistance;
import com.ktc.chungnam3.remembrall.domain.memberconsent.ConsentType;
import com.ktc.chungnam3.remembrall.geofence.config.GeofenceProperties;
import com.ktc.chungnam3.remembrall.geofence.dto.GeofenceQuery;
import com.ktc.chungnam3.remembrall.geofence.dto.GeofenceResponse;
import com.ktc.chungnam3.remembrall.repository.MemberConsentRepository;
import com.ktc.chungnam3.remembrall.repository.TriggerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class GeofenceService {
    private final TriggerRepository triggers;
    private final MemberConsentRepository consents;
    private final GeofenceProperties properties;

    public GeofenceService(TriggerRepository triggers, MemberConsentRepository consents, GeofenceProperties properties) {
        this.triggers = triggers;
        this.consents = consents;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public GeofenceResponse list(UUID memberId, GeofenceQuery query) {
        if (!consents.existsByMemberIdAndConsentTypeAndWithdrawnAtIsNull(memberId, ConsentType.LOCATION_BASED_SERVICE)) {
            return GeofenceResponse.empty();
        }

        int limit = (query.platform() == GeofenceQuery.Platform.IOS ? properties.iosLimit() : properties.androidLimit()) - 1;
        List<NearbyPlace> selected = triggers.findEligibleGeofencePlaces(memberId).stream()
                .map(place -> new NearbyPlace(place.getPlaceId(), place.getLatitude(), place.getLongitude(),
                        GeoDistance.meters(query.latitude(), query.longitude(), place.getLatitude(), place.getLongitude())))
                .sorted(Comparator.comparingDouble(NearbyPlace::distanceMeters)
                        .thenComparing(place -> place.placeId().toString()))
                .limit(limit)
                .toList();
        if (selected.isEmpty()) {
            return GeofenceResponse.empty();
        }

        double radius = Math.max(properties.resyncMinRadiusMeters(),
                selected.getLast().distanceMeters() + properties.resyncMarginMeters());
        var resync = new GeofenceResponse.ResyncGeofence("resync", query.latitude(), query.longitude(), radius);
        var places = selected.stream().map(place -> new GeofenceResponse.PlaceGeofence(
                "place:" + place.placeId(), place.placeId(), place.latitude(), place.longitude(), properties.placeRadiusMeters()))
                .toList();
        return new GeofenceResponse(List.of(), resync, places);
    }

    private record NearbyPlace(UUID placeId, double latitude, double longitude, double distanceMeters) {}
}
