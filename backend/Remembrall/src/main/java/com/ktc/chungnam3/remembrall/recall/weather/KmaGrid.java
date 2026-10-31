package com.ktc.chungnam3.remembrall.recall.weather;

public record KmaGrid(int x, int y) {
    public static KmaGrid from(double latitude, double longitude) {
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || Math.abs(latitude) >= 90 || Math.abs(longitude) > 180) {
            throw new IllegalArgumentException("Invalid weather coordinates");
        }
        double re = 6371.00877 / 5.0;
        double slat1 = Math.toRadians(30);
        double slat2 = Math.toRadians(60);
        double sn = Math.log(Math.cos(slat1) / Math.cos(slat2))
                / Math.log(Math.tan(Math.PI / 4 + slat2 / 2) / Math.tan(Math.PI / 4 + slat1 / 2));
        double sf = Math.pow(Math.tan(Math.PI / 4 + slat1 / 2), sn) * Math.cos(slat1) / sn;
        double ro = re * sf / Math.pow(Math.tan(Math.PI / 4 + Math.toRadians(38) / 2), sn);
        double ra = re * sf / Math.pow(Math.tan(Math.PI / 4 + Math.toRadians(latitude) / 2), sn);
        double theta = Math.toRadians(longitude - 126);
        if (theta > Math.PI) theta -= 2 * Math.PI;
        if (theta < -Math.PI) theta += 2 * Math.PI;
        theta *= sn;
        int x = (int) Math.floor(ra * Math.sin(theta) + 43 + 0.5);
        int y = (int) Math.floor(ro - ra * Math.cos(theta) + 136 + 0.5);
        if (x < 1 || x > 149 || y < 1 || y > 253) {
            throw new IllegalArgumentException("Outside KMA forecast grid");
        }
        return new KmaGrid(x, y);
    }
}
