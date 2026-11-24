package com.veloster.vehicle;
import android.content.*;
import android.os.Build;
public final class BootReceiver extends BroadcastReceiver {
 public void onReceive(Context c,Intent i){if(!Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())||!c.getSharedPreferences("meter",0).getBoolean("boot",false))return;Intent s=new Intent(c,MeterService.class);try{if(Build.VERSION.SDK_INT>=26)c.startForegroundService(s);else c.startService(s);}catch(RuntimeException ignored){}}
}
