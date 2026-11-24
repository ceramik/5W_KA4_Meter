package com.veloster.vehicle;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.*;
import java.io.*;
import java.net.*;
import java.security.*;
import org.json.*;

final class OtaUpdater {
    private static final String API="https://api.github.com/repos/ceramik/5W_KA4_Meter/releases/latest";
    private static final String APK="KA4_NEMESIS_TaxiMeter.apk";
    private final MainActivity activity;
    private final Handler handler=new Handler();
    private boolean busy,closed;
    private long lastCheck;
    private File ready;
    private String version;
    OtaUpdater(MainActivity a){activity=a;}
    void close(){closed=true;}
    void check(boolean manual){
        if(closed||busy)return;
        if(!activity.otaSafe()){if(manual)notice("정차 및 운행 종료 후 업데이트할 수 있습니다.");return;}
        long now=SystemClock.elapsedRealtime();
        if(!manual&&lastCheck!=0&&now-lastCheck<3600000)return;
        lastCheck=now;busy=true;
        new Thread(()->{
            try{
                PackageManager pm=activity.getPackageManager();
                PackageInfo local=pm.getPackageInfo(activity.getPackageName(),PackageManager.GET_SIGNATURES);
                JSONObject release=new JSONObject(new String(fetch(API,1024*1024),"UTF-8"));
                String tag=release.getString("tag_name");
                if(!tag.matches("v[0-9]+"))throw new IOException("지원하지 않는 배포 태그");
                int code=Integer.parseInt(tag.substring(1));
                if(code<=local.versionCode){if(manual)notice("현재 최신 버전입니다.");return;}
                JSONArray assets=release.getJSONArray("assets");String url=null;String digest=null;
                for(int i=0;i<assets.length();i++){JSONObject asset=assets.getJSONObject(i);if(APK.equals(asset.optString("name"))){url=asset.getString("browser_download_url");digest=asset.optString("digest","");break;}}
                String expected="https://github.com/ceramik/5W_KA4_Meter/releases/download/"+tag+"/"+APK;
                if(!expected.equals(url))throw new IOException("APK 배포 파일 없음");
                File dir=new File(activity.getCacheDir(),"ota");if(!dir.exists()&&!dir.mkdirs())throw new IOException("저장 공간 오류");
                File tmp=new File(dir,"download.tmp"),dest=new File(dir,"update.apk");
                MessageDigest hash=MessageDigest.getInstance("SHA-256");
                HttpURLConnection c=connect(url);try(InputStream in=c.getInputStream();FileOutputStream out=new FileOutputStream(tmp)){byte[] b=new byte[32768];long total=0;int n;while((n=in.read(b))!=-1){total+=n;if(total>40L*1024*1024)throw new IOException("APK 크기 제한");out.write(b,0,n);hash.update(b,0,n);}}finally{c.disconnect();}
                if(digest.startsWith("sha256:")){StringBuilder h=new StringBuilder();for(byte b:hash.digest())h.append(String.format(java.util.Locale.US,"%02x",b&255));if(!digest.substring(7).equalsIgnoreCase(h.toString()))throw new IOException("SHA-256 불일치");}
                PackageInfo incoming=pm.getPackageArchiveInfo(tmp.getPath(),PackageManager.GET_SIGNATURES);
                if(incoming==null||!local.packageName.equals(incoming.packageName)||incoming.versionCode!=code||!same(local.signatures,incoming.signatures)){tmp.delete();throw new IOException("패키지/버전/서명 불일치: 기존 앱 유지");}
                if(dest.exists()&&!dest.delete())throw new IOException("기존 다운로드 삭제 실패");if(!tmp.renameTo(dest))throw new IOException("APK 저장 실패");
                handler.post(()->{if(!closed){ready=dest;version=incoming.versionName;offer();}});
            }catch(Exception e){if(manual)notice("업데이트 확인 실패: "+e.getMessage());else android.util.Log.w("MeterOTA","Update check failed",e);}
            finally{handler.post(()->busy=false);}
        },"meter-ota").start();
    }
    private static boolean same(Signature[] a,Signature[] b){return a!=null&&b!=null&&a.length>0&&new java.util.HashSet<Signature>(java.util.Arrays.asList(a)).equals(new java.util.HashSet<Signature>(java.util.Arrays.asList(b)));}
    private static HttpURLConnection connect(String url)throws IOException{
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(15000);c.setReadTimeout(20000);c.setRequestProperty("User-Agent","5W-TaxiMeter");c.setInstanceFollowRedirects(true);if(c.getResponseCode()!=200){int code=c.getResponseCode();c.disconnect();throw new IOException("HTTP "+code);}return c;
    }
    private static byte[] fetch(String url,int limit)throws IOException{HttpURLConnection c=connect(url);try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>limit)throw new IOException("응답 크기 초과");out.write(b,0,n);}return out.toByteArray();}finally{c.disconnect();}}
    private void notice(String text){handler.post(()->{if(!closed&&!activity.isFinishing())android.widget.Toast.makeText(activity,text,android.widget.Toast.LENGTH_LONG).show();});}
    private boolean showing;
    void offer(){if(closed||ready==null||showing||!activity.otaSafe())return;showing=true;
        AlertDialog d=new AlertDialog.Builder(activity).setTitle("새 버전 "+version).setMessage("APK 다운로드 및 서명 확인 완료. 설치할까요?").setPositiveButton("설치",(q,n)->install()).setNegativeButton("나중에",(q,n)->{ready=null;}).create();
        d.setOnDismissListener(q->showing=false);d.show();
    }
    private void install(){if(!activity.otaSafe()){notice("정차 후 다시 설치하세요.");return;}try{Intent i=new Intent(Intent.ACTION_VIEW);i.setDataAndType(Uri.parse("content://"+activity.getPackageName()+".ota/update.apk"),"application/vnd.android.package-archive");i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);activity.startActivity(i);ready=null;}catch(Exception e){notice("설치 실행 실패: "+e.getMessage());}}
}
