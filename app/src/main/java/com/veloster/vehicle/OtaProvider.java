package com.veloster.vehicle;
import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.*;
import java.io.*;
public final class OtaProvider extends ContentProvider {
 public boolean onCreate(){return true;}
 private File file(Uri u)throws FileNotFoundException{if(!"/update.apk".equals(u.getPath()))throw new FileNotFoundException();File f=new File(new File(getContext().getCacheDir(),"ota"),"update.apk");if(!f.isFile())throw new FileNotFoundException();return f;}
 public ParcelFileDescriptor openFile(Uri u,String mode)throws FileNotFoundException{if(!"r".equals(mode))throw new FileNotFoundException();return ParcelFileDescriptor.open(file(u),ParcelFileDescriptor.MODE_READ_ONLY);}
 public String getType(Uri u){return "application/vnd.android.package-archive";}
 public Cursor query(Uri u,String[] p,String s,String[] args,String sort){try{File f=file(u);String[] cols=p==null?new String[]{"_display_name","_size"}:p;Object[] row=new Object[cols.length];for(int i=0;i<cols.length;i++){if("_display_name".equals(cols[i]))row[i]="update.apk";else if("_size".equals(cols[i]))row[i]=f.length();}MatrixCursor c=new MatrixCursor(cols);c.addRow(row);return c;}catch(FileNotFoundException e){return null;}}
 public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
 public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
 public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
}
