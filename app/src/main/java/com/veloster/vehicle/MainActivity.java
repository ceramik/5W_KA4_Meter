package com.veloster.vehicle;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import java.util.Calendar;
import java.util.Locale;

/** Standalone port of the original TaxiCluster presentation; non-privileged speed bridge is read-only. */
public final class MainActivity extends Activity {
    public static final String SPEED_ACTION="me.bamtol.ka4taximeter.VEHICLE_SPEED";
    public static final String SPEED_EXTRA="speed_kmh";
    private static final String[] REGIONS=MeterService.REGIONS;
    private MeterService meter;
    private boolean bound,permissionAsked;
    private MeterView view;
    private final Handler handler=new Handler();
    private MusicMonitor.Track music=new MusicMonitor.Track();
    private long lastMusic=-1;
    private final Runnable updater=new Runnable(){public void run(){
        long now=SystemClock.elapsedRealtime();
        if(lastMusic<0||now-lastMusic>=1000){music=MusicMonitor.read(MainActivity.this);lastMusic=now;}
        if(view!=null)view.invalidate();handler.postDelayed(this,80);
    }};
    private final android.content.ServiceConnection connection=new android.content.ServiceConnection(){
        public void onServiceConnected(android.content.ComponentName n,android.os.IBinder b){meter=((MeterService.LocalBinder)b).service();requestGps();view.invalidate();}
        public void onServiceDisconnected(android.content.ComponentName n){meter=null;}
    };
    @Override public void onCreate(Bundle b){super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN|WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(5894|1024|512);
        view=new MeterView();setContentView(view);
        Intent i=new Intent(this,MeterService.class);
        startService(i);bound=bindService(i,connection,Context.BIND_AUTO_CREATE);
    }
    @Override protected void onResume(){super.onResume();requestGps();handler.removeCallbacks(updater);handler.post(updater);}
    @Override protected void onPause(){handler.removeCallbacks(updater);super.onPause();}
    @Override protected void onDestroy(){if(bound)unbindService(connection);super.onDestroy();}
    private void requestGps(){
        if(Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){if(!permissionAsked){permissionAsked=true;requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},11);}return;}
        if(meter!=null)meter.requestGps();
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){super.onRequestPermissionsResult(request,permissions,grants);if(request==11&&grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED&&meter!=null)meter.requestGps();}
    private void settings(){if(meter==null)return;
        String[] options={"지역 요금 선택 ("+REGIONS[meter.region]+")","시계외 할증: "+(meter.outside?"ON":"OFF"),"자동 운행 시작: "+(meter.auto?"ON":"OFF"),"이전 영수증 보기"};
        new android.app.AlertDialog.Builder(this).setTitle("미터기 설정").setItems(options,(d,n)->{
            if(meter==null)return;
            if(n==0){if(meter.running){android.widget.Toast.makeText(this,"운행 종료 후 변경하세요",0).show();return;}
                new android.app.AlertDialog.Builder(this).setTitle("지역 요금 선택").setSingleChoiceItems(REGIONS,meter.region,(dialog,r)->{if(meter!=null)meter.setRegion(r);dialog.dismiss();view.invalidate();}).setNegativeButton("취소",null).show();
            }else if(n==1){meter.outside=!meter.outside;meter.saveSettings();}
            else if(n==2){meter.auto=!meter.auto;meter.saveSettings();}
            else if(meter.hasReceipt){meter.ended=true;}else android.widget.Toast.makeText(this,"저장된 영수증 없음",0).show();
            view.invalidate();
        }).setNegativeButton("닫기",null).show();
    }

    private final class MeterView extends View {
        private final Paint p=new Paint(3);
        private final RectF r=new RectF();
        private final int LED=0xffff6544,LED_DIM=0xff38221c,INACTIVE=0xff303034,GREEN=0xff2fe06a;
        private long lastAnim=-1;private float phase=0;
        MeterView(){super(MainActivity.this);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        private void fill(Canvas c,float a,float b,float x,float y,int color){p.setColor(color);p.setStyle(Paint.Style.FILL);c.drawRect(a,b,x,y,p);}
        private void round(Canvas c,float a,float b,float x,float y,float radius,int color){p.setColor(color);p.setStyle(Paint.Style.FILL);r.set(a,b,x,y);c.drawRoundRect(r,radius,radius,p);}
        private void outline(Canvas c,float a,float b,float x,float y,float radius,int color){p.setColor(color);p.setStrokeWidth(1.5f);p.setStyle(Paint.Style.STROKE);r.set(a,b,x,y);c.drawRoundRect(r,radius,radius,p);p.setStyle(Paint.Style.FILL);}
        private void text(Canvas c,String s,float x,float y,float size,int color,Paint.Align align,boolean bold){if(s==null)s="";p.setStyle(Paint.Style.FILL);p.setTypeface(bold?Typeface.DEFAULT_BOLD:Typeface.DEFAULT);p.setTextSize(size);p.setTextAlign(align);p.setColor(color);c.drawText(s,x,y,p);}
        private void mono(Canvas c,String s,float x,float y,float size,int color,Paint.Align align){p.setTypeface(Typeface.MONOSPACE);p.setTextAlign(align);p.setTextSize(size);p.setColor(color);p.setStyle(Paint.Style.FILL);c.drawText(s,x,y,p);}
        private String time(float s){int v=(int)Math.max(0,s);return String.format(Locale.US,"%02d:%02d",v/60,v%60);}
        private String clock(long ms){long s=Math.max(0,ms)/1000;return String.format(Locale.US,"%d:%02d",s/60,s%60);}
        @Override public void onDraw(Canvas c){super.onDraw(c);
            if(meter==null){c.drawColor(0xff14110e);return;} int save=c.save();c.scale(getWidth()/800f,getHeight()/480f);c.drawColor(0xff14110e);
            float v=meter.speed.speed(SystemClock.elapsedRealtime());boolean moving=!Float.isNaN(v)&&v>0.5f;
            if(lastAnim>0&&meter.running&&moving)phase+=Math.min(500,SystemClock.elapsedRealtime()-lastAnim)/Math.max(40f,100-v);
            lastAnim=SystemClock.elapsedRealtime();int horseFrame=meter.running&&moving?(int)phase%HorseFrames.FRAMES.length:3;
            round(c,14,60,522,470,14,0xff1d1d20);round(c,22,68,514,462,10,0xff0c0c0d);
            text(c,"KOREA DIGITAL TAXIMETER",34,88,12,0xff5d5d63,Paint.Align.LEFT,true);
            text(c,REGIONS[meter.region]+" 요금 · "+meter.speed.source(SystemClock.elapsedRealtime())+(meter.outside?" · 시계외":""),502,88,12,0xffaaaaaa,Paint.Align.RIGHT,true);
            String[] labs={"빈차","주행","할증","예약","호출"};boolean[] lamps={!meter.running,meter.running,meter.surcharge()>0,false,false};int[] colors={0xffff3b2f,GREEN,0xffffb020,0xff3fa2ff,0xffff3b2f};
            for(int i=0;i<5;i++){float x=34+i*94;round(c,x,98,x+84,128,5,lamps[i]?colors[i]:0xff1a1a1c);text(c,labs[i],x+42,119,16,lamps[i]?0xff111111:0xff4a4a50,Paint.Align.CENTER,true);}
            round(c,34,140,502,290,8,0xff220e0a);outline(c,34,140,502,290,8,0xff603529);
            text(c,moving?"요금 · 주행":"요금 · 정차",46,162,13,0xffbd6c50,Paint.Align.LEFT,true);
            fill(c,44,174,154,240,0xff29110e);
            HorseFrames.draw(c,p,46,176,108,63,horseFrame,LED);
            String shown=String.valueOf(meter.fare.total());while(shown.length()<5)shown=" "+shown;
            seg7(c,shown,452,160,90,LED,LED_DIM);text(c,"원",494,252,22,LED,Paint.Align.RIGHT,true);
            String hint=!meter.running?(!meter.auto?"자동 시작 OFF · 버튼으로 시작":meter.waitStop?"자동 재시작은 정차 후 · 수동 시작 가능":"움직이면 자동 시작 · 버튼으로 바로 시작"):Float.isNaN(v)?"속도 미수신 · 요금 계산 일시정지":v<=15?String.format(Locale.US,"시간 요금 · %d초당 100원",meter.fare.tariff().stepS):String.format(Locale.US,"다음 100원까지 %.0f m",Math.ceil(meter.fare.toNext()));
            text(c,hint,46,280,12,0xffc67f5c,Paint.Align.LEFT,false);
            fill(c,290,272,490,280,LED_DIM);fill(c,290,272,290+200*meter.fare.progress(),280,LED);
            String[] label={"주행 거리 km","주행 시간","저속 시간"};String[] value={String.format(Locale.US,"%.2f",meter.km),time(meter.secs),time(meter.slowSecs)};
            for(int i=0;i<3;i++){float x=34+i*158;round(c,x,302,x+148,374,6,0xff210e0a);text(c,label[i],x+10,320,12,0xffbd6c50,Paint.Align.LEFT,false);seg7(c,value[i],x+138,330,34,LED,LED_DIM);}
            round(c,34,386,268,452,8,meter.running?0xff902f25:0xff206d3c);
            text(c,meter.running?"운행 종료 · 버튼":"운행 시작",151,426,25,0xffffffff,Paint.Align.CENTER,true);
            TaxiTariff tariff=meter.fare.tariff();text(c,String.format(Locale.US,"기본 %,d원 (%.1f km)",tariff.base,tariff.baseM/1000f),282,414,13,0xff8a8a90,Paint.Align.LEFT,false);
            text(c,"심야 "+tariff.nightHours()+"시 최대 "+tariff.nightMax()+"% · 시계외 "+tariff.out+"%",282,438,12,0xff8a8a90,Paint.Align.LEFT,false);
            if(meter.ended)receipt(c,v);else radio(c);
            // Idle settings: tapping region name cycles area; long tapping region toggles city-outside flag.
            c.restoreToCount(save);
        }
        private void seg7(Canvas c,String value,float right,float top,float h,int on,int off){
            float w=h*0.57f,gap=h*.12f;float left=right-(value.length()*(w+gap));
            for(int i=0;i<value.length();i++){
                char z=value.charAt(i);float x=left+i*(w+gap);
                if(z=='.'){round(c,x,top+h*.86f,x+h*.08f,top+h*.94f,1,on);continue;}
                if(z==':'){round(c,x+w*.2f,top+h*.32f,x+w*.31f,top+h*.41f,1,on);round(c,x+w*.2f,top+h*.65f,x+w*.31f,top+h*.74f,1,on);continue;}
                boolean[] m=segments(z);float t=h*.08f,bar=h*.10f;float mid=top+h*.50f;
                // Seven segments, all dim-unlit like the original LED display
                round(c,x+t,top,x+w-t,top+bar,2,m[0]?on:off);
                round(c,x+w-bar,top+bar,x+w,mid-bar*.3f,2,m[1]?on:off);
                round(c,x+w-bar,mid+bar*.3f,x+w,top+h-bar,2,m[2]?on:off);
                round(c,x+t,top+h-bar,x+w-t,top+h,2,m[3]?on:off);
                round(c,x,mid+bar*.3f,x+bar,top+h-bar,2,m[4]?on:off);
                round(c,x,top+bar,x+bar,mid-bar*.3f,2,m[5]?on:off);
                round(c,x+t,mid-bar/2,x+w-t,mid+bar/2,2,m[6]?on:off);
            }
        }
        private boolean[] segments(char c){int a=0;switch(c){case '0':a=0x3f;break;case '1':a=0x06;break;case '2':a=0x5b;break;case '3':a=0x4f;break;case '4':a=0x66;break;case '5':a=0x6d;break;case '6':a=0x7d;break;case '7':a=0x07;break;case '8':a=0x7f;break;case '9':a=0x6f;break;case '-':a=0x40;break;default:a=0;}
            return new boolean[]{(a&1)!=0,(a&2)!=0,(a&4)!=0,(a&8)!=0,(a&16)!=0,(a&32)!=0,(a&64)!=0};}
        /** Original-style continuous scrolling for long music titles/artists. */
        private void marquee(Canvas c,String value,float x,float y,float maxWidth,float size,int color,boolean bold,float pixelsPerSecond){
            if(value==null||value.isEmpty())return;
            p.setTypeface(bold?Typeface.DEFAULT_BOLD:Typeface.DEFAULT);p.setTextSize(size);
            float width=p.measureText(value);
            if(width<=maxWidth){text(c,value,x,y,size,color,Paint.Align.LEFT,bold);return;}
            int save=c.save();c.clipRect(x,y-size-3,x+maxWidth,y+6);
            float gap=32f,cycle=width+gap;
            float shift=(SystemClock.elapsedRealtime()%((long)(cycle/pixelsPerSecond*1000f)))*pixelsPerSecond/1000f;
            text(c,value,x-shift,y,size,color,Paint.Align.LEFT,bold);
            text(c,value,x-shift+cycle,y,size,color,Paint.Align.LEFT,bold);
            c.restoreToCount(save);
        }
        private void radio(Canvas c){float l=538,t=64,rr=784,b=452;
            round(c,l,t,rr,b,12,0xff1d1d20);round(c,l+4,t+4,rr-4,b-4,9,0xff0c0c0d);
            text(c,"CAR AUDIO",l+16,t+26,12,0xff5d5d63,Paint.Align.LEFT,true);
            if(music.has())text(c,music.source,rr-16,t+26,10,LED,Paint.Align.RIGHT,true);
            round(c,l+14,t+40,rr-14,t+250,6,0xff28130d);outline(c,l+14,t+40,rr-14,t+250,6,0xff603529);
            if(!music.has()){
                text(c,"재생 중인 곡 없음",(l+rr)/2,t+140,17,0xffbd6c50,Paint.Align.CENTER,true);
                if(!MusicMonitor.hasAccess(MainActivity.this))text(c,"눌러서 음악 접근 권한 설정",(l+rr)/2,t+172,12,LED,Paint.Align.CENTER,false);
            }else{
                boolean playing=music.state==3;
                if(music.state>=0){text(c,playing?"▶":"Ⅱ",l+30,t+72,16,LED,Paint.Align.LEFT,true);text(c,playing?"재생 중":"일시정지",l+54,t+71,12,0xffbd6c50,Paint.Align.LEFT,false);}
                String title=music.title;
                marquee(c,title,l+28,t+108,rr-l-56,20,LED,true,45);
                String artist=music.artist==null?"":music.artist;
                marquee(c,artist,l+28,t+140,rr-l-56,14,0xffc68a73,false,38);
                if(music.duration>0&&music.position>=0){float f=Math.max(0,Math.min(1,music.position/(float)music.duration));fill(c,l+28,t+214,rr-28,t+218,LED_DIM);fill(c,l+28,t+214,l+28+(rr-l-56)*f,t+218,LED);
                    mono(c,clock(music.position),l+28,t+238,13,LED,Paint.Align.LEFT);mono(c,clock(music.duration),rr-28,t+238,13,0xffbd6c50,Paint.Align.RIGHT);
                }
            }
            for(float x:new float[]{l+66,rr-66}){outline(c,x-26,t+280,x+26,t+332,26,0xff3a3a40);round(c,x-20,t+286,x+20,t+326,20,0xff232326);round(c,x-3,t+288,x+3,t+294,3,0xff8a8a90);}
            text(c,"운행을 마치면 영수증이 나옵니다",(l+rr)/2,b-20,12,0xff5a5a60,Paint.Align.CENTER,false);
        }
        private void receipt(Canvas c,float v){float l=538,t=64,rr=784,b=444;fill(c,l+6,t+6,rr+4,b+6,0xff161616);fill(c,l,t,rr,b,0xfff1ede3);
            for(float x=l;x<rr;x+=12)fill(c,x,b,x+6,b+8,0xfff1ede3);
            int ink=0xff2a2724,soft=0xff7a756c;
            text(c,"영 수 증",(l+rr)/2,t+30,18,0xff1d1b18,Paint.Align.CENTER,true);
            for(float x=l+16;x<rr-16;x+=6)fill(c,x,t+44,x+3,t+45,0xffbdb7aa);
            mono(c,"거리  "+String.format(Locale.US,"%.2f km",meter.receiptKm),l+16,t+68,13,ink,Paint.Align.LEFT);
            mono(c,"시간  "+time(meter.receiptSecs),l+16,t+88,13,ink,Paint.Align.LEFT);
            for(float x=l+16;x<rr-16;x+=6)fill(c,x,t+102,x+3,t+103,0xffbdb7aa);
            TaxiTariff tf=meter.receiptFare.tariff();String[] labels={String.format(Locale.US,"기본요금 %.1f km",tf.baseM/1000f),"거리 "+tf.stepM+"m × "+meter.receiptFare.distUnits,"시간 "+tf.stepS+"초 × "+meter.receiptFare.timeUnits,"할증 (심야·시계외)"};
            int[] amounts={tf.base,meter.receiptFare.distWon,meter.receiptFare.timeWon,meter.receiptFare.surcharge()};
            for(int i=0;i<4;i++){text(c,labels[i],l+16,t+126+i*24,12,ink,Paint.Align.LEFT,false);mono(c,String.format(Locale.US,"%,d",amounts[i]),rr-16,t+126+i*24,13,ink,Paint.Align.RIGHT);}
            fill(c,l+16,t+212,rr-16,t+214,ink);text(c,"합계",l+16,t+242,16,ink,Paint.Align.LEFT,true);text(c,String.format(Locale.US,"%,d원",meter.receiptFare.total()),rr-16,t+242,20,0xffb3261e,Paint.Align.RIGHT,true);
            text(c,"운행 종료 · "+meter.receiptFare.tariff().region,l+16,t+268,12,soft,Paint.Align.LEFT,false);
            text(c,"현재 "+(Float.isNaN(v)?"—":String.format(Locale.US,"%.0f",v))+" km/h · "+meter.speed.source(SystemClock.elapsedRealtime()),l+16,t+288,12,soft,Paint.Align.LEFT,false);
            for(int i=0,x=(int)l+30;x<rr-30;i++){int bw=1+((i*7)%3);fill(c,x,t+310,x+bw,t+344,ink);x+=bw+1+((i*5)%2);}
            text(c,"누르면 음악으로",(l+rr)/2,t+370,12,soft,Paint.Align.CENTER,false);
        }
        private float downX,downY;private long downAt;
        @Override public boolean onTouchEvent(MotionEvent ev){float x=ev.getX()*800f/getWidth(),y=ev.getY()*480f/getHeight();
            if(meter==null)return true; if(ev.getAction()==MotionEvent.ACTION_DOWN){downX=x;downY=y;downAt=SystemClock.elapsedRealtime();return true;}
            if(ev.getAction()!=MotionEvent.ACTION_UP)return true;
            if(x>=34&&x<=268&&y>=386&&y<=452){if(meter.running)meter.endTrip();else meter.startTrip();}
            else if(x>=538&&x<=784&&y>=64&&y<=452){if(meter.ended)meter.ended=false;else if(!music.has()&&!MusicMonitor.hasAccess(MainActivity.this)){
                if(Build.VERSION.SDK_INT>=22)startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            }}
            else if(y>=60&&y<=95){settings();}
            invalidate();return true;
        }
    }
}
