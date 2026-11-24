package com.veloster.vehicle;
/** Monotonic freshness arbitration: vehicle telemetry > GNSS. No fabricated zero when both fail. */
public final class SpeedArbiter {
    public static final long VEHICLE_TIMEOUT_MS=2000, GPS_TIMEOUT_MS=4000;
    private float vehicle=Float.NaN,gps=Float.NaN;
    private long vehicleAt=-1,gpsAt=-1;
    public void vehicle(float kmh,long elapsedMs) { if(valid(kmh)){vehicle=kmh;vehicleAt=elapsedMs;} }
    public void gps(float kmh,long elapsedMs) { if(valid(kmh)){gps=kmh;gpsAt=elapsedMs;} }
    private boolean valid(float x){return !Float.isNaN(x)&&!Float.isInfinite(x)&&x>=0&&x<=260;}
    public float speed(long now){if(fresh(now,vehicleAt,VEHICLE_TIMEOUT_MS))return vehicle;if(fresh(now,gpsAt,GPS_TIMEOUT_MS))return gps;return Float.NaN;}
    public String source(long now){return fresh(now,vehicleAt,VEHICLE_TIMEOUT_MS)?"차량":fresh(now,gpsAt,GPS_TIMEOUT_MS)?"GPS":"미수신";}
    private boolean fresh(long n,long at,long timeout){return at>=0&&n>=at&&n-at<=timeout;}
    public void clearGps(){gpsAt=-1;gps=Float.NaN;}
}
