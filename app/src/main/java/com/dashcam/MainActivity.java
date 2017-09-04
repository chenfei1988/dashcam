package com.dashcam;

import android.app.AppOpsManager;
import android.app.usage.NetworkStats;
import android.app.usage.NetworkStatsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
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
import android.os.Process;
import android.os.RemoteException;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.baidu.location.BDLocation;
import com.baidu.location.BDLocationListener;
import com.dashcam.base.ApiInterface;
import com.dashcam.base.DateUtils;
import com.dashcam.base.FileInfo;
import com.dashcam.base.FileSUtil;
import com.dashcam.base.MacUtils;
import com.dashcam.base.MyAPP;
import com.dashcam.base.NetworkStatsHelper;
import com.dashcam.base.PhoneInfoUtils;
import com.dashcam.base.RefreshEvent;
import com.dashcam.base.SPUtils;
import com.dashcam.httpservers.VideoServer;
import com.dashcam.location.LocationUtil;
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
import java.io.FileFilter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import butterknife.Bind;
import butterknife.ButterKnife;
import okhttp3.Call;
import okhttp3.Request;
import okhttp3.Response;

import static com.dashcam.photovedio.FileUtil.getConnectedIP;

public class MainActivity extends AppCompatActivity implements SensorEventListener, BDLocationListener {

    @Bind(R.id.liuliang)
    TextView liuliang;
    private String IMEI = "";
    private String GPSSTR = ",,,";
    private UDPClient client = null;
    public static Context context;
    private final MyHandler myHandler = new MyHandler();
    private CameraSurfaceView cameraSurfaceView;
    private WifiManager wifiManager;
    /**
     * 时间计时器
     */
    private Timer timer1 = null;//上传定位坐标定时器
    private Timer timer3 = null;//清除TP卡内容定时器
    /**
     * 热点名称
     */
    private static final String WIFI_HOTSPOT_SSID = "XiaoMa-";
    //  private SmsReceiver smsReceiver;
    private AudioManager audioMgr = null; // Audio管理器，用了控制音量
    private int maxVolume = 50; // 最大音量值
    private int curVolume = 20; // 当前音量值
    private int stepVolume = 0; // 每次调整的音量幅度
    private int Carmertype = 1;//前后摄像头，1为前置摄像头，2为后置摄像头
    // private boolean IsStopRecord = false;
    MediaPlayer mediaPlayer;
    private SensorManager mSensorManager;
    private Sensor mSensor;
    private String DEFAULT_FILE_PATH = "";
    private VideoServer mVideoServer;
    private MyBroadcastReceiver myBroadcastReceiver = new MyBroadcastReceiver();
    //   private boolean IsCharge = false;//是否充电
    //   private boolean IsCarStop = false;//超过5分钟静止
    private boolean IsXiumian = false;//是否是休眠状态
    LocationUtil mLocationUtil;
    private String rootPath = "";//存放视频的路径
    int Batterylevel = 100;//电池电量
    private String phonenumber = "";
    private String G4Itedbm = "0";//4G信号强弱
    public static boolean IsBackCamera = true;
    public static boolean IsZhualu = false;//是否在抓录视频
    private boolean IsPengZhuang = false;//是否是碰撞
    int cishu = 0;//上传3次，不成功退出
    int Batterylevelbobao = 20;//电池电量

    @Override
    public void onReceiveLocation(BDLocation location) {
        if (location != null) {
            if (location.getLongitude() > 1 && location.getLatitude() > 1) {
                GPSSTR = location.getLongitude() + "," + location.getLatitude() + "," + location.getSpeed() + "," + location.getDirection();
            }
            Toast.makeText(MainActivity.this, GPSSTR, Toast.LENGTH_LONG);
            //  textGps.setText(location.getLongitude() + "," + location.getLatitude()+","+location.getSpeed()+","+location.getBearing());
        }
    }

    @Override
    public void onConnectHotSpotMessage(String s, int i) {

    }

    // private Timer recordtimer;
    private class MyHandler extends Handler {

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case 9:
                    cameraSurfaceView.stopRecord();
                    //   new clearTFCardethread().start();
                    //  if (!IsStopRecord && IsBackCamera&&!IsZhualu) { //不停止录像并且是后置摄像头，因为抓录前置摄像头时只用录像一次
                    if (IsBackCamera && !IsZhualu) { //不停止录像并且是后置摄像头，因为抓录前置摄像头时只用录像一次
                        StartRecord();
                    }
                    break;
                case 10:
                    //   IsStopRecord = false;
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
        context = this;
        initData();
        initViews();
        registerReceiver(mbatteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        BindReceiver();
        //   DeleteOldVedioFile();
        //  setBrightnessMode();//开启Wifi

    }

    private void initData() {
        CheckPermissionsUtil checkPermissionsUtil = new CheckPermissionsUtil(this);
        checkPermissionsUtil.requestAllPermission(this);
        if (hasPermissionToReadNetworkStats()) {
            NetworkStatsManager networkStatsManager = (NetworkStatsManager) getSystemService(NETWORK_STATS_SERVICE);
            long liangliangbyte = new NetworkStatsHelper(networkStatsManager).getAllMonthMobile(this);
            liuliang.setText(liangliangbyte + "B");
        }
        phonenumber = new PhoneInfoUtils(context).getNativePhoneNumber();
        if (phonenumber == null) {
            phonenumber = "";
        }
        FileUtil.getCurrentNetDBM(MainActivity.this);
        rootPath = FileUtil.getStoragePath(this, true);
        if (rootPath == null) {
            rootPath = FileUtil.getStoragePath(context, false);
        }
        DEFAULT_FILE_PATH = rootPath + "/vedio/";
        mLocationUtil = new LocationUtil(this, this);
        mLocationUtil.startLocate();
        wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        //videoDb = new DriveVideoDbHelper(this);
        audioMgr = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        // 获取最大音乐音量
        maxVolume = audioMgr.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        // 初始化音量大概为最大音量的1/2
        curVolume = maxVolume / 2;
        // 每次调整的音量大概为最大音量的1/6
        // stepVolume = maxVolume / 8;
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
        InitDeleteShanchu();
        myHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                myHandler.sendEmptyMessage(10);
            }
        }, 3000);
    }

    private void initViews() {

        cameraSurfaceView = (CameraSurfaceView) findViewById(R.id.cameraSurfaceView);
        ExecutorService exec = Executors.newCachedThreadPool();
        findViewById(R.id.intoxiumian).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                IntoXiumian();
            }
        });
        findViewById(R.id.outxiumian).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                OutXiumian();
            }
        });
        client = new UDPClient();
        exec.execute(client);
        if (timer1 == null) {
            timer1 = new Timer();
            timer1.schedule(task, 3000, 10000);
        }
        if (timer3 == null) {
            timer3 = new Timer();
            timer3.schedule(clearTFtask, 3000, 1000 * 60 * 2);
        }
      /*  smsReceiver = new SmsReceiver();
        IntentFilter filter = new IntentFilter("android.provider.Telephony.SMS_RECEIVED");
        filter.setPriority(Integer.MAX_VALUE);
        registerReceiver(smsReceiver, filter);*/
        mVideoServer = new VideoServer(DEFAULT_FILE_PATH);
    }

    /*
  获取IMEI
   */
    public String getid() {
        TelephonyManager TelephonyMgr = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        String ID = TelephonyMgr.getDeviceId();
        return ID;
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
            DeleteOldVedioFile();
        }
    };


    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void onEvnet(RefreshEvent refresh) {
        if (refresh.getYwlx() == 1) {  //上传图片
            savePicture(refresh.getPhotopath());

            if (!IsBackCamera) {
                //  cameraSurfaceView.stopRecord();
                cameraSurfaceView.setDefaultCamera(true);
                IsBackCamera = true;
                //  IsStopRecord = false;
                if (IsXiumian) {
                    cameraSurfaceView.closeCamera();
                } else {
                    cameraSurfaceView.startRecord();
                }
            }
        } else if (refresh.getYwlx() == 2) { //

            String sendtext = "*" + IMEI + ",6,"
                    + refresh.getPhotopath() + "#";
            client.send(sendtext);
        } else if (refresh.getYwlx() == 3) {   //获取4G信号
            G4Itedbm = refresh.getPhotopath();

         /*   String sendtext = "*" + IMEI + ",8,"
                    + refresh.getPhotopath() + "#";
            client.send(sendtext);*/
        } else if (refresh.getYwlx() == 4) {   //抓录视频
            String vediopath = refresh.getPhotopath();
            saveFile(vediopath);
            if (!IsBackCamera) {
                cameraSurfaceView.stopRecord();
                cameraSurfaceView.setDefaultCamera(true);
                IsBackCamera = true;
                if (IsXiumian) {
                    cameraSurfaceView.closeCamera();
                } else {
                    //   IsStopRecord = false;
                    cameraSurfaceView.startRecord();
                }
            }
         /*   String sendtext = "*" + IMEI + ",8,"
                    + refresh.getPhotopath() + "#";
            client.send(sendtext);*/
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
        IntentFilter intentFilter1 = new IntentFilter("udpRcvMsg");
        IntentFilter intentFilter2 = new IntentFilter("com.android.settings.suspend");
        IntentFilter intentFilter3 = new IntentFilter(Intent.ACTION_POWER_CONNECTED);
        IntentFilter intentFilter4 = new IntentFilter("rock.intent.CHECK_NEW_SOFTWARE");//有升级消息的通知
        IntentFilter intentFilter5 = new IntentFilter("rock.intent.INSTALL.SUCCESS");//升级成功的广播
        IntentFilter intentFilter6 = new IntentFilter("rock.intent.UPDATE_FAIL");//升级失败的广播
        registerReceiver(myBroadcastReceiver, intentFilter1);
        registerReceiver(myBroadcastReceiver, intentFilter2);
        registerReceiver(myBroadcastReceiver, intentFilter3);
        registerReceiver(myBroadcastReceiver, intentFilter4);
        registerReceiver(myBroadcastReceiver, intentFilter5);
        registerReceiver(myBroadcastReceiver, intentFilter6);

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
                case "com.android.settings.suspend":  //进入休眠
                    IntoXiumian();
                    break;
                case Intent.ACTION_POWER_CONNECTED
                        :  //退出休眠
                    OutXiumian();
                    break;
                case "rock.intent.CHECK_NEW_SOFTWARE"://有更新的时候
                    final String updatetext = "*" + IMEI + ",25"
                            + "#";
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            client.send(updatetext);
                        }
                    }).start();
                    break;
                case "rock.intent.INSTALL.SUCCESS":
                    final String updatesuccesstext = "*" + IMEI + ",26"
                            + "#";
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            client.send(updatesuccesstext);
                        }
                    }).start();
                    break;
                case "rock.intent.UPDATE_FAIL":
                    final String updatefailedtext = "*" + IMEI + ",27"
                            + "#";
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            client.send(updatefailedtext);
                        }
                    }).start();
                    break;
            }
        }
    }

    private synchronized void GetZhilingType(String msg) {

        if (msg.contains("*") && msg.contains("#")) {
            msg = msg.replace("*", "").replace("#", "");
        }
        String[] types = msg.split(",");
        if (types.length >= 2) {
            SendCommonReply(types[1]);
            switch (types[1]) {
                case "99":// 微信公众号抓拍的协议，我这边接受服务器的指令，拍照并上传，并把照片路径发送给服务器
                    if (types.length == 3) {
                        String lushu = types[2];
                        if (IsXiumian == true) {
                            cameraSurfaceView.openCamera();
                            cameraSurfaceView.startPreview();
                        }
                        // (0 前置摄像头,1 后置摄像头)
                        if (lushu.equals("0")) {
                            cameraSurfaceView.capture();
                        } else if (lushu.equals("1")) {
                            //           IsStopRecord = true;
                            //     cameraSurfaceView.isRecording = false;
                            try {
                                cameraSurfaceView.stopRecord();
                                cameraSurfaceView.setDefaultCamera(false);
                                IsBackCamera = false;
                                cameraSurfaceView.capture();
                              /*  new Handler().postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        cameraSurfaceView.capture();
                                    }
                                }, 500);*/
                            } catch (Exception e) {

                            }
                        }
                    }
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
                        boolean success = createWifiHotspot("", types[2]);
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
                    final String sendliuliangtext = "*" + IMEI + ",5,"
                            + liuliang.getText().toString().trim() + "#";
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            client.send(sendliuliangtext);
                        }
                    }).start();
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
                    //   FileUtil.getCurrentNetDBM(MainActivity.this);
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
                    //  IsStopRecord = true;
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
                case "86"://开启WIFI  *终端编号,86#
                    if (FileSUtil.isWifiApOpen(MainActivity.this)) {
                        closeWifiHotspot();
                    }
                    boolean startwifisuccess = createWifiHotspot("", "");
                    final String startWifitext = "*" + IMEI + ",15," +
                            (startwifisuccess == true ? 0 : 1) + "#";
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            client.send(startWifitext);
                        }
                    }).start();
                    break;
                case "85"://关闭WIFI  *终端编号,85#
                    boolean closewifisuccess = closeWifiHotspot();
                    final String closeWifitext = "*" + IMEI + ",16," +
                            (closewifisuccess == true ? 0 : 1) + "#";
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            client.send(closeWifitext);
                        }
                    }).start();
                    break;
                case "84"://修改热点连接用户名  *终端编号,84,用户名#

                    if (types.length == 3) {
                        String username = types[2];
                        SPUtils.put(MainActivity.this, "wifiname", types[2]);
                        closeWifiHotspot();
                        boolean updatewifisuccess = createWifiHotspot(username, "");
                        final String updateWifitext = "*" + IMEI + ",15," +
                                (updatewifisuccess == true ? 0 : 1) + "#";
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                client.send(updateWifitext);
                            }
                        }).start();
                    }
                    break;
                case "82"://.设备状态查询
                    int connectnum = getConnectedIP().size();//热点连接数
                    String version = MyAPP.getVersion();
                    String path = FileUtil.getStoragePath(this, true);
                    int hasSDk = 0;
                    if (path == null) {
                        hasSDk = 1;
                    }
                    final String statustext = "*" + IMEI + ",19," +
                            G4Itedbm + "," + Batterylevel + "," + phonenumber + ","
                            + connectnum + "," + hasSDk + "," + version + "#";
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            client.send(statustext);
                        }
                    }).start();
                    break;
                case "81"://当前音量
                    int current = audioMgr.getStreamVolume(AudioManager.STREAM_MUSIC);
                    final String currentvolumetext = "*" + IMEI + ",20," +
                            current + "#";
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            client.send(currentvolumetext);
                        }
                    }).start();
                    break;
                case "80"://新增视频抓拍协议
                    if (types.length == 3) {
                        String lushu = types[2];
                        // (0 前置摄像头,1 后置摄像头)
                        if (IsXiumian) {
                            cameraSurfaceView.openCamera();
                            cameraSurfaceView.startPreview();
                        }
                        if (lushu.equals("0")) {
                            IsZhualu = true;
                            if (IsXiumian) {
                                cameraSurfaceView.startRecord();
                            }
                            //  cameraSurfaceView.capture();
                        } else if (lushu.equals("1")) {
                            //     IsStopRecord = true;
                            try {
                                cameraSurfaceView.stopRecord();
                                cameraSurfaceView.setDefaultCamera(false);
                                IsBackCamera = false;
                                IsZhualu = true;
                                StartRecord();
                            } catch (Exception e) {

                            }
                        }
                    }
                    break;
                case "79":
                    if (types.length == 3) {
                        String lushu = types[2];
                        // 平台发送  *终端编号,79,类型(1 720*1080P ,2 1080*1920) #
                        if (lushu.equals("1")) {
                            CameraSurfaceView.VIDEO_SIZE = new int[]{1280, 720};
                            //  cameraSurfaceView.capture();
                        } else if (lushu.equals("2")) {

                            CameraSurfaceView.VIDEO_SIZE = new int[]{1920, 1080};
                        }
                        final String vediosizetext = "*" + IMEI + ",22," +
                                lushu + "#";
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                client.send(vediosizetext);
                            }
                        }).start();
                    }
                    break;
                case "78": //声音开关
                    if (types.length == 3) {
                        String isopen = types[2];
                        if (isopen.equals("0")) {
                            audioMgr.setRingerMode(AudioManager.RINGER_MODE_SILENT);
                        } else {
                            audioMgr.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                        }
                    }
                    break;
                case "76": //WINFI状态查询
                    //  APK回复　*终端编号, 23, WINFI开关(0开, 1关), WIFI用户名, WIFI密码#
                    String wifiapopen = "1";
                    if (FileSUtil.isWifiApOpen(MainActivity.this)) {
                        wifiapopen = "0";
                    }
                    String nameandpassword = FileSUtil.Getapwifinameandpassword(MainActivity.this);
                    final String wifiapstate = "*" + IMEI + ",24," +
                            wifiapopen + "," + nameandpassword + "#";
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            client.send(wifiapstate);
                        }
                    }).start();
                    break;
                default:
                    break;

            }

        }
    }

    private void StartRecord() {
        if (!cameraSurfaceView.isRecording) {
            try {
                cameraSurfaceView.startRecord();
            } catch (IllegalStateException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

    }


    public void PlayMusic(Context mcontext, final int type) {  //0 day 1 night
        mediaPlayer = new MediaPlayer();
        switch (type) {
            case 0:
                mediaPlayer = MediaPlayer.create(mcontext, R.raw.day);
                break;
            case 1:
                mediaPlayer = MediaPlayer.create(mcontext, R.raw.night);
                break;
            case 2:
                mediaPlayer = MediaPlayer.create(mcontext, R.raw.xiumian);
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
                if (type != 2) {
                    StartRecord();
                }
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
            int temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
            if (temperature > 680) {

                final String xiumiantext = "*" + IMEI + ",77,"
                        + 3 + "#";
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        client.send(xiumiantext);
                    }
                }).start();
            }
            if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
                int status = intent.getIntExtra("status", BatteryManager.BATTERY_STATUS_UNKNOWN);
                // 电池当前的电量, 它介于0和 EXTRA_SCALE之间
                Batterylevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                // 电池电量的最大值
                if (status == BatteryManager.BATTERY_STATUS_NOT_CHARGING) {
                    if (Batterylevel == Batterylevelbobao) {
                        if (Batterylevelbobao == 20) {
                            Batterylevelbobao = 10;
                            final String xiumiantext = "*" + IMEI + ",77,"
                                    + 1 + "#";
                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    client.send(xiumiantext);
                                }
                            }).start();
                            Timer timer = new Timer();//实例化Timer类
                            timer.schedule(new TimerTask() {
                                public void run() {
                                    client.send(xiumiantext);
                                    this.cancel();
                                }
                            }, 10000);//10秒
                        } else if (Batterylevelbobao == 10) {
                            Batterylevelbobao = 1;
                            final String xiumiantext = "*" + IMEI + ",77,"
                                    + 1 + "#";
                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    client.send(xiumiantext);
                                }
                            }).start();
                            Timer timer = new Timer();//实例化Timer类
                            timer.schedule(new TimerTask() {
                                public void run() {
                                    client.send(xiumiantext);
                                    this.cancel();
                                }
                            }, 10000);//10秒
                        } else if (Batterylevelbobao == 1) {
                            final String xiumiantext = "*" + IMEI + ",77,"
                                    + 2 + "#";
                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    client.send(xiumiantext);
                                }
                            }).start();
                        }

                    }
                }
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
        /*    if (maxvalue > 3) {
                lastaccelerometertimestamp = stamp;
                //唤醒休眠
                IsCarStop = false;
                if (IsXiumian == true) {
                    IsXiumian = false;
                    final String xiumiantext = "*" + IMEI + ",18,"
                            + 1 + "#";
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            client.send(xiumiantext);
                        }
                    }).start();
                    StartRecord();
                }
            }
            mX = x;
            mY = y;
            mZ = z;*/
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
        //   IsStopRecord = true;
        //  unregisterReceiver(smsReceiver);
        unregisterReceiver(mbatteryReceiver);
        unregisterReceiver(myBroadcastReceiver);
        EventBus.getDefault().unregister(this);
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
        mLocationUtil.stopLocate();
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        return super.onKeyLongPress(keyCode, event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
  /*      if (keyCode == KeyEvent.KEYCODE_BACK) {
            moveTaskToBack(false);
            return true;
        }*/
        if (keyCode == KeyEvent.KEYCODE_F11) {
            long currenttime = System.currentTimeMillis();
            IsPengZhuang = true;
            cameraSurfaceView.capture();
            cameraSurfaceView.capture();
            cameraSurfaceView.capture();
            cameraSurfaceView.capture();
            cameraSurfaceView.capture();
            cameraSurfaceView.stopRecord();
            cameraSurfaceView.setDefaultCamera(false);
            IsBackCamera = false;
            cameraSurfaceView.capture();
            cameraSurfaceView.capture();
            cameraSurfaceView.capture();
            cameraSurfaceView.capture();
            cameraSurfaceView.capture();
            cameraSurfaceView.setDefaultCamera(true);
            cameraSurfaceView.startRecord();
            IsBackCamera = true;
            IsPengZhuang = false;
            FileUtil.MoveFiletoDangerFile(currenttime, rootPath);
            return true;
        }
        return super.onKeyDown(keyCode, event);

    }

    private void SendCommonReply(final String zlbh) {

        new Thread(
                new Runnable() {
                    @Override
                    public void run() {
                        String url = "*" + IMEI + ",0," + zlbh + "#";
                        client.send(url);
                    }
                }
        ).start();

    }

    /**
     * 创建Wifi热点
     */
    private boolean createWifiHotspot(String name, String password) {
        if (wifiManager.isWifiEnabled()) {
            //如果wifi处于打开状态，则关闭wifi,
            wifiManager.setWifiEnabled(false);
        }
        try {
            Method method = wifiManager.getClass().getMethod("getWifiApConfiguration");
            method.setAccessible(true);
            WifiConfiguration config = (WifiConfiguration) method.invoke(wifiManager);
            if (!name.equals("")) {
                config.SSID = name;
            }
            if (!password.equals("")) {
                config.preSharedKey = password;
            }
            Method method2 = wifiManager.getClass().getMethod(
                    "setWifiApEnabled", WifiConfiguration.class, Boolean.TYPE);
            boolean enable = (Boolean) method2.invoke(wifiManager, config, true);
            return enable;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    /*    if (name.equals("")) {
            name = (String) SPUtils.get(MainActivity.this, "wifiname", WIFI_HOTSPOT_SSID + MacUtils.getMacAddr());
        }
        config.SSID = name;
        if (password.equals("")) {
            password = (String) SPUtils.get(MainActivity.this, "wifipassword", "12345678");
        }
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
        config.status = WifiConfiguration.Status.ENABLED;*/
      /*  try {
            Method method = wifiManager.getClass().getMethod(
                    "setWifiApEnabled", WifiConfiguration.class, Boolean.TYPE);
            boolean enable = (Boolean) method.invoke(wifiManager, config, true);
            return enable;
        } catch (Exception e) {
            e.printStackTrace();
            return false;

        }*/
    }

    /**
     * 关闭WiFi热点
     */
    public boolean closeWifiHotspot() {
        boolean success = true;
        try {
            Method method = wifiManager.getClass().getMethod("getWifiApConfiguration");
            method.setAccessible(true);
            WifiConfiguration config = (WifiConfiguration) method.invoke(wifiManager);
            Method method2 = wifiManager.getClass().getMethod("setWifiApEnabled", WifiConfiguration.class, boolean.class);
            method2.invoke(wifiManager, config, false);
        } catch (NoSuchMethodException e) {
            success = false;
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            success = false;
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            success = false;
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            success = false;
            e.printStackTrace();
        }
        return success;
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
                createWifiHotspot("", "");
            }
        }
    }

    public static long getTimesMonthMorning() {
        Calendar cal = Calendar.getInstance();
        cal.set(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH), 0, 0, 0);
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMinimum(Calendar.DAY_OF_MONTH));
        return cal.getTimeInMillis();
    }

    private boolean hasPermissionToReadNetworkStats() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        final AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(), getPackageName());
        if (mode == AppOpsManager.MODE_ALLOWED) {
            return true;
        }

        requestReadNetworkStats();
        return false;
    }

    // 打开“有权查看使用情况的应用”页面
    private void requestReadNetworkStats() {
        Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
        startActivity(intent);
    }


    /**
     * 调整音量
     */
    private void adjustVolume() {
        audioMgr.setStreamVolume(AudioManager.STREAM_MUSIC, curVolume,
                AudioManager.FLAG_PLAY_SOUND);
    }

    public void saveFile(final String path) {
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
                                         cishu = 0;
                                         String backurl = mJsonObject.getString("msg");
                                         //    backurl = backurl.substring(0, backurl.length() - 1);
                                         if (IsZhualu) {
                                             IsZhualu = false;
                                             backurl = "*" + IMEI + ",21," + backurl + "#";
                                         } else {
                                             backurl = "*" + IMEI + ",14," + backurl + "#";
                                         }
                                         final String url = backurl;
                                         new Thread(
                                                 new Runnable() {
                                                     @Override
                                                     public void run() {
                                                         client.send(url);
                                                        /* Message message = Message.obtain();
                                                         message.what = 2;
                                                         message.obj = url;
                                                         myHandler.sendMessage(message);*/
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
                                 if (cishu < 3) {
                                     saveFile(path);
                                 }
                               /*  String backurl = "";
                                 if (IsZhualu) {
                                     IsZhualu = false;
                                     backurl = "*" + IMEI + ",21," + backurl + "#";
                                 } else {
                                     backurl = "*" + IMEI + ",14," + backurl + "#";
                                 }
                                 final String url = backurl;
                                 new Thread(
                                         new Runnable() {
                                             @Override
                                             public void run() {
                                                 client.send(url);
                                              /*   Message message = Message.obtain();
                                                 message.what = 2;
                                                 client.send(url);
                                                 message.obj = url;
                                                 myHandler.sendMessage(message);
                                             }
                                         }
                                 ).start();*/
                                 super.onError(isFromCache, call, response, e);

                             }
                         }
                );

    }

    public void savePicture(final String path) {
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
                                         cishu = 0;
                                         InitDeleteShanchu();
                                         String backurl = mJsonObject.getString("msg");
                                         backurl = "*" + IMEI + ",2," + backurl + "#";
                                         final String url = backurl;
                                         new Thread(
                                                 new Runnable() {
                                                     @Override
                                                     public void run() {
                                                         client.send(url);
                                                         // Message message = Message.obtain();
                                                         //  message.what = 1;

                                                         //  message.obj = url;
                                                         //    myHandler.sendMessage(message);
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
                                 cishu = cishu + 1;
                                 if (cishu < 3) {
                                     savePicture(path);
                                 }
                                 super.onError(isFromCache, call, response, e);

                             }
                         }
                );

    }

    public class FileComparator implements Comparator<FileInfo> {
        public int compare(FileInfo file1, FileInfo file2) {
            if (file1.lastModified < file2.lastModified) {
                return -1;
            } else {
                return 1;
            }
        }
    }

    public FileFilter fileFilter = new FileFilter() {
        public boolean accept(File file) {
            String tmp = file.getName().toLowerCase();
            if (tmp.endsWith(".mp4")) {
                return true;
            }
            return false;
        }
    };

    private void DeleteOldVedioFile() {

        try {
            // sharedPreferences.getString("sdcardPath","/mnt/sdcard2");
            float sdFree = FileUtil.getSDAvailableSize(rootPath);
            float sdTotal = FileUtil.getSDTotalSize(rootPath);
            int intSdFree = (int) sdFree;
            if (sdFree < sdTotal * 0.2
                    && intSdFree < 2000) {
                DeleteFile();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }


    }

    private void DeleteFile() {
        File fileroot = new File(DEFAULT_FILE_PATH);
        if (!fileroot.exists()) {
            return;
        }

        //取出文件列表：
        StringBuilder builder = new StringBuilder();
        final File[] files = fileroot.listFiles();
        ArrayList<FileInfo> fileList = new ArrayList<FileInfo>();//将需要的子文件信息存入到FileInfo里面
        for (int i = 0; i < files.length; i++) {
            File file = files[i];
            FileInfo fileInfo = new FileInfo();
            fileInfo.name = file.getName();
            fileInfo.path = file.getAbsolutePath();
            fileInfo.lastModified = file.lastModified();
            fileList.add(fileInfo);
        }
        Collections.sort(fileList, new FileComparator());//通过重写Comparator的实现类FileComparator来
        if (fileList.size() > 40) {
            for (int i = 0; i < 40; i++) {
                File f = new File(fileList.get(i).getPath());
                if (f.exists() && f.isFile()) {
                    f.delete();
                }
            }
        }
    }

    /**
     * 删除最旧视频 (弃用)
     */
  /*  private boolean deleteOldestUnlockVideo() {
        try {

            // sharedPreferences.getString("sdcardPath","/mnt/sdcard2");
            float sdFree = FileUtil.getSDAvailableSize(rootPath);
            float sdTotal = FileUtil.getSDTotalSize(rootPath);
            int intSdFree = (int) sdFree;

            while (sdFree < sdTotal * 0.2
                    && intSdFree < 2000) {
                int oldestUnlockVideoId = videoDb.getOldestUnlockVideoId();
                // 删除较旧未加锁视频文件
                if (oldestUnlockVideoId != -1) {
                    String oldestUnlockVideoName = videoDb
                            .getVideNameById(oldestUnlockVideoId);
                    File f = new File(rootPath + "/vedio"
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
                   /*     File file = new File(rootPath + "/vedio/");
                        sdFree = FileUtil.getSDAvailableSize(rootPath);
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
                        File f = new File(rootPath + "/vedio"
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
                sdFree = FileUtil.getSDAvailableSize(rootPath);
                intSdFree = (int) sdFree;
            }
            return true;
        } catch (Exception e) {
            /*
             * 异常原因：1.文件由用户手动删除
             */
    //    MyLog.e("[MainActivity]deleteOldestUnlockVideo:Catch Exception:"
    //            + e.toString());
   /*         e.printStackTrace();
            return true;
        }
    }*/
    private void InitDeleteShanchu() {

        /*
        删除原图片目录
         */
        File filephoto = new File(rootPath + "/photo/");
        if (!filephoto.exists()) {
            return;
        } else {
            if (filephoto.isDirectory()) {
                for (File f : filephoto.listFiles())
                    f.delete();
            }
        }
        /*
        删除压缩图片目录
         */
        File filephotomini = new File(rootPath + "/photomini/");
        if (!filephotomini.exists()) {
            return;
        } else {
            if (filephotomini.isDirectory()) {
                for (File f : filephotomini.listFiles())
                    f.delete();
            }
        }
    }

    public void IntoXiumian() {
        IsXiumian = true;
        //   cameraSurfaceView.stopRecord();
        cameraSurfaceView.closeCamera();
        mLocationUtil.stopLocate();
        mVideoServer.stop();
        final String intoxiumiantext = "*" + IMEI + ",18,"
                + 0 + "#";
        new Thread(new Runnable() {
            @Override
            public void run() {
                client.send(intoxiumiantext);
            }
        }).start();

    }

    public void OutXiumian() {
        IsXiumian = false;
        mLocationUtil.startLocate();
        cameraSurfaceView.openCamera();
        cameraSurfaceView.startPreview();
        cameraSurfaceView.startRecord();
        try {
            mVideoServer.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
        final String outxiumiantext = "*" + IMEI + ",18,"
                + 1 + "#";
        new Thread(new Runnable() {
            @Override
            public void run() {
                client.send(outxiumiantext);
            }
        }).start();
    }
}

