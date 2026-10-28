package com.ktc.chungnam3.remembrall.common.geo;

public final class GeoDistance {
    private GeoDistance() {}

    public static double meters(double fromLatitude, double fromLongitude, double toLatitude, double toLongitude) {
        double latitudeDelta = Math.toRadians(toLatitude - fromLatitude);
        double longitudeDelta = Math.toRadians(toLongitude - fromLongitude);
        double haversine = Math.pow(Math.sin(latitudeDelta / 2), 2)
                + Math.cos(Math.toRadians(fromLatitude)) * Math.cos(Math.toRadians(toLatitude))
                * Math.pow(Math.sin(longitudeDelta / 2), 2);
        return 6371000.0 * 2 * Math.asin(Math.sqrt(Math.min(1.0, Math.max(0.0, haversine))));
    }
}
