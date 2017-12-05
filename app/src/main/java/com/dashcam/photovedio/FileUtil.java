package com.dashcam.photovedio;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaPlayer;
import android.os.Environment;
import android.os.StatFs;
import android.os.storage.StorageManager;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Switch;
import android.widget.Toast;

import com.dashcam.MainActivity;
import com.dashcam.R;
import com.dashcam.base.FileInfo;
import com.dashcam.base.RefreshEvent;

import org.greenrobot.eventbus.EventBus;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.ConnectException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import id.zelory.compressor.Compressor;

import static android.content.Context.STORAGE_SERVICE;

public class FileUtil {
  //  private static final String rootPath = Environment.getExternalStorageDirectory().getAbsolutePath();
  // private static final String rootPath= System.getenv("SECONDARY_STORAGE");;


    /**
     * 获得SD卡总大小
     *
     * @return 总大小，单位：字节B
     */
    public static long getSDTotalSize(String SDCardPath) {
        StatFs stat = new StatFs(SDCardPath);
        long blockSize = stat.getBlockSize();
        long totalBlocks = stat.getBlockCount();
        return blockSize * totalBlocks / 1024 / 1024;
    }

    /**
     * 获得sd卡剩余容量，即可用大小
     *
     * @return 剩余空间，单位：字节B
     */
    public static long getSDAvailableSize(String SDCardPath) {
        // StatFs stat = new StatFs("/storage/sdcard1");
        StatFs stat = new StatFs(SDCardPath);
        long blockSize = stat.getBlockSize();
        long availableBlocks = stat.getAvailableBlocks();
        return blockSize * availableBlocks / 1024 / 1024;
    }



    /**
     * 直接调用短信接口发短信
     *
     * @param phoneNumber
     * @param message
     */
    public static void sendSMS(String phoneNumber, String message) {
        //获取短信管理器
        android.telephony.SmsManager smsManager = android.telephony.SmsManager.getDefault();
        //拆分短信内容（手机短信长度限制）
        List<String> divideContents = smsManager.divideMessage(message);
        for (String text : divideContents) {
            smsManager.sendTextMessage(phoneNumber, null, text, null, null);
        }
    }

    public static ArrayList<String> getConnectedIP() {
        ArrayList<String> connectedIP = new ArrayList<String>();
        try {
            BufferedReader br = new BufferedReader(new FileReader(
                    "/proc/net/arp"));
            String line;
            while ((line = br.readLine()) != null) {
                String[] splitted = line.split(" +");
                if (splitted != null && splitted.length >= 4) {
                    String ip = splitted[0];
                    if (ip.length() > 8) {
                        connectedIP.add(ip);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return connectedIP;
    }


    /**
     * 判断文件是否在前后时间内
     *
     * @param
     * @return
     */
    public static boolean judgeTime3Time(String name, String time1, String time2) {

      //  SimpleDateFormat sdf1 = new SimpleDateFormat("yyyyMMdd-HH:mm:ss");
        SimpleDateFormat sdf1 = new SimpleDateFormat("yyyyMMdd_HHmmss");
        SimpleDateFormat sdf2 = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        try {
            //转化为时间
            Date date = sdf1.parse(name);
            Date date1 = sdf2.parse(time1);
            Date date2 = sdf2.parse(time2);
            //获取秒数作比较
            long l = date.getTime() / 1000;
            long l1 = date1.getTime() / 1000;
            long l2 = date2.getTime() / 1000;
            if (l >= l1 && l2 >= l) {
                return true;
            } else {
                return false;
            }
        } catch (ParseException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static String GetTimeFiles(String mVideoFilePath,String time1,String time2) {

        File file = new File(mVideoFilePath);
        if (!file.exists()) {
            return "";
        }
        //取出文件列表：
        StringBuilder builder = new StringBuilder();
        final File[] files = file.listFiles();
        for (File spec : files)
        {
            if (judgeTime3Time(spec.getName().replace(".mp4",""),time1,time2)){
                builder.append(spec.getName());
                builder.append(";");
            }
        }
        String returnfilenames = "";
        if (builder.toString().length()>0){
            returnfilenames =builder.toString();
            returnfilenames=  returnfilenames.substring(0,returnfilenames.length()-1);
        }
       return  returnfilenames;
    }

    public static String getStoragePath(Context mContext, boolean is_removale) {

        StorageManager mStorageManager = (StorageManager) mContext.getSystemService(Context.STORAGE_SERVICE);
        Class<?> storageVolumeClazz = null;
        try {
            storageVolumeClazz = Class.forName("android.os.storage.StorageVolume");
            Method getVolumeList = mStorageManager.getClass().getMethod("getVolumeList");
            Method getPath = storageVolumeClazz.getMethod("getPath");
            Method isRemovable = storageVolumeClazz.getMethod("isRemovable");
            Object result = getVolumeList.invoke(mStorageManager);
            final int length = Array.getLength(result);
            for (int i = 0; i < length; i++) {
                Object storageVolumeElement = Array.get(result, i);
                String path = (String) getPath.invoke(storageVolumeElement);
                boolean removable = (Boolean) isRemovable.invoke(storageVolumeElement);
                if (is_removale == removable) {
                    return path;
                }
            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return null;
    }
    /**
     * 获取一个最大值
     *
     * @param px
     * @param py
     * @param pz
     * @return
     */
    public static int getMaxValue(int px, int py, int pz) {
        int max = 0;
        if (px > py && px > pz) {
            max = px;
        } else if (py > px && py > pz) {
            max = py;
        } else if (pz > px && pz > py) {
            max = pz;
        }

        return max;
    }


    //获取视频存储路径
  /*  public static String getMediaOutputPath() {
        return rootPath + "/" + getTime() + ".mp4";
    }*/

    private static String getTime() {
        return new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date(System.currentTimeMillis()));
    }

    public static boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

    public  static  void MoveFiletoDangerFile(long currenttime,String rootpath){

        File fileroot = new File(rootpath+"/video");
        if (!fileroot.exists()) {
            return;
        }
        final File[] files = fileroot.listFiles();
        for (int i = 0; i < files.length; i++) {
            File file = files[i];
             long filetime = file.lastModified();
            if (Math.abs(filetime-currenttime)<3000*60){
                copyFile(file.getPath(),rootpath+"/EmergencyVideo/"+file.getName());
            }
        }
    }

    /**
     *  复制单个文件
     *  @param  oldPath  String  原文件路径  如：c:/fqf.txt
     *  @param  newPath  String  复制后路径  如：f:/fqf.txt
     *  @return  boolean
     */
    public static  void  copyFile(String  oldPath,  String  newPath)  {
        try  {
//           int  bytesum  =  0;
            int  byteread  =  0;
            File  oldfile  =  new  File(oldPath);
            if  (oldfile.exists())  {  //文件存在时
                InputStream  inStream  =  new FileInputStream(oldPath);  //读入原文件
                FileOutputStream  fs  =  new  FileOutputStream(newPath);
                byte[]  buffer  =  new  byte[1444];
//               int  length;
                while  (  (byteread  =  inStream.read(buffer))  !=  -1)  {
//                   bytesum  +=  byteread;  //字节数  文件大小
//                   System.out.println(bytesum);
                    fs.write(buffer,  0,  byteread);
                }
                inStream.close();
                fs.close();
            }
        }
        catch  (Exception  e)  {
            System.out.println("复制单个文件操作出错");
            e.printStackTrace();

        }

    }
    // 递归方式 计算文件的大小
    public static long getTotalSizeOfFilesInDir(final File file) {
        if (file.isFile())
            return file.length();
        final File[] children = file.listFiles();
        long total = 0;
        if (children != null)
            for (final File child : children)
                total += getTotalSizeOfFilesInDir(child);
        return total/1024/1024;
    }
}
