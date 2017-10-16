package com.dashcam.photovedio;

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
     * 得到当前的手机蜂窝网络信号强度
     * 获取LTE网络和3G/2G网络的信号强度的方式有一点不同，
     * LTE网络强度是通过解析字符串获取的，
     * 3G/2G网络信号强度是通过API接口函数完成的。
     * asu 与 dbm 之间的换算关系是 dbm=-113 + 2*asu
     */
    public static void getCurrentNetDBM(final Context context) {

        final TelephonyManager tm = (TelephonyManager) context
                .getSystemService(Context.TELEPHONY_SERVICE);
        PhoneStateListener mylistener = new PhoneStateListener() {
            @Override
            public void onSignalStrengthsChanged(SignalStrength signalStrength) {
                super.onSignalStrengthsChanged(signalStrength);
                String signalInfo = signalStrength.toString();
                String[] params = signalInfo.split(" ");
                if (tm.getNetworkType() == TelephonyManager.NETWORK_TYPE_LTE) {
                    //4G网络 最佳范围   >-90dBm 越大越好
                    int Itedbm = Integer.parseInt(params[9]);
                    Toast.makeText(context, Itedbm + "", Toast.LENGTH_LONG);
                    EventBus.getDefault().post(new RefreshEvent(3, "" + Itedbm, ""));
                    //  setDBM(Itedbm+"");

                } else if (tm.getNetworkType() == TelephonyManager.NETWORK_TYPE_HSDPA ||
                        tm.getNetworkType() == TelephonyManager.NETWORK_TYPE_HSPA ||
                        tm.getNetworkType() == TelephonyManager.NETWORK_TYPE_HSUPA ||
                        tm.getNetworkType() == TelephonyManager.NETWORK_TYPE_UMTS) {
                    //3G网络最佳范围  >-90dBm  越大越好  ps:中国移动3G获取不到  返回的无效dbm值是正数（85dbm）
                    //在这个范围的已经确定是3G，但不同运营商的3G有不同的获取方法，故在此需做判断 判断运营商与网络类型的工具类在最下方
                    String yys = tm.getSubscriberId();
                    if (yys.startsWith("46000") || yys.startsWith("46002")) {
                        yys = "中国移动";
                    } else if (yys.startsWith("46001")) {
                        yys = "中国联通";
                    } else if (yys.startsWith("46003")) {
                        yys = "中国电信";
                    }
                    if (yys == "中国移动") {
                        //   setDBM(0+"");//中国移动3G不可获取，故在此返回0
                    } else if (yys == "中国联通") {
                        int cdmaDbm = signalStrength.getCdmaDbm();
                        Toast.makeText(context, cdmaDbm + "", Toast.LENGTH_LONG);
                        //    setDBM(cdmaDbm+"");
                    } else if (yys == "中国电信") {
                        int evdoDbm = signalStrength.getEvdoDbm();
                        Toast.makeText(context, evdoDbm + "", Toast.LENGTH_LONG);
                        //   setDBM(evdoDbm+"");
                    }

                } else {
                    //2G网络最佳范围>-90dBm 越大越好
                    int asu = signalStrength.getGsmSignalStrength();
                    int dbm = -113 + 2 * asu;
                    //   EventBus.getDefault().post(new RefreshEvent(3,""+dbm,""));
                    //Toast.makeText(context,dbm+"",Toast.LENGTH_LONG);
                    // setDBM(dbm+"");
                }

            }
        };
        //开始监听
        tm.listen(mylistener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
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

        File fileroot = new File(rootpath+"/vedio");
        if (!fileroot.exists()) {
            return;
        }
        final File[] files = fileroot.listFiles();
        for (int i = 0; i < files.length; i++) {
            File file = files[i];
             long filetime = file.lastModified();
            if (Math.abs(filetime-currenttime)<3000*60){
                copyFile(file.getPath(),rootpath+"/dangervedio/"+file.getName());
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
}
