package com.veloster.vehicle;

import android.content.ComponentName;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import java.util.List;

/** Read-only Android MediaSession reader. Needs user-granted Notification Listener access. */
final class MusicMonitor {
    static final class Track {
        String title="",artist="",source="";
        long duration=-1,position=-1;
        int state=-1;
        boolean has(){return title!=null&&!title.isEmpty();}
    }
    static Track read(Context context){
        Track out=new Track();
        if(Build.VERSION.SDK_INT<21)return out;
        try{
            MediaSessionManager m=(MediaSessionManager)context.getSystemService(Context.MEDIA_SESSION_SERVICE);
            ComponentName cn=new ComponentName(context,MeterNotificationListener.class);
            List<MediaController> sessions=m.getActiveSessions(cn);
            MediaController choice=null;
            for(MediaController mc:sessions){
                if(mc.getMetadata()==null)continue;
                String t=mc.getMetadata().getString(MediaMetadata.METADATA_KEY_TITLE);
                if(TextUtils.isEmpty(t))continue;
                if(choice==null)choice=mc;
                PlaybackState ps=mc.getPlaybackState();
                if(ps!=null&&ps.getState()==PlaybackState.STATE_PLAYING){choice=mc;break;}
            }
            if(choice==null)return out;
            MediaMetadata meta=choice.getMetadata();
            out.title=meta.getString(MediaMetadata.METADATA_KEY_TITLE);
            out.artist=meta.getString(MediaMetadata.METADATA_KEY_ARTIST);
            out.source=choice.getPackageName();
            out.duration=meta.getLong(MediaMetadata.METADATA_KEY_DURATION);
            PlaybackState ps=choice.getPlaybackState();
            if(ps!=null){
                out.state=ps.getState();out.position=ps.getPosition();
                if(out.state==PlaybackState.STATE_PLAYING && out.position>=0 && ps.getLastPositionUpdateTime()>0){
                    out.position+=Math.max(0,SystemClock.elapsedRealtime()-ps.getLastPositionUpdateTime())*ps.getPlaybackSpeed();
                }
            }
        }catch(SecurityException ignored){}catch(RuntimeException ignored){}
        return out;
    }
    static boolean hasAccess(Context context){
        String value=Settings.Secure.getString(context.getContentResolver(),"enabled_notification_listeners");
        return value!=null&&value.contains(context.getPackageName());
    }
}
