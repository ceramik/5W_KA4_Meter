package com.veloster.vehicle;
import android.app.*;
import android.content.*;
import android.location.*;
import android.os.*;
import java.util.Calendar;

public final class MeterService extends Service implements LocationListener {
    static final String[] REGIONS={"서울","경기","인천","부산","대구","광주","대전","울산","세종","강원","충북","충남","전북","전남","경북","경남","제주"};
    final SpeedArbiter speed=new SpeedArbiter();
    final Gen5wSpeedReader bcm=new Gen5wSpeedReader();
    private final java.util.concurrent.ScheduledExecutorService bcmWorker=java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
    private volatile boolean destroyed;
    private void startBcm(){bcmWorker.scheduleWithFixedDelay(()->{if(destroyed)return;float v=bcm.read();long at=SystemClock.elapsedRealtime();if(!Float.isNaN(v))handler.post(()->{if(!destroyed)speed.bcm(v,at);});},0,500,java.util.concurrent.TimeUnit.MILLISECONDS);}
    final TaxiFare fare=new TaxiFare(), receiptFare=new TaxiFare();
    int region, surchargeMode=0, zone=-1, startZone=-1, pendingZone=-1, zoneCount=0;
    boolean autoRegion=false, gpsOutside=false;
    long zoneAt=-1, lastZoneSample=-1;
    private static final int AUTO=0, FORCE_ON=1, FORCE_OFF=2;
    boolean freshZone(){return zone>=0&&zoneAt>=0&&SystemClock.elapsedRealtime()-zoneAt<=4000;}
    String modeLabel(){return surchargeMode==AUTO?"자동":surchargeMode==FORCE_ON?"수동 ON":"수동 OFF";}
    String zoneLabel(){return zone<0?"GPS 지역 미확인":TaxiZones.name(zone)+(freshZone()?"":" (GPS 끊김)");}
    void setSurchargeMode(int mode){surchargeMode=mode;updateOutside();saveSettings();refreshIdleFare();}
    void toggleSurcharge(){setSurchargeMode(outside?FORCE_OFF:FORCE_ON);}
    void updateOutside(){outside=surchargeMode==FORCE_ON||(surchargeMode==AUTO&&gpsOutside);}
    void refreshIdleFare(){if(!running&&!ended){TaxiTariff t=TaxiTariff.of(REGIONS[region]);fare.start(t,t.surcharge(Calendar.getInstance().get(Calendar.HOUR_OF_DAY),outside));}}
    void setAutoRegion(boolean enabled){if(running)return;autoRegion=enabled;if(enabled&&freshZone())selectGpsRegion();saveSettings();refreshIdleFare();}
    private void selectGpsRegion(){if(!autoRegion||running||zone<0)return;String name=TaxiZones.tariff(zone);for(int i=0;i<REGIONS.length;i++)if(REGIONS[i].equals(name)){if(region!=i){region=i;saveSettings();refreshIdleFare();}break;}}
    private void place(Location loc){long now=SystemClock.elapsedRealtime();if(lastZoneSample>=0&&now-lastZoneSample<900)return;lastZoneSample=now;
        if(loc.hasAccuracy()&&loc.getAccuracy()>150)return;
        int z=TaxiZones.at(loc.getLatitude(),loc.getLongitude());if(z<0){pendingZone=-1;zoneCount=0;return;}
        if(z==zone){zoneAt=now;pendingZone=-1;zoneCount=0;if(running&&startZone<0){startZone=z;gpsOutside=false;updateOutside();}else if(!running)selectGpsRegion();return;}
        if(z!=pendingZone){pendingZone=z;zoneCount=1;}else zoneCount++;
        if(zoneCount<3)return;zone=z;zoneAt=now;pendingZone=-1;zoneCount=0;
        if(running){if(startZone<0)startZone=zone;gpsOutside=zone!=startZone;updateOutside();}else selectGpsRegion();
    }
    boolean running,ended,waitStop,outside,auto=true,hasReceipt;
    double km,receiptKm; float secs,slowSecs,receiptSecs,receiptSlow;
    float maxSpeed=Float.NaN,receiptMax=Float.NaN,receiptEndSpeed=Float.NaN;
    int speedSources,receiptSources;
    String receiptEndSource="미확인";
    float averageSpeed(){return secs>0?(float)(km*3600/secs):Float.NaN;}
    float receiptAverage(){return receiptSecs>0?(float)(receiptKm*3600/receiptSecs):Float.NaN;}
    static String sourceList(int mask){StringBuilder b=new StringBuilder();for(int i=0;i<3;i++)if((mask&(1<<i))!=0){if(b.length()>0)b.append(" / ");b.append(new String[]{"GPS","BCM","외부"}[i]);}return b.length()==0?"미확인":b.toString();}
    static int sourceBit(String source){return "GPS".equals(source)?1:"5W BCM".equals(source)?2:"외부 차속".equals(source)?4:0;}
    SharedPreferences prefs;
    TripStore trips;
    int toll,receiptToll,receiptFuel,theme,brightness=100,price=1750;
    float efficiency=7f,accuracy=Float.NaN;
    boolean nightDim=true,boot,reserved,called;
    long started,checkpoint;
    int fuel(double distance){return (int)Math.round(distance/Math.max(1,efficiency)*price);}
    void saveOptions(){prefs.edit().putInt("theme",theme).putInt("brightness",brightness).putInt("price",price).putFloat("efficiency",efficiency).putBoolean("nightDim",nightDim).putBoolean("boot",boot).apply();}
    void saveActive(){if(!running)return;prefs.edit().putBoolean("active",true).putLong("active_start",started).putString("active_region",fare.tariff().region).putFloat("active_m",fare.savedMeters()).putFloat("active_w",fare.savedWon()).putInt("active_du",fare.distUnits).putInt("active_tu",fare.timeUnits).putInt("active_dw",fare.distWon).putInt("active_tw",fare.timeWon).putFloat("active_km",(float)km).putFloat("active_sec",secs).putFloat("active_slow",slowSecs).putInt("active_zone",startZone).putBoolean("active_out",gpsOutside).putInt("active_toll",toll).putFloat("active_max_speed",maxSpeed).putInt("active_speed_sources",speedSources).apply();}
    void restoreActive(){if(!prefs.getBoolean("active",false))return;fare.restore(TaxiTariff.of(prefs.getString("active_region","서울")),prefs.getFloat("active_m",0),prefs.getInt("active_du",0),prefs.getInt("active_tu",0),prefs.getInt("active_dw",0),prefs.getInt("active_tw",0),prefs.getFloat("active_w",0));started=prefs.getLong("active_start",System.currentTimeMillis());km=prefs.getFloat("active_km",0);secs=prefs.getFloat("active_sec",0);slowSecs=prefs.getFloat("active_slow",0);startZone=prefs.getInt("active_zone",-1);gpsOutside=prefs.getBoolean("active_out",false);toll=prefs.getInt("active_toll",0);maxSpeed=prefs.getFloat("active_max_speed",Float.NaN);speedSources=prefs.getInt("active_speed_sources",0);running=true;ended=false;waitStop=false;updateOutside();}
    String diagnostics(){long n=SystemClock.elapsedRealtime();return "차속 소스: "+speed.source(n)+"\n5W BCM: "+speed.bcmReading(n)+" / "+bcm.status+"\n외부 차속: "+speed.vehicleReading(n)+"\nGPS 차속: "+speed.gpsReading(n)+"\nGPS 정확도: "+(Float.isNaN(accuracy)?"미확인":accuracy+" m")+"\n행정구역: "+zoneLabel()+"\n할증: "+modeLabel()+" / "+Math.round(surcharge()*100)+"%\n서비스 실행 중";}

    private LocationManager gps;
    private Location lastPosition;
    private final Handler handler=new Handler();
    private long lastTick;
    private final IBinder binder=new LocalBinder();
    public final class LocalBinder extends Binder { MeterService service(){return MeterService.this;} }
    @Override public IBinder onBind(Intent i){return binder;}
    private final BroadcastReceiver bridge=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){
        if(!MainActivity.SPEED_ACTION.equals(i.getAction())||i.getExtras()==null)return;
        Object raw=i.getExtras().get(MainActivity.SPEED_EXTRA);
        if(raw instanceof Number)speed.vehicle(((Number)raw).floatValue(),SystemClock.elapsedRealtime());
    }};
    @Override public void onCreate(){super.onCreate();
        prefs=getSharedPreferences("meter",MODE_PRIVATE);trips=new TripStore(this);theme=prefs.getInt("theme",0);brightness=prefs.getInt("brightness",100);price=prefs.getInt("price",1750);efficiency=prefs.getFloat("efficiency",7f);nightDim=prefs.getBoolean("nightDim",true);boot=prefs.getBoolean("boot",false);receiptToll=prefs.getInt("receipt_toll",0);receiptFuel=prefs.getInt("receipt_fuel",0);
        region=Math.max(0,Math.min(REGIONS.length-1,prefs.getInt("region",0)));
        surchargeMode=prefs.getInt("surchargeMode",prefs.getBoolean("outside",false)?FORCE_ON:AUTO);if(surchargeMode<0||surchargeMode>2)surchargeMode=AUTO;autoRegion=prefs.getBoolean("autoRegion",false);auto=prefs.getBoolean("auto",true);updateOutside();
        TaxiTariff t=TaxiTariff.of(REGIONS[region]);fare.start(t,t.surcharge(Calendar.getInstance().get(Calendar.HOUR_OF_DAY),outside));
        if(prefs.getBoolean("receipt",false)){
            receiptFare.restore(TaxiTariff.of(prefs.getString("receipt_region","서울")),prefs.getFloat("receipt_meters",0),prefs.getInt("receipt_du",0),prefs.getInt("receipt_tu",0),prefs.getInt("receipt_dw",0),prefs.getInt("receipt_tw",0),prefs.getFloat("receipt_won",0));
            receiptKm=prefs.getFloat("receipt_km",0);receiptSecs=prefs.getFloat("receipt_secs",0);receiptSlow=prefs.getFloat("receipt_slow",0);receiptMax=prefs.getFloat("receipt_max_speed",Float.NaN);receiptEndSpeed=prefs.getFloat("receipt_end_speed",Float.NaN);receiptSources=prefs.getInt("receipt_speed_sources",0);receiptEndSource=prefs.getString("receipt_end_source","미확인");ended=true;hasReceipt=true;waitStop=true;
            if(receiptFare.tariff()==t){fare.copyFrom(receiptFare);km=receiptKm;secs=receiptSecs;slowSecs=receiptSlow;maxSpeed=receiptMax;speedSources=receiptSources;}
        }
        restoreActive();
        gps=(LocationManager)getSystemService(LOCATION_SERVICE);
        IntentFilter filter=new IntentFilter(MainActivity.SPEED_ACTION);
        if(Build.VERSION.SDK_INT>=33)registerReceiver(bridge,filter,Context.RECEIVER_EXPORTED);else registerReceiver(bridge,filter);
        lastTick=SystemClock.elapsedRealtime();handler.post(tick);startBcm();
    }
    @Override public int onStartCommand(Intent i,int flags,int startId){
        if(i!=null&&"stop".equals(i.getAction())){if(running)endTrip();stopForeground(true);stopSelf();return START_NOT_STICKY;}
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(new NotificationChannel("meter","택시 미터기",NotificationManager.IMPORTANCE_LOW));
        int pf=PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0);
        PendingIntent pi=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),pf);
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,MeterService.class).setAction("stop"),pf);
        Notification.Builder nb=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,"meter"):new Notification.Builder(this);
        nb.setSmallIcon(android.R.drawable.ic_menu_compass).setContentTitle("5W 택시 미터기").setContentText("차속 수신 및 운행 적산").setContentIntent(pi).setOngoing(true).addAction(android.R.drawable.ic_menu_close_clear_cancel,"운행 종료 및 서비스 중지",stop);
        startForeground(41,nb.build());requestGps();return START_NOT_STICKY;
    }
    void requestGps(){try{if(gps!=null){gps.removeUpdates(this);lastPosition=null;gps.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000,0,this);}}catch(SecurityException|IllegalArgumentException ignored){}}
    private final Runnable tick=new Runnable(){public void run(){
        long now=SystemClock.elapsedRealtime();float v=speed.speed(now);
        float dt=Math.max(0,Math.min((now-lastTick)/1000f,1.5f));lastTick=now;
        if(waitStop&&!Float.isNaN(v)&&v<=3)waitStop=false;
        if(auto&&!running&&!waitStop&&!Float.isNaN(v)&&v>3){startTrip();dt=0;}
        if(running&&!Float.isNaN(v)){maxSpeed=Float.isNaN(maxSpeed)?v:Math.max(maxSpeed,v);speedSources|=sourceBit(speed.source(now));}
        if(running&&dt>0&&!Float.isNaN(v)){fare.add(v,dt,surcharge());km+=v*dt/3600.0;secs+=dt;if(v<=15)slowSecs+=dt;}
        if(running&&now-checkpoint>=2000){checkpoint=now;saveActive();}
        handler.postDelayed(this,100);
    }};
    float surcharge(){return fare.tariff().surcharge(Calendar.getInstance().get(Calendar.HOUR_OF_DAY),outside);}
    void saveSettings(){prefs.edit().putInt("region",region).putBoolean("outside",outside).putInt("surchargeMode",surchargeMode).putBoolean("autoRegion",autoRegion).putBoolean("auto",auto).apply();}
    void setRegion(int r){if(running)return;autoRegion=false;region=r;saveSettings();TaxiTariff t=TaxiTariff.of(REGIONS[region]);fare.start(t,t.surcharge(Calendar.getInstance().get(Calendar.HOUR_OF_DAY),outside));km=0;secs=slowSecs=0;maxSpeed=Float.NaN;speedSources=0;}
    void startTrip(){started=System.currentTimeMillis();toll=0;reserved=false;called=false;if(autoRegion&&freshZone())selectGpsRegion();startZone=freshZone()?zone:-1;gpsOutside=false;updateOutside();TaxiTariff t=TaxiTariff.of(REGIONS[region]);fare.start(t,t.surcharge(Calendar.getInstance().get(Calendar.HOUR_OF_DAY),outside));km=0;secs=slowSecs=0;maxSpeed=Float.NaN;speedSources=0;running=true;ended=false;waitStop=false;lastTick=SystemClock.elapsedRealtime();saveActive();}
    void endTrip(){if(!running)return;long now=SystemClock.elapsedRealtime();receiptEndSpeed=speed.speed(now);receiptEndSource=speed.source(now);if(!Float.isNaN(receiptEndSpeed)){maxSpeed=Float.isNaN(maxSpeed)?receiptEndSpeed:Math.max(maxSpeed,receiptEndSpeed);speedSources|=sourceBit(receiptEndSource);}receiptMax=maxSpeed;receiptSources=speedSources;receiptToll=toll;receiptFuel=fuel(km);trips.add(started,System.currentTimeMillis(),fare.tariff().region,km,secs,fare.total(),toll,receiptFuel,averageSpeed(),receiptMax,receiptEndSpeed,receiptEndSource,sourceList(receiptSources));running=false;ended=true;gpsOutside=false;updateOutside();waitStop=true;hasReceipt=true;receiptFare.copyFrom(fare);receiptKm=km;receiptSecs=secs;receiptSlow=slowSecs;prefs.edit().putBoolean("active",false).putFloat("receipt_max_speed",receiptMax).putFloat("receipt_end_speed",receiptEndSpeed).putInt("receipt_speed_sources",receiptSources).putString("receipt_end_source",receiptEndSource).putInt("receipt_toll",receiptToll).putInt("receipt_fuel",receiptFuel).putBoolean("receipt",true).putString("receipt_region",fare.tariff().region).putFloat("receipt_meters",fare.savedMeters()).putInt("receipt_du",fare.distUnits).putInt("receipt_tu",fare.timeUnits).putInt("receipt_dw",fare.distWon).putInt("receipt_tw",fare.timeWon).putFloat("receipt_won",fare.savedWon()).putFloat("receipt_km",(float)km).putFloat("receipt_secs",secs).putFloat("receipt_slow",slowSecs).apply();}
    @Override public void onLocationChanged(Location loc){if(loc==null)return;long now=SystemClock.elapsedRealtime();long fix=loc.getElapsedRealtimeNanos()/1000000L;if(fix<=0||fix>now||now-fix>SpeedArbiter.GPS_TIMEOUT_MS)return;accuracy=loc.hasAccuracy()?loc.getAccuracy():Float.NaN;place(loc);if(loc.hasAccuracy()&&loc.getAccuracy()>50){speed.clearGps();lastPosition=null;return;}float k=Float.NaN;
        if(loc.hasSpeed())k=Math.max(0,loc.getSpeed()*3.6f);
        else if(lastPosition!=null&&loc.getTime()>lastPosition.getTime()){long ms=loc.getTime()-lastPosition.getTime();if(ms>=500&&ms<=5000)k=lastPosition.distanceTo(loc)*3600f/ms;}
        if(!Float.isNaN(k))speed.gps(k,fix);lastPosition=loc;
    }
    @Override public void onProviderDisabled(String n){speed.clearGps();zoneAt=-1;pendingZone=-1;zoneCount=0;}
    @Override public void onProviderEnabled(String n){}
    @Override public void onStatusChanged(String n,int s,Bundle b){}
    @Override public void onDestroy(){destroyed=true;bcmWorker.shutdownNow();saveActive();if(trips!=null)trips.close();handler.removeCallbacks(tick);if(gps!=null)gps.removeUpdates(this);unregisterReceiver(bridge);super.onDestroy();}
}
