package com.veloster.vehicle;
/** Monotonic freshness arbitration: GNSS > OEM BCM > external vehicle telemetry. No fabricated zero when both fail. */
public final class SpeedArbiter {
    public static final long VEHICLE_TIMEOUT_MS=2000, GPS_TIMEOUT_MS=4000;
    private float vehicle=Float.NaN,gps=Float.NaN,bcm=Float.NaN;
    private long vehicleAt=-1,gpsAt=-1,bcmAt=-1;
    public void bcm(float kmh,long at){if(valid(kmh)){if(at>=bcmAt){bcm=kmh;bcmAt=at;}}else if(at>=bcmAt){bcm=Float.NaN;bcmAt=-1;}}
    public String bcmReading(long n){return fresh(n,bcmAt,VEHICLE_TIMEOUT_MS)?String.format(java.util.Locale.US,"%.1f km/h",bcm):"미수신";}
    public void vehicle(float kmh,long elapsedMs) { if(valid(kmh)){if(elapsedMs>=vehicleAt){vehicle=kmh;vehicleAt=elapsedMs;}}else if(elapsedMs>=vehicleAt){vehicle=Float.NaN;vehicleAt=-1;} }
    public void gps(float kmh,long elapsedMs) { if(elapsedMs<gpsAt)return;if(valid(kmh)){gps=kmh;gpsAt=elapsedMs;}else clearGps(); }
    private boolean valid(float x){return !Float.isNaN(x)&&!Float.isInfinite(x)&&x>=0&&x<=260;}
    public float speed(long now){if(fresh(now,gpsAt,GPS_TIMEOUT_MS))return gps;if(fresh(now,bcmAt,VEHICLE_TIMEOUT_MS))return bcm;if(fresh(now,vehicleAt,VEHICLE_TIMEOUT_MS))return vehicle;return Float.NaN;}
    public String source(long now){return fresh(now,gpsAt,GPS_TIMEOUT_MS)?"GPS":fresh(now,bcmAt,VEHICLE_TIMEOUT_MS)?"5W BCM":fresh(now,vehicleAt,VEHICLE_TIMEOUT_MS)?"외부 차속":"미수신";}
    private boolean fresh(long n,long at,long timeout){return at>=0&&n>=at&&n-at<=timeout;}
    public String vehicleReading(long n){return fresh(n,vehicleAt,VEHICLE_TIMEOUT_MS)?String.format(java.util.Locale.US,"%.1f km/h",vehicle):"미수신";}
    public String gpsReading(long n){return fresh(n,gpsAt,GPS_TIMEOUT_MS)?String.format(java.util.Locale.US,"%.1f km/h",gps):"미수신";}
    public void clearGps(){gpsAt=-1;gps=Float.NaN;}
}
