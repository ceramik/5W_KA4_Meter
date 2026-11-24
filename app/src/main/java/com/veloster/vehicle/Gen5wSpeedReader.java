package com.veloster.vehicle;

import android.os.IBinder;
import android.os.SystemClock;
import java.lang.reflect.Method;

/** Optional read-only Gen5W BCM adapter. No CAN writes, root commands or permission bypass. */
final class Gen5wSpeedReader {
    static final String SERVICE="com.hkmc.micom.bcm";
    static final String STUB="com.hkmc.bcm.IBCMService$Stub";
    private Object proxy;
    private Method getter;
    private IBinder binder;
    private long nextConnect;
    volatile String status="확인 중";
    float read(){
        long now=SystemClock.elapsedRealtime();
        try{
            if(binder!=null&&!binder.isBinderAlive()){proxy=null;getter=null;binder=null;}
            if(proxy==null){
                if(now<nextConnect)return Float.NaN;
                nextConnect=now+5000;
                Class<?> sm=Class.forName("android.os.ServiceManager");
                IBinder b=(IBinder)sm.getMethod("getService",String.class).invoke(null,SERVICE);
                if(b==null){status="BCM 서비스 없음 또는 접근 제한";return Float.NaN;}
                Class<?> stub=Class.forName(STUB);
                Object p=stub.getMethod("asInterface",IBinder.class).invoke(null,b);
                if(p==null){status="BCM 인터페이스 없음";return Float.NaN;}
                Method method=Class.forName("com.hkmc.bcm.IBCMService").getMethod("getSpeed_KMHUnit");
                binder=b;proxy=p;getter=method;
            }
            Object value=getter.invoke(proxy);
            if(value instanceof Number){
                float v=((Number)value).floatValue();
                if(!Float.isNaN(v)&&!Float.isInfinite(v)&&v>=0&&v<=260){status="BCM 수신 정상";return v;}
            }
            status="BCM 차속 값 무효";
        }catch(Exception e){
            Throwable t=e instanceof java.lang.reflect.InvocationTargetException&&e.getCause()!=null?e.getCause():e;
            status="BCM 미지원/권한 제한: "+t.getClass().getSimpleName();
            proxy=null;getter=null;binder=null;nextConnect=now+5000;
        }catch(LinkageError e){status="BCM 클래스 미지원";proxy=null;getter=null;binder=null;nextConnect=now+5000;}
        return Float.NaN;
    }
}
