package com.veloster.vehicle;
import android.app.*;
import android.content.*;
import android.location.*;
import android.os.*;
import java.util.Calendar;

public final class MeterService extends Service implements LocationListener {
    static final String[] REGIONS={"서울","경기","인천","부산","대구","광주","대전","울산","세종","강원","충북","충남","전북","전남","경북","경남","제주"};
    final SpeedArbiter speed=new SpeedArbiter();
    final TaxiFare fare=new TaxiFare(), receiptFare=new TaxiFare();
    int region;
    boolean running,ended,waitStop,outside,auto=true,hasReceipt;
    double km,receiptKm; float secs,slowSecs,receiptSecs,receiptSlow;
    private SharedPreferences prefs;
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
        prefs=getSharedPreferences("meter",MODE_PRIVATE);
        region=Math.max(0,Math.min(REGIONS.length-1,prefs.getInt("region",0)));
        outside=prefs.getBoolean("outside",false);auto=prefs.getBoolean("auto",true);
        TaxiTariff t=TaxiTariff.of(REGIONS[region]);fare.start(t,t.surcharge(Calendar.getInstance().get(Calendar.HOUR_OF_DAY),outside));
        if(prefs.getBoolean("receipt",false)){
            receiptFare.restore(TaxiTariff.of(prefs.getString("receipt_region","서울")),prefs.getFloat("receipt_meters",0),prefs.getInt("receipt_du",0),prefs.getInt("receipt_tu",0),prefs.getInt("receipt_dw",0),prefs.getInt("receipt_tw",0),prefs.getFloat("receipt_won",0));
            receiptKm=prefs.getFloat("receipt_km",0);receiptSecs=prefs.getFloat("receipt_secs",0);receiptSlow=prefs.getFloat("receipt_slow",0);ended=true;hasReceipt=true;waitStop=true;
            if(receiptFare.tariff()==t){fare.copyFrom(receiptFare);km=receiptKm;secs=receiptSecs;slowSecs=receiptSlow;}
        }
        gps=(LocationManager)getSystemService(LOCATION_SERVICE);
        IntentFilter filter=new IntentFilter(MainActivity.SPEED_ACTION);
        if(Build.VERSION.SDK_INT>=33)registerReceiver(bridge,filter,Context.RECEIVER_EXPORTED);else registerReceiver(bridge,filter);
        lastTick=SystemClock.elapsedRealtime();handler.post(tick);
    }
    @Override public int onStartCommand(Intent i,int flags,int startId){
        if(i!=null&&"stop".equals(i.getAction())){if(running)endTrip();stopForeground(true);stopSelf();return START_NOT_STICKY;}
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(new NotificationChannel("meter","택시 미터기",NotificationManager.IMPORTANCE_LOW));
        int pf=PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0);
        PendingIntent pi=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),pf);
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,MeterService.class).setAction("stop"),pf);
        Notification.Builder nb=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,"meter"):new Notification.Builder(this);
        nb.setSmallIcon(android.R.drawable.ic_menu_compass).setContentTitle("KA4 택시 미터기").setContentText("차속 수신 및 운행 적산").setContentIntent(pi).setOngoing(true).addAction(android.R.drawable.ic_menu_close_clear_cancel,"운행 종료 및 서비스 중지",stop);
        startForeground(41,nb.build());requestGps();return START_NOT_STICKY;
    }
    void requestGps(){try{if(gps!=null){gps.removeUpdates(this);lastPosition=null;gps.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000,0,this);}}catch(SecurityException|IllegalArgumentException ignored){}}
    private final Runnable tick=new Runnable(){public void run(){
        long now=SystemClock.elapsedRealtime();float v=speed.speed(now);
        float dt=Math.max(0,Math.min((now-lastTick)/1000f,1.5f));lastTick=now;
        if(waitStop&&!Float.isNaN(v)&&v<=3)waitStop=false;
        if(auto&&!running&&!waitStop&&!Float.isNaN(v)&&v>3){startTrip();dt=0;}
        if(running&&dt>0&&!Float.isNaN(v)){fare.add(v,dt,surcharge());km+=v*dt/3600.0;secs+=dt;if(v<=15)slowSecs+=dt;}
        handler.postDelayed(this,100);
    }};
    float surcharge(){return fare.tariff().surcharge(Calendar.getInstance().get(Calendar.HOUR_OF_DAY),outside);}
    void saveSettings(){prefs.edit().putInt("region",region).putBoolean("outside",outside).putBoolean("auto",auto).apply();}
    void setRegion(int r){if(running)return;region=r;saveSettings();TaxiTariff t=TaxiTariff.of(REGIONS[region]);fare.start(t,t.surcharge(Calendar.getInstance().get(Calendar.HOUR_OF_DAY),outside));km=0;secs=slowSecs=0;}
    void startTrip(){TaxiTariff t=TaxiTariff.of(REGIONS[region]);fare.start(t,t.surcharge(Calendar.getInstance().get(Calendar.HOUR_OF_DAY),outside));km=0;secs=slowSecs=0;running=true;ended=false;waitStop=false;lastTick=SystemClock.elapsedRealtime();}
    void endTrip(){running=false;ended=true;waitStop=true;hasReceipt=true;receiptFare.copyFrom(fare);receiptKm=km;receiptSecs=secs;receiptSlow=slowSecs;prefs.edit().putBoolean("receipt",true).putString("receipt_region",fare.tariff().region).putFloat("receipt_meters",fare.savedMeters()).putInt("receipt_du",fare.distUnits).putInt("receipt_tu",fare.timeUnits).putInt("receipt_dw",fare.distWon).putInt("receipt_tw",fare.timeWon).putFloat("receipt_won",fare.savedWon()).putFloat("receipt_km",(float)km).putFloat("receipt_secs",secs).putFloat("receipt_slow",slowSecs).apply();}
    @Override public void onLocationChanged(Location loc){if(loc==null)return;float k=Float.NaN;
        if(loc.hasSpeed())k=Math.max(0,loc.getSpeed()*3.6f);
        else if(lastPosition!=null&&loc.getTime()>lastPosition.getTime()){long ms=loc.getTime()-lastPosition.getTime();if(ms>=500&&ms<=5000)k=lastPosition.distanceTo(loc)*3600f/ms;}
        if(!Float.isNaN(k))speed.gps(k,SystemClock.elapsedRealtime());lastPosition=loc;
    }
    @Override public void onProviderDisabled(String n){speed.clearGps();}
    @Override public void onProviderEnabled(String n){}
    @Override public void onStatusChanged(String n,int s,Bundle b){}
    @Override public void onDestroy(){handler.removeCallbacks(tick);if(gps!=null)gps.removeUpdates(this);unregisterReceiver(bridge);super.onDestroy();}
}
