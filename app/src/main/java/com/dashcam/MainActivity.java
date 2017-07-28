package com.dashcam;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.location.Location;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.dashcam.base.ApiInterface;
import com.dashcam.base.DateUtils;
import com.dashcam.base.MacUtils;
import com.dashcam.base.RefreshEvent;
import com.dashcam.base.SPUtils;
import com.dashcam.httpservers.VideoServer;
import com.dashcam.location.GPSLocationListener;
import com.dashcam.location.GPSLocationManager;
import com.dashcam.location.GPSProviderStatus;
import com.dashcam.photovedio.CameraSurfaceView;
import com.dashcam.photovedio.CheckPermissionsUtil;
import com.dashcam.photovedio.DriveVideo;
import com.dashcam.photovedio.DriveVideoDbHelper;
import com.dashcam.photovedio.FileUtil;
import com.dashcam.udp.UDPClient;
import com.lzy.okhttputils.OkHttpUtils;
import com.lzy.okhttputils.callback.StringCallback;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import okhttp3.Call;
import okhttp3.Request;
import okhttp3.Response;

import static com.dashcam.photovedio.FileUtil.getConnectedIP;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private GPSLocationManager gpsLocationManager;
    private String IMEI = "";
    private String GPSSTR = ",,,";
    private UDPClient client = null;
    public static Context context;
    private final MyHandler myHandler = new MyHandler();
    private StringBuffer udpSendStrBuf = new StringBuffer();
    private CameraSurfaceView cameraSurfaceView;
    private WifiManager wifiManager;
    /**
     * 时间计时器
     */
    private Timer timer1 = null;//上传定位坐标定时器
    //private Timer timer2 = null;//录制视频定时器
    private Timer timer3 = null;//清除TP卡内容定时器
    /**
     * 热点名称
     */
    private static final String WIFI_HOTSPOT_SSID = "JIJJMA-";
    private DriveVideoDbHelper videoDb;
    private SmsReceiver smsReceiver;
    private AudioManager audioMgr = null; // Audio管理器，用了控制音量
    private int maxVolume = 50; // 最大音量值
    private int curVolume = 20; // 当前音量值
    private int stepVolume = 0; // 每次调整的音量幅度
    private int Carmertype = 1;//前后摄像头，1为前置摄像头，2为后置摄像头
    private boolean IsStopRecord = false;
    MediaPlayer mediaPlayer;
    private SensorManager mSensorManager;
    private Sensor mSensor;
    private static final String DEFAULT_FILE_PATH = Environment.getExternalStorageDirectory() + "/vedio/";
    private VideoServer mVideoServer;
    private MyBroadcastReceiver myBroadcastReceiver = new MyBroadcastReceiver();
    private boolean IsCharge = false;//是否充电
    // private Timer recordtimer;
    private class MyHandler extends Handler {

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case 1:
                    break;
                case 2:
                    Calendar mCalendar = Calendar.getInstance();
                    long timestamp = mCalendar.getTimeInMillis() / 1000;// 1393844912
                     if (timestamp-lastaccelerometertimestamp>300&&IsCharge == false){

                         IsStopRecord = true;
                         cameraSurfaceView.stopRecord();
                     }
                    break;
                case 3:
                    break;
                case 4:
                    break;
                case 5:
                    Toast.makeText(MainActivity.this, "存储已满，请手动删除", Toast.LENGTH_SHORT);
                    break;
                case 6:
                    Toast.makeText(MainActivity.this, "存储已满，正在删除加锁视频", Toast.LENGTH_SHORT);
                    break;
                case 7:

                    break;
                case 8:

                    break;
                case 9:
                    cameraSurfaceView.stopRecord();
                 //   new clearTFCardethread().start();
                    if (!IsStopRecord) {
                        StartRecord();
                    }
                    break;
                case 10:
                    IsStopRecord = false;
                    if (DateUtils.IsDay()) {
                        PlayMusic(MainActivity.this, 0);
                    } else {
                        PlayMusic(MainActivity.this, 1);
                    }
                    break;
            }
        }
    }



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        EventBus.getDefault().register(this);
        initData();
        initViews();
        context = this;
        String SDpath = FileUtil.getStoragePath(context,true);
        registerReceiver(mbatteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        BindReceiver();

    }

    private void initData() {
        CheckPermissionsUtil checkPermissionsUtil = new CheckPermissionsUtil(this);
        checkPermissionsUtil.requestAllPermission(this);
        gpsLocationManager = GPSLocationManager.getInstances(MainActivity.this);
        wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        videoDb = new DriveVideoDbHelper(this);
        audioMgr = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        // 获取最大音乐音量
        maxVolume = audioMgr.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        // 初始化音量大概为最大音量的1/2
        curVolume = maxVolume / 2;
        // 每次调整的音量大概为最大音量的1/6
        stepVolume = maxVolume / 8;
        Calendar mCalendar = Calendar.getInstance();
        lastaccelerometertimestamp = mCalendar.getTimeInMillis() / 1000;// 1393844912
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);// TYPE_GRAVITY
        IMEI = getid();
        if (null == mSensorManager) {
            Log.d("dfdfd", "deveice not support SensorManager");
        }
        // 参数三，检测的精准度
        mSensorManager.registerListener(this, mSensor,
                SensorManager.SENSOR_DELAY_NORMAL);// SENSOR_DELAY_GAME
        myHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                myHandler.sendEmptyMessage(10);
            }
        }, 4000);
    }

    private void initViews() {

        cameraSurfaceView = (CameraSurfaceView) findViewById(R.id.cameraSurfaceView);
        gpsLocationManager.start(new MyListener());
        ExecutorService exec = Executors.newCachedThreadPool();
        client = new UDPClient();
        exec.execute(client);

        if (timer1 == null) {
            timer1 = new Timer();
            timer1.schedule(task, 3000, 30000);
            //  timer.schedule(recordtask, 5000, 1000 * 4 * 3);
            // timer1.schedule(new clearTFtask(), 3000, 1000 * 60 * 5);
        }
        if (timer3 == null) {
            timer3 = new Timer();
            // timer3.schedule(task, 3000, 30000);
            //  timer.schedule(recordtask, 5000, 1000 * 4 * 3);
            timer3.schedule(clearTFtask, 3000, 1000 * 60 * 5);
        }
        smsReceiver = new SmsReceiver();
        IntentFilter filter = new IntentFilter("android.provider.Telephony.SMS_RECEIVED");
        filter.setPriority(Integer.MAX_VALUE);
        registerReceiver(smsReceiver, filter);
        mVideoServer = new VideoServer(DEFAULT_FILE_PATH);

    }

    class MyListener implements GPSLocationListener {

        @Override
        public void UpdateLocation(Location location) {
            if (location != null) {
                GPSSTR = location.getLongitude() + "," + location.getLatitude()+","+location.getSpeed()+","+location.getBearing();
              //  textGps.setText(location.getLongitude() + "," + location.getLatitude()+","+location.getSpeed()+","+location.getBearing());
            }
        }

        @Override
        public void UpdateStatus(String provider, int status, Bundle extras) {
            if ("gps" == provider) {
                Toast.makeText(MainActivity.this, "定位类型：" + provider, Toast.LENGTH_SHORT).show();
            }
        }

        @Override
        public void UpdateGPSProviderStatus(int gpsStatus) {
            switch (gpsStatus) {
                case GPSProviderStatus.GPS_ENABLED:
                    Toast.makeText(MainActivity.this, "GPS开启", Toast.LENGTH_SHORT).show();
                    break;
                case GPSProviderStatus.GPS_DISABLED:
                    Toast.makeText(MainActivity.this, "GPS关闭", Toast.LENGTH_SHORT).show();
                    break;
                case GPSProviderStatus.GPS_OUT_OF_SERVICE:
                    Toast.makeText(MainActivity.this, "GPS不可用", Toast.LENGTH_SHORT).show();
                    break;
                case GPSProviderStatus.GPS_TEMPORARILY_UNAVAILABLE:
                    Toast.makeText(MainActivity.this, "GPS暂时不可用", Toast.LENGTH_SHORT).show();
                    break;
                case GPSProviderStatus.GPS_AVAILABLE:
                    Toast.makeText(MainActivity.this, "GPS可用啦", Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    }
/*
获取IMEI
 */
    public String getid() {
        TelephonyManager TelephonyMgr = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        String ID = TelephonyMgr.getDeviceId();
        return ID;
    }

    /**
     * 创建Wifi热点
     */
    private boolean createWifiHotspot() {
        if (wifiManager.isWifiEnabled()) {
            //如果wifi处于打开状态，则关闭wifi,
            wifiManager.setWifiEnabled(false);
        }
        WifiConfiguration config = new WifiConfiguration();
        config.SSID = WIFI_HOTSPOT_SSID + MacUtils.getMacAddr();
        String password = (String) SPUtils.get(MainActivity.this, "wifipassword", "12345678");
        config.preSharedKey = password;
        config.hiddenSSID = true;
        config.allowedAuthAlgorithms
                .set(WifiConfiguration.AuthAlgorithm.OPEN);//开放系统认证
        config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
        config.allowedPairwiseCiphers
                .set(WifiConfiguration.PairwiseCipher.TKIP);
        config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
        config.allowedPairwiseCiphers
                .set(WifiConfiguration.PairwiseCipher.CCMP);
        config.status = WifiConfiguration.Status.ENABLED;
        try {
            Method method = wifiManager.getClass().getMethod(
                    "setWifiApEnabled", WifiConfiguration.class, Boolean.TYPE);
            boolean enable = (Boolean) method.invoke(wifiManager, config, true);
            return enable;
        } catch (Exception e) {
            e.printStackTrace();
            return false;

        }
    }

    /**
     * 关闭WiFi热点
     */
    public void closeWifiHotspot() {
        try {
            Method method = wifiManager.getClass().getMethod("getWifiApConfiguration");
            method.setAccessible(true);
            WifiConfiguration config = (WifiConfiguration) method.invoke(wifiManager);
            Method method2 = wifiManager.getClass().getMethod("setWifiApEnabled", WifiConfiguration.class, boolean.class);
            method2.invoke(wifiManager, config, false);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }
/*
  开启wifi
 */
    private void setBrightnessMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.System.canWrite(context)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
                intent.setData(Uri.parse("package:" + context.getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } else {
                //有了权限，具体的动作
                createWifiHotspot();
            }
        }
    }
/*
上传GPS位置信息
 */
    TimerTask task = new TimerTask() {
        @Override
        public void run() {
            Message message = new Message();
            message.what = 2;
            String sendtext = "*" + IMEI + ",1,"
                    + GPSSTR + "#";
            client.send(sendtext);
            message.obj = sendtext;
            myHandler.sendMessage(message);

            //每隔30秒判断车辆是否停止，如果停止，则关闭录像
        }
    };

    TimerTask clearTFtask = new TimerTask() {
        @Override
        public void run() {
            deleteOldestUnlockVideo();
        }
    };

    public void savePicture(String path) {
        List<File> mList = new ArrayList<>();
        mList.add(new File(path));
        OkHttpUtils.post(ApiInterface.savePicture)     // 请求方式和请求url
                .tag(this)
                // 请求的 tag, 主要用于取消对应的请求
                .addFileParams("xczp", mList)
                .execute(new StringCallback() {
                             @Override
                             public void onResponse(boolean isFromCache, String s, Request
                                     request, @Nullable Response response) {
                                 try {
                                     JSONObject mJsonObject = new JSONObject(s);
                                     if (mJsonObject.getBoolean("success")) {
                                         String backurl = mJsonObject.getString("msg");
                                         backurl = backurl.substring(0, backurl.length() - 1);
                                         backurl = "*" + IMEI + ",2," + backurl + "#";
                                         final String url = backurl;
                                         new Thread(
                                                 new Runnable() {
                                                     @Override
                                                     public void run() {
                                                         Message message = Message.obtain();
                                                         message.what = 1;
                                                         client.send(url);
                                                         message.obj = url;
                                                         myHandler.sendMessage(message);
                                                     }
                                                 }
                                         ).start();

                                     }
                                 } catch (Exception e) {
                                     e.printStackTrace();
                                 }
                             }

                             @Override
                             public void onError(boolean isFromCache, Call call, @Nullable Response
                                     response, @Nullable Exception e) {
                                 super.onError(isFromCache, call, response, e);

                             }
                         }
                );

    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void onEvnet(RefreshEvent refresh) {
        if (refresh.getYwlx() == 1) {  //上传图片
            savePicture(refresh.getPhotopath());
        } else if (refresh.getYwlx() == 2) { //获取余额

            String sendtext = "*" + IMEI + ",6,"
                    + refresh.getPhotopath() + "#";
            client.send(sendtext);
        } else if (refresh.getYwlx() == 3) {   //获取4G信号
            String sendtext = "*" + IMEI + ",8,"
                    + refresh.getPhotopath() + "#";
            client.send(sendtext);
        }
    }

    public static String getLocalIpStr(Context context) {
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        return intToIpAddr(wifiInfo.getIpAddress());
    }

    private static String intToIpAddr(int ip) {
        return (ip & 0xff) + "." + ((ip >> 8) & 0xff) + "." + ((ip >> 16) & 0xff) + "." + ((ip >> 24) & 0xff);
    }

    private void BindReceiver() {
        IntentFilter intentFilter = new IntentFilter("udpRcvMsg");
        registerReceiver(myBroadcastReceiver, intentFilter);
    }


    /**
     * 删除最旧视频
     */
    private boolean deleteOldestUnlockVideo() {
        try {
            String sdcardPath = FileUtil.getSDPath();
            // sharedPreferences.getString("sdcardPath","/mnt/sdcard2");
            float sdFree = FileUtil.getSDAvailableSize(sdcardPath);
            float sdTotal = FileUtil.getSDTotalSize(sdcardPath);
            int intSdFree = (int) sdFree;

            while (sdFree < sdTotal * 0.2
                    && intSdFree < 2000) {
                int oldestUnlockVideoId = videoDb.getOldestUnlockVideoId();
                // 删除较旧未加锁视频文件
                if (oldestUnlockVideoId != -1) {
                    String oldestUnlockVideoName = videoDb
                            .getVideNameById(oldestUnlockVideoId);
                    File f = new File(sdcardPath + "/vedio"
                            + File.separator + oldestUnlockVideoName + ".mp4");
                    if (f.exists() && f.isFile()) {
                        int i = 0;
                        while (!f.delete() && i < 3) {
                            i++;
                        }
                    }
                    // 删除数据库记录
                    videoDb.deleteDriveVideoById(oldestUnlockVideoId);
                } else {
                    List<DriveVideo> vedios = videoDb.getAllDriveVideo();
                    int oldestVideoId = videoDb.getOldestVideoId();
                    if (oldestVideoId == -1) {
                        /**
                         * 有一种情况：数据库中无视频信息。导致的原因：
                         * 1：升级时选Download的话，不会清理USB存储空间，应用数据库被删除； 2：应用被清除数据
                         * 这种情况下旧视频无法直接删除， 此时如果满存储，需要直接删除
                         */
                        File file = new File(sdcardPath + "/vedio/");
                        sdFree = FileUtil.getSDAvailableSize(sdcardPath);
                        intSdFree = (int) sdFree;
                        if (sdFree < sdTotal
                                * 0.2
                                || intSdFree < 2000) {
                            // 此时若空间依然不足,提示用户清理存储（已不是行车视频的原因）
                            Message message = new Message();
                            message.what = 5;
                            myHandler.sendMessage(message);
                            //      Toast.makeText(MainActivity.this,"存储已满，请手动删除",Toast.LENGTH_SHORT);

                            return false;
                        }
                    } else {
                        // 提示用户清理空间，删除较旧的视频（加锁）

                        Message message = new Message();
                        message.what = 6;
                        myHandler.sendMessage(message);
                        String oldestVideoName = videoDb
                                .getVideNameById(oldestVideoId);
                        File f = new File(sdcardPath + "/vedio"
                                + File.separator + oldestVideoName + ".mp4");
                        if (f.exists() && f.isFile()) {

                            int i = 0;
                            while (!f.delete() && i < 3) {
                                i++;
                            }
                        }
                        // 删除数据库记录
                        videoDb.deleteDriveVideoById(oldestVideoId);
                    }
                }
                // 更新剩余空间
                sdFree = FileUtil.getSDAvailableSize(sdcardPath);
                intSdFree = (int) sdFree;
            }
            return true;
        } catch (Exception e) {
            /*
             * 异常原因：1.文件由用户手动删除
             */
            //    MyLog.e("[MainActivity]deleteOldestUnlockVideo:Catch Exception:"
            //            + e.toString());
            e.printStackTrace();
            return true;
        }
    }

    /**
     * 调整音量
     */
    private void adjustVolume() {
        audioMgr.setStreamVolume(AudioManager.STREAM_MUSIC, curVolume,
                AudioManager.FLAG_PLAY_SOUND);
    }

    private class MyBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String mAction = intent.getAction();
            switch (mAction) {
                case "udpRcvMsg":
                    String msg = intent.getStringExtra("udpRcvMsg");
                    GetZhilingType(msg);
                    break;
            }
        }
    }

    private void GetZhilingType(String msg) {

        if (msg.contains("*") && msg.contains("#")) {
            msg = msg.replace("*", "").replace("#", "");
        }
        String[] types = msg.split(",");
        if (types.length >= 2) {
            SendCommonReply(types[1]);
            switch (types[1]) {
                case "99":// 微信公众号抓拍的协议，我这边接受服务器的指令，拍照并上传，并把照片路径发送给服务器
                    cameraSurfaceView.capture();

                    break;
                case "98"://查看热点连接的IP地址列表
                    ArrayList<String> connectedIP = getConnectedIP();
                    StringBuilder resultList = new StringBuilder();
                    for (String ip : connectedIP) {
                        resultList.append(ip);
                        resultList.append("@");
                    }
                    final String sendtext = "*" + IMEI + ",3,"
                            + resultList.substring(0, resultList.length() - 1) + "#";
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            client.send(sendtext);
                        }
                    }).start();

                    break;
                case "97"://修改热点连接密码
                    if (types.length == 3) {
                        closeWifiHotspot();
                        SPUtils.put(MainActivity.this, "wifipassword", types[2]);
                        boolean success = createWifiHotspot();
                        String updatewifetext = "";
                        if (success) {
                            updatewifetext = "*" + IMEI + ",4,"
                                    + 0 + "#";
                        } else {
                            updatewifetext = "*" + IMEI + ",4,"
                                    + 1 + "#";
                        }
                        final String passwordtext = updatewifetext;
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                client.send(passwordtext);
                            }
                        }).start();

                    }

                    break;
                case "96"://剩余流量

                    break;
                case "95"://费用查询
                    FileUtil.sendSMS("10086", "cxye");
                    break;
                case "94"://音量调节（加减）
                    if (types.length == 3) {
                        curVolume = Integer.parseInt(types[2]);
                        if (curVolume >= maxVolume) {
                            curVolume = maxVolume;
                        }
                        // 调整音量
                        adjustVolume();
                        final String volumetext = "*" + IMEI + ",7,"
                                + curVolume + "#";
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                client.send(volumetext);
                            }
                        }).start();

                    }
                    break;
                case "93"://4G信号强度
                    FileUtil.getCurrentNetDBM(MainActivity.this);
                    break;
                case "92"://设备重启
                    PowerManager pManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
                    pManager.reboot("重启");
                    break;
                case "91":// 切换摄像头
                    if (types.length == 3) {
                        cameraSurfaceView.stopRecord();
                        Carmertype = Integer.parseInt(types[2]);
                        boolean success = false;
                        if (Carmertype == 1)
                            success = cameraSurfaceView.setDefaultCamera(true);
                        else if (Carmertype == 2) {
                            success = cameraSurfaceView.setDefaultCamera(false);
                        }

                        final String volumetext = "*" + IMEI + ",10,"
                                + Carmertype + "," + (success == true ? 0 : 1) + "#";
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                client.send(volumetext);
                            }
                        }).start();

                    }
                    break;
                case "90":// 开启录像
                    String recordopentext = "";
                    if (!cameraSurfaceView.isRecording) {
                        if (DateUtils.IsDay()) {
                            PlayMusic(MainActivity.this, 0);
                        } else {
                            PlayMusic(MainActivity.this, 1);
                        }
                        new Handler().postDelayed(new Runnable() {
                            public void run() {
                                //execute the task
                                final String opentext = "*" + IMEI + ",11," +
                                        +(cameraSurfaceView.isRecording == true ? 0 : 1) + "#";

                                new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        client.send(opentext);
                                    }
                                }).start();
                            }
                        }, 14000);
                        ;
                    } else {
                        recordopentext = "*" + IMEI + ",11," +
                                +(0) + "#";
                        final String opentext = recordopentext;
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                client.send(opentext);
                            }
                        }).start();
                    }

                    break;
                case "89":// 关闭录像
                    String recordclosetext = "";
                    IsStopRecord = true;
                    cameraSurfaceView.stopRecord();
                    recordclosetext = "*" + IMEI + ",12," +
                            +(cameraSurfaceView.isRecording == true ? 1 : 0) + "#";
                    final String closetext = recordclosetext;
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            client.send(closetext);
                        }
                    }).start();
                    break;
                case "88":// 关闭录像提取录像列表平台发送  *终端编号,88,开始时间,结束时间#
                    String filenamestext = "";
                    if (types.length == 4) {
                        String time1 = types[2];
                        String time2 = types[3];
                        filenamestext = FileUtil.GetTimeFiles(DEFAULT_FILE_PATH, time1, time2);
                    }
                    filenamestext = "*" + IMEI + ",13," +
                            filenamestext + "#";
                    final String sendfilenamestext = filenamestext;
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            client.send(sendfilenamestext);
                        }
                    }).start();
                    break;
                case "87"://提取录像平台发送  *终端编号,87,文件名#
                    if (types.length == 3) {
                        String filename = types[2];
                        saveFile(DEFAULT_FILE_PATH + filename);

                    }
                    break;

            }

        }
    }

    private void StartRecord() {
        if (!cameraSurfaceView.isRecording) {
            try {
                cameraSurfaceView.startRecord();
                myHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        myHandler.sendEmptyMessage(9);
                    }
                }, 1 * 60 * 1000);

            } catch (IllegalStateException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

    }

    public void saveFile(String path) {
        List<File> mList = new ArrayList<>();
        mList.add(new File(path));
        OkHttpUtils.post(ApiInterface.saveFile)     // 请求方式和请求url
                .tag(this)
                // 请求的 tag, 主要用于取消对应的请求
                .addFileParams("xczp", mList)
                .execute(new StringCallback() {
                             @Override
                             public void onResponse(boolean isFromCache, String s, Request
                                     request, @Nullable Response response) {
                                 try {
                                     JSONObject mJsonObject = new JSONObject(s);
                                     if (mJsonObject.getBoolean("success")) {
                                         String backurl = mJsonObject.getString("msg");
                                         backurl = backurl.substring(0, backurl.length() - 1);
                                         backurl = "*" + IMEI + ",14," + backurl + "#";
                                         final String url = backurl;
                                         new Thread(
                                                 new Runnable() {
                                                     @Override
                                                     public void run() {
                                                         Message message = Message.obtain();
                                                         message.what = 2;
                                                         client.send(url);
                                                         message.obj = url;
                                                         myHandler.sendMessage(message);
                                                     }
                                                 }
                                         ).start();

                                     }
                                 } catch (Exception e) {
                                     e.printStackTrace();
                                 }
                             }

                             @Override
                             public void onError(boolean isFromCache, Call call, @Nullable Response
                                     response, @Nullable Exception e) {
                                 super.onError(isFromCache, call, response, e);

                             }
                         }
                );

    }

    public void PlayMusic(Context mcontext, int type) {  //0 day 1 night
        mediaPlayer = new MediaPlayer();
        switch (type) {
            case 0:
                mediaPlayer = MediaPlayer.create(mcontext, R.raw.day);
                break;
            case 1:
                mediaPlayer = MediaPlayer.create(mcontext, R.raw.night);
                break;
            default:
                mediaPlayer = MediaPlayer.create(mcontext, R.raw.day);
                break;

        }
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        if (mediaPlayer != null) {
            mediaPlayer.stop();
        }
        // 通过异步的方式装载媒体资源
        mediaPlayer.prepareAsync();
        mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                // 装载完毕回调
                mediaPlayer.start();
            }

        });
        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                // 在播放完毕被回调
                StartRecord();
            }
        });
        //    StartRecord();
    }



    /*
    充电状态获取
     */
    private BroadcastReceiver mbatteryReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
                int status = intent.getIntExtra("status", BatteryManager.BATTERY_STATUS_UNKNOWN);
                if (status == BatteryManager.BATTERY_STATUS_CHARGING) {
                    IsCharge = true;
                }
            } else {
                IsCharge = false;
            }
        }
    };
    private int mX, mY, mZ;
    private long lastaccelerometertimestamp = 0; //上次加速度不为0的时间
    private int maxvalue;

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor == null) {
            return;
        }

        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            int x = (int) event.values[0];
            int y = (int) event.values[1];
            int z = (int) event.values[2];
            Calendar mCalendar = Calendar.getInstance();
            long stamp = mCalendar.getTimeInMillis() / 1000;// 1393844912
            int px = Math.abs(mX - x);
            int py = Math.abs(mY - y);
            int pz = Math.abs(mZ - z);
            maxvalue = FileUtil.getMaxValue(px, py, pz);
            if (maxvalue > 2) {
                lastaccelerometertimestamp = stamp;
            } else {

            }
            mX = x;
            mY = y;
            mZ = z;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraSurfaceView.closeCamera();
        mVideoServer.stop();
        task.cancel();
        clearTFtask.cancel();
        if (timer1 != null) {
            timer1.cancel();
            timer1 = null;
        }
        if (timer3 != null) {
            timer3.cancel();
            timer3 = null;
        }
        IsStopRecord = true;
        unregisterReceiver(smsReceiver);
        unregisterReceiver(mbatteryReceiver);
        EventBus.getDefault().unregister(this);
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
      /*  if (keyCode == KeyEvent.KEYCODE_BACK) {
            moveTaskToBack(false);
            return true;
        }*/
        return super.onKeyDown(keyCode, event);
    }

    private void SendCommonReply(final String zlbh){

        new Thread(
                new Runnable() {
                    @Override
                    public void run() {
                        String url = "*"+IMEI+",0,"+zlbh+"#";
                        client.send(url);
                    }
                }
        ).start();

    }
}

