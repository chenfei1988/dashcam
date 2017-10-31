package com.dashcam;

import android.app.AppOpsManager;
import android.app.usage.NetworkStatsManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.Process;
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
import com.baidu.speech.EventListener;
import com.baidu.speech.EventManager;
import com.baidu.speech.EventManagerFactory;
import com.baidu.speech.asr.SpeechConstant;
import com.dashcam.base.ApiInterface;
import com.dashcam.base.DateUtil;
import com.dashcam.base.DateUtils;
import com.dashcam.base.FileInfo;
import com.dashcam.base.FileSUtil;
import com.dashcam.base.HomeListener;
import com.dashcam.base.MyAPP;
import com.dashcam.base.NetworkStatsHelper;
import com.dashcam.base.PhoneInfoUtils;
import com.dashcam.base.RefreshEvent;
import com.dashcam.base.SPUtils;
import com.dashcam.httpservers.VideoServer;
import com.dashcam.location.LocationUtil;
import com.dashcam.photovedio.CameraSurfaceView;
import com.dashcam.photovedio.CheckPermissionsUtil;
import com.dashcam.photovedio.FileUtil;
import com.dashcam.udp.UDPClient;
import com.itgoyo.logtofilelibrary.LogToFileUtils;
import com.lzy.okhttputils.OkHttpUtils;
import com.lzy.okhttputils.callback.StringCallback;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

public class MainActivity extends AppCompatActivity implements BDLocationListener, EventListener {

    @Bind(R.id.liuliang)
    TextView liuliang;
    @Bind(R.id.iszaixian)
    TextView iszaixian;
    private String IMEI = "";
    public static String GPSSTR = ",,,";
    public static String GPSTRZW = "";
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
    private int maxVolume = 10; // 最大音量值
    private int curVolume = 7; // 当前音量值
    private int stepVolume = 0; // 每次调整的音量幅度

    // private boolean IsStopRecord = false;
    MediaPlayer mediaPlayer;
    private SensorManager mSensorManager;
    private Sensor mSensor;
    private String DEFAULT_FILE_PATH = "";
    private VideoServer mVideoServer;
    private final MyBroadcastReceiver myBroadcastReceiver = new MyBroadcastReceiver();
    //   private boolean IsCharge = false;//是否充电
    //   private boolean IsCarStop = false;//超过5分钟静止

    LocationUtil mLocationUtil;
    public static String rootPath = "";//存放视频的路径
    int Batterylevel = 100;//电池电量
    private String phonenumber = "";
    private String G4Itedbm = "0";//4G信号强弱

    public static boolean IsZhualu = false;//是否在抓录视频
    public static boolean IsPengZhuang = false;//是否是碰撞
    int cishu = 0;//上传3次，不成功退出
    int Batterylevelbobao = 20;//电池电量
    private long pengzhuangtimestamp = 0;
    private EventManager wakeup;//语音唤醒
    int zaixianflag = 0;
    private boolean IsTurnClick = false;//是否跳转到工厂模式
    private boolean IsCharging = false; //判断是否充电
    private boolean IsJingzhi = true;
    private int Carmertype = 1;//前后摄像头，1为前置摄像头，2为后置摄像头
    private boolean IsXiumian = false;//是否是休眠状态
    private int ZhuapaiStatus = 1;//1:微信抓拍，2，电源键抓拍 3，碰撞抓拍，4，充电抓拍 5，语音抓拍 6 抓录视频
    public static boolean IsBackCamera = true; //是否是车外镜头
    final ZhuapaiRunnable runnable_zhuai = new ZhuapaiRunnable();
    private Thread mThread ;
    PowerManager powerManager = null;
    PowerManager.WakeLock wakeLock = null;
    // private Timer recordtimer;
    private class MyHandler extends Handler {

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case 9:
                    if (!IsCharging) {
                        IntoXiumian();
                    }
                    break;
                case 10:
                    //   IsStopRecord = false;
                    //   start();
                    if (timer1 == null) {
                        timer1 = new Timer();
                        timer1.schedule(task, 100000, 60000);
                    }
                    if (timer3 == null) {
                        timer3 = new Timer();
                        timer3.schedule(clearTFtask, 3000, 1000 * 60 * 3);
                    }
                    if (DateUtils.IsDay()) {
                        PlayMusic(MainActivity.this, 0);
                    } else {
                        PlayMusic(MainActivity.this, 1);
                    }
                    FileUtil.getCurrentNetDBM(MainActivity.this);
                    LogToFileUtils.write("NETDBM Success");//写入日志
                    registerReceiver(mbatteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                    BindReceiver();
                    LogToFileUtils.write("Receiverbind Success");//写入日志
                    DeleteOldVedioFile();
                    registerHomeListener();
                    LogToFileUtils.write("HomeListen Success");//写入日志
                    if(!IsCharging) {
                        myHandler.postDelayed(new Runnable() {
                            public void run() {
                                if (!IsCharging) {
                                    IntoXiumian();
                                }
                            }
                        }, 60000);
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
        initViews();
        initData();

        //  setBrightnessMode();//开启Wifi
    }

    private void initData() {
        //  CheckPermissionsUtil checkPermissionsUtil = new CheckPermissionsUtil(this);
        //  checkPermissionsUtil.requestAllPermission(this);
        liuliang.setText(0 + "");//C类问题先不管
       /* if (hasPermissionToReadNetworkStats()) {
            NetworkStatsManager networkStatsManager = (NetworkStatsManager) getSystemService(NETWORK_STATS_SERVICE);
            long liangliangbyte = new NetworkStatsHelper(networkStatsManager).getAllMonthMobile(this);
            liuliang.setText((int)(liangliangbyte/1024/1024)+"");
        }*/
        phonenumber = new PhoneInfoUtils(context).getNativePhoneNumber();
        if (phonenumber == null) {
            phonenumber = "";
        }
        rootPath = FileUtil.getStoragePath(this, true);
        if (rootPath == null) {
            rootPath = FileUtil.getStoragePath(context, false);
        }
        DEFAULT_FILE_PATH = rootPath + "/vedio/";
        wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        //videoDb = new DriveVideoDbHelper(this);
        audioMgr = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        //   audioMgr.setParameters("SET_MIC_CHOOSE=2");
        // 获取最大音乐音量
        maxVolume = audioMgr.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        // 初始化音量大概为最大音量的1/2
        curVolume = maxVolume / 2;
        // 每次调整的音量大概为最大音量的1/6
        // stepVolume = maxVolume / 8;
        Calendar mCalendar = Calendar.getInstance();
        //   lastaccelerometertimestamp = mCalendar.getTimeInMillis() / 1000;// 1393844912
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);// TYPE_GRAVITY
        IMEI = getid();
        if (null == mSensorManager) {
            Log.d("dfdfd", "deveice not support SensorManager");
        }
        // 参数三，检测的精准度
   /*     mSensorManager.registerListener(this, mSensor,
                SensorManager.SENSOR_DELAY_NORMAL);// SENSOR_DELAY_GAME*/
        //InitDeleteShanchu();
        powerManager = (PowerManager)this.getSystemService(this.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "screen_on");
        wakeLock.acquire();
     //   wakeLock = this.powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "My Lock");
        myHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                myHandler.sendEmptyMessage(10);
            }
        }, 4000);
    }

    private void initViews() {

        //语音唤醒
        wakeup = EventManagerFactory.create(this, "wp");
        wakeup.registerListener(this); //  EventListener 中 onEvent方法
        //start();
        cameraSurfaceView = (CameraSurfaceView) findViewById(R.id.cameraSurfaceView);
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
                // IntoPengZhuang();
            }
        });
        findViewById(R.id.turnapk).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // OutXiumian();
                IsTurnClick = true;
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.addCategory(Intent.CATEGORY_LAUNCHER);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ComponentName cn = new ComponentName("com.mediatek.factorymode", "com.mediatek.factorymode.FactoryMode");
                intent.setComponent(cn);
                startActivity(intent);
            }
        });

        client = new UDPClient();
       ExecutorService exec = Executors.newCachedThreadPool();
        exec.execute(client);
        LogToFileUtils.write("UDPclientconnect Success");//写入日志
        mLocationUtil = new LocationUtil(this, this);
        mLocationUtil.startLocate();
        LogToFileUtils.write("Gpsopen Success");//写入日志
      /*  smsReceiver = new SmsReceiver();
        IntentFilter filter = new IntentFilter("android.provider.Telephony.SMS_RECEIVED");
        filter.setPriority(Integer.MAX_VALUE);
        registerReceiver(smsReceiver, filter);*/
        //      mVideoServer = new VideoServer(DEFAULT_FILE_PATH);
    }


    @Override
    protected void onResume() {
        super.onResume();

      /*  if (IsTurnClick) {
            if (!cameraSurfaceView.isRecording) {
                cameraSurfaceView.openCamera();
                LogToFileUtils.write("openCamera");//写入日志
                cameraSurfaceView.startPreview();
                cameraSurfaceView.startRecord();
            }
            IsTurnClick = false;
        }*/
    }

    @Override
    protected void onPause() {
        super.onPause();

    /*    if (IsTurnClick) {
            cameraSurfaceView.closeCamera();
        }*/
    }

    TimerTask task = new TimerTask() {
        @Override
        public void run() {
            String sendtext = "*" + IMEI + ",1,"
                    + GPSSTR + "#";
           boolean status =   client.send(sendtext);
            if (!status) {

            }
         //   wakeLock.acquire();
           //client.setUdpLife(true);
        }
    };
    TimerTask clearTFtask = new TimerTask() {
        @Override
        public void run() {
              /*
        给底层发送广播，获取流量
         */
            if (!IsXiumian) {
                Intent intent = new Intent();
                intent.setAction("com.dashcam.intent.REQUEST_DATA_USAGE");
                sendBroadcast(intent);
                DeleteOldVedioFile();
            }
        }
    };


    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void onEvnet(RefreshEvent refresh) {
        if (refresh.getYwlx() == 1) {  //抓拍后上传图片
            savePicture(refresh.getPhotopath());
        } else if (refresh.getYwlx() == 2) { //

            String sendtext = "*" + IMEI + ",6,"
                    + refresh.getPhotopath() + "#";
            client.send(sendtext);
        } else if (refresh.getYwlx() == 3) {   //获取4G信号
            G4Itedbm = refresh.getPhotopath();
        } else if (refresh.getYwlx() == 4) {   //抓录视频
            String vediopath = refresh.getPhotopath();
            saveFile(vediopath, IsZhualu);
            IsZhualu = false;
            //  cameraSurfaceView.stopRecord();
            if (!IsBackCamera) {
                cameraSurfaceView.setDefaultCamera(true);
                IsBackCamera = true;
            }

            if (IsXiumian) {
                cameraSurfaceView.closeCamera();
                LogToFileUtils.write("closeCamera");//写入日志
                //   IntoXiumian();
                Intent intent = new Intent();
                intent.setAction("com.dashcam.intent.TAKE_CAPTURE");
                sendBroadcast(intent);
            } else {
                //   IsStopRecord = false;
                CameraSurfaceView.Recordtime = 60 * 1000 * 3;
                CameraSurfaceView.VIDEO_SIZE = new int[]{1280, 720};
                CameraSurfaceView.mencode = MediaRecorder.VideoEncoder.MPEG_4_SP;
                cameraSurfaceView.startRecord();
            }
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
        IntentFilter intentFilter2 = new IntentFilter("android.intent.GO_SUSPEND");
        IntentFilter intentFilter3 = new IntentFilter(Intent.ACTION_POWER_CONNECTED);
        IntentFilter intentFilter4 = new IntentFilter("rock.intent.CHECK_NEW_SOFTWARE");//有升级消息的通知
        IntentFilter intentFilter5 = new IntentFilter("rock.intent.INSTALL.SUCCESS");//升级成功的广播
        IntentFilter intentFilter6 = new IntentFilter("rock.intent.UPDATE_FAIL");//升级失败的广播
        IntentFilter intentFilter7 = new IntentFilter("android.intent.KEYCODE_F11");//升级失败的广播
        IntentFilter intentFilter8 = new IntentFilter("android.intent.POWER_KEY_SHUTTER");//电源抓拍键
        IntentFilter intentFilter9 = new IntentFilter("com.dashcam.intent.SEND_DATA_USAGE");//获取流量
        IntentFilter intentFilter10 = new IntentFilter(Intent.ACTION_POWER_DISCONNECTED);
        registerReceiver(myBroadcastReceiver, intentFilter1);
        registerReceiver(myBroadcastReceiver, intentFilter2);
        registerReceiver(myBroadcastReceiver, intentFilter3);
        registerReceiver(myBroadcastReceiver, intentFilter4);
        registerReceiver(myBroadcastReceiver, intentFilter5);
        registerReceiver(myBroadcastReceiver, intentFilter6);
        registerReceiver(myBroadcastReceiver, intentFilter7);
        registerReceiver(myBroadcastReceiver, intentFilter8);
        registerReceiver(myBroadcastReceiver, intentFilter9);
        registerReceiver(myBroadcastReceiver, intentFilter10);
        /*
        给底层发送广播，获取流量
         */
        Intent intent = new Intent();
        intent.setAction("com.dashcam.intent.REQUEST_DATA_USAGE");
        sendBroadcast(intent);

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
                case "android.intent.GO_SUSPEND":  //进入休眠
                    IntoXiumian();
                    break;
                case Intent.ACTION_POWER_DISCONNECTED:  //断开电源进入休眠状态
                 LogToFileUtils.write("duankai dian yuan");
                    IsCharging = false;
                    myHandler.postDelayed(new Runnable() {
                        public void run() {
                            if (!IsCharging) {
                                myHandler.sendEmptyMessage(9);
                            }
                        }
                    }, 60000);
                    ;
                    break;
                case Intent.ACTION_POWER_CONNECTED:  //退出休眠
                    LogToFileUtils.write("cha shang dianyuan");
                    IsCharging = true;
                    if (IsXiumian) {
                        OutXiumian();
                        // cameraSurfaceView.capture();
                    }
                    // (0 前置摄像头,1 后置摄像头)

                    break;
                case "rock.intent.CHECK_NEW_SOFTWARE"://有更新的时候
                    final String updatetext = "*" + IMEI + ",110"
                            + "#";
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            client.send(updatetext);
                        }
                    }).start();
                    break;
                case "rock.intent.INSTALL.SUCCESS":
                    final String updatesuccesstext = "*" + IMEI + ",111"
                            + "#";
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            client.send(updatesuccesstext);
                        }
                    }).start();
                    break;
                case "rock.intent.UPDATE_FAIL":
                    final String updatefailedtext = "*" + IMEI + ",112"
                            + "#";
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            client.send(updatefailedtext);
                        }
                    }).start();
                    break;
                case "android.intent.KEYCODE_F11":
                    long currenttime = System.currentTimeMillis();
                    if (currenttime - pengzhuangtimestamp > 5 * 1000) {
                        pengzhuangtimestamp = currenttime;
                        IntoPengZhuang();
                    }
                    break;
                case "android.intent.POWER_KEY_SHUTTER":
                    ZhuapaiStatus = 2;//电源键抓拍
                    new Thread(runnable_zhuai).start();
                    /*if (IsXiumian) {
                        cameraSurfaceView.openCamera();
                        LogToFileUtils.write("openCamera");//写入日志
                        cameraSurfaceView.startPreview();
                        cameraSurfaceView.capture();
                        cameraSurfaceView.closeCamera();
                        LogToFileUtils.write("closeCamera");//写入日志
                    } else {
                        cameraSurfaceView.capture();
                    }*/
                    break;
                case "com.dashcam.intent.SEND_DATA_USAGE":
                    int bit = intent.getIntExtra("data_usage", 0);
                    liuliang.setText(bit + "");
                    final String sendliuliangtext = "*" + IMEI + ",5,"
                            + liuliang.getText().toString().trim() + "#";
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            client.send(sendliuliangtext);
                        }
                    }).start();
                    break;
                default:
                    break;
            }
        }
    }

    private synchronized void GetZhilingType(String msg) {
        //  if (!IsXiumian) {
        LogToFileUtils.write(msg);//写入日志
        //  }
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
                        // (0 前置摄像头,1 后置摄像头)
                        if (lushu.equals("0")) {
                            ZhuapaiStatus = 1;
                            IsBackCamera = true;
                            new Thread(runnable_zhuai).start();
                        } else if (lushu.equals("1")) {
                            ZhuapaiStatus = 1;
                            IsBackCamera = false;
                            new Thread(runnable_zhuai).start();
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
                case "96"://剩余流量  发送广播给底层，由底层来操作
                    Intent intent = new Intent();
                    intent.setAction("com.dashcam.intent.REQUEST_DATA_USAGE");
                    sendBroadcast(intent);
                    break;
                case "95"://费用查询
                    FileUtil.sendSMS("10086", "cxye");
                    break;
                case "94"://音量调节（加减）
                    if (types.length == 3) {
                        curVolume = maxVolume * Integer.parseInt(types[2]) / 100;
                       /* if (curVolume >= maxVolume) {
                            curVolume = maxVolume;
                        }*/
                        SPUtils.put(this, "currentvolume", curVolume);
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
                    MyAPP.ReBoot();
                    LogToFileUtils.write("root Runtime->reboot");
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
                        saveFile(DEFAULT_FILE_PATH + filename, false);
                    }
                    break;
                case "86"://开启WIFI  *终端编号,86#
                    if (FileSUtil.isWifiApOpen(MainActivity.this)) {
                        // closeWifiHotspot();
                        final String startWifitext = "*" + IMEI + ",15," +
                                0 + "#";
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                client.send(startWifitext);
                            }
                        }).start();
                    } else {
                        boolean startwifisuccess = createWifiHotspot("", "");
                        final String startWifitext = "*" + IMEI + ",15," +
                                (startwifisuccess == true ? 0 : 1) + "#";
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                client.send(startWifitext);
                            }
                        }).start();
                    }
                    break;
                case "85"://关闭WIFI  *终端编号,85#
                    if (!FileSUtil.isWifiApOpen(MainActivity.this)) {
                        // closeWifiHotspot();
                        final String startWifitext = "*" + IMEI + ",16," +
                                0 + "#";
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                client.send(startWifitext);
                            }
                        }).start();
                    } else {
                        boolean closewifisuccess = closeWifiHotspot();
                        final String closeWifitext = "*" + IMEI + ",16," +
                                (closewifisuccess == true ? 0 : 1) + "#";
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                client.send(closeWifitext);
                            }
                        }).start();
                    }
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
                            current * 10 + "#";
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
                        // (1前置摄像头,0后置摄像头)
                        if (lushu.equals("0")) {
                            IsBackCamera = true;
                        } else {
                            IsBackCamera = false;
                        }
                        ZhuapaiStatus = 6;
                        new Thread(runnable_zhuai).start();
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
                            int currentvolume = audioMgr.getStreamVolume(AudioManager.STREAM_MUSIC);
                            SPUtils.put(this, "currentvolume", currentvolume);
                            curVolume = 0;
                            adjustVolume();
                        } else {
                            int currentvolume = (int) SPUtils.get(this, "currentvolume", 0);
                            curVolume = currentvolume;
                            adjustVolume();
                        }
                        final String volumeswitch = "*" + IMEI + ",25," +
                                "#";
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                client.send(volumeswitch);
                            }
                        }).start();
                    }
                    break;
                case "76": //WINFI状态查询
                    //  APK回复　*终端编号, 23, WINFI开关(0开, 1关), WIFI用户名, WIFI密码#
                    String wifiapopen = "0";
                    if (FileSUtil.isWifiApOpen(MainActivity.this)) {
                        wifiapopen = "1";
                    }
                    String nameandpassword = FileSUtil.Getapwifinameandpassword(MainActivity.this);
                    final String wifiapstate = "*" + IMEI + ",25," +
                            wifiapopen + "," + nameandpassword + "#";
                    LogToFileUtils.write(wifiapstate);//写入日志
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            client.send(wifiapstate);
                        }
                    }).start();
                    break;
                case "75": //WINFI状态查询
                    //  APK回复　*终端编号, 23, WINFI开关(0开, 1关), WIFI用户名, WIFI密码#
                    int currentvolue = audioMgr.getStreamVolume(AudioManager.STREAM_MUSIC);
                    final String volumestate = "*" + IMEI + ",26," +
                            (currentvolue > 0 ? 1 : 0) + "," + (int) (currentvolue * 100 / maxVolume) + "#";
                    LogToFileUtils.write(volumestate);//写入日志
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            client.send(volumestate);
                        }
                    }).start();
                    break;
                case "74":
                     MyAPP.ShutDown();
                    break;
                default:
                    break;

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
                    try {
                        cameraSurfaceView.startRecord();//开始录像
                        Thread.sleep(1000);
                        cameraSurfaceView.capture();
                    } catch (Exception e) {
                        LogToFileUtils.write("kaiji zhuapai noxiumian houzhi failed" + e.toString());//写入日志
                    }
                    //   ZhuapaiStatus =5;//开机抓拍
                    //   new Thread(runnable_zhuai).start();
                    // cameraSurfaceView.capture();
                }
            }
        });
    }


    /*
    充电状态获取
     */
    private BroadcastReceiver mbatteryReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            int temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
            if (temperature == 630) {
               /* final String xiumiantext = "*" + IMEI + ",24,"
                        + 3 + "#";
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        client.send(xiumiantext);
                    }
                }).start();*/
                LogToFileUtils.write("root Runtime->shutdown");
                //Process proc =Runtime.getRuntime().exec(new String[]{"su","-c","shutdown"});  //关机
                 MyAPP.ShutDown();
            /*    PowerManager pManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
                pManager.reboot("重启");*/
            }
            if (temperature == 600) {
                final String xiumiantext = "*" + IMEI + ",24,"
                        + 3 + "#";
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        client.send(xiumiantext);
                    }
                }).start();
            }
            if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
                int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
              //  int status = intent.getIntExtra("status", BatteryManager.BATTERY_STATUS_UNKNOWN);
                // 电池当前的电量, 它介于0和 EXTRA_SCALE之间
                Batterylevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                // 电池电量的最大值
                if(status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL){
                    IsCharging = true;
                    LogToFileUtils.write("now is charting");
                }
                else  {
                    IsCharging = false;
                    LogToFileUtils.write("now is nocharting");
                    if (Batterylevel == Batterylevelbobao) {
                        if (Batterylevel == 20) {
                            Batterylevelbobao = 10;
                            final String xiumiantext = "*" + IMEI + ",24,"
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
                        } else if (Batterylevel == 10) {
                            Batterylevelbobao = 1;
                            final String xiumiantext = "*" + IMEI + ",24,"
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
                   /*     } else if (Batterylevelbobao == 2) {
                            Batterylevelbobao = 1;
                            final String xiumiantext = "*" + IMEI + ",24,"
                                    + 2 + "#";
                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    client.send(xiumiantext);
                                }
                            }).start();*/
                        } else if (Batterylevelbobao == 1) {
                            Batterylevelbobao = 0;
                            final String xiumiantext = "*" + IMEI + ",24,"
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


   /* @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }*/

    @Override
    protected void onDestroy() {
        super.onDestroy();
        wakeLock.release();
        cameraSurfaceView.closeCamera();
        LogToFileUtils.write("closeCamera");//写入日志
        // mVideoServer.stop();
        //  task.cancel();
        Intent intent = new Intent();
        intent.setAction("com.dashcam.intent.STOP_RECORD");
        sendBroadcast(intent);
        wakeup.send(SpeechConstant.WAKEUP_STOP, "{}", null, 0, 0);
        clearTFtask.cancel();
        task.cancel();
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
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            this.finish();
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
                        LogToFileUtils.write("common reply:"+url);
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
        } else {
            wifiManager.setWifiEnabled(true);
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


    /**
     * 测试参数填在这里
     */
    private void start() {

        Map<String, Object> params = new LinkedHashMap<String, Object>();
        params.put(SpeechConstant.ACCEPT_AUDIO_VOLUME, false);
        params.put(SpeechConstant.WP_WORDS_FILE, "assets:///aidemo");
        String json = null; // 这里可以替换成你需要测试的json
        json = new JSONObject(params).toString();
        wakeup.send(SpeechConstant.WAKEUP_START, json, null, 0, 0);
    }

    /**
     * 注册Home键的监听
     */
    private void registerHomeListener() {
        HomeListener mHomeWatcher = new HomeListener(this);
        mHomeWatcher.setOnHomePressedListener(new HomeListener.OnHomePressedListener() {

            @Override
            public void onHomePressed() {
                //TODO 进行点击Home键的处理
                Log.i("xsl", "0000000000000");
                MainActivity.this.finish();
            }

            @Override
            public void onHomeLongPressed() {
                //TODO 进行长按Home键的处理
                Log.i("xsl", "0000000000000");
            }
        });
        mHomeWatcher.startWatch();
    }


    @Override
    public void onReceiveLocation(BDLocation location) {
        if (location != null) {
            if (location.getLongitude() > 1 && location.getLatitude() > 1) {
                if (location.getLocType() == BDLocation.TypeGpsLocation) {
                    GPSSTR = location.getLongitude() + "," + location.getLatitude() + "," + location.getSpeed() + "," + location.getDirection() + ",1";
                    GPSTRZW = "地址：" + location.getAddrStr() + "车速：" + location.getSpeed() + "km/h" + "方向：" + GetDirection(location.getDirection()) + "定位方式：GPS";

                } else {
                    GPSSTR = location.getLongitude() + "," + location.getLatitude() + "," + location.getSpeed() + "," + location.getDirection() + ",0";
                    GPSTRZW = "地址：" + location.getAddrStr() + "车速：" + location.getSpeed() + "km/h" + "方向：" + GetDirection(location.getDirection()) + "定位方式：非GPS";
                }
                if (location.getSpeed() > 0) {
                    IsJingzhi = false;
                } else {
                    IsJingzhi = true;
                }
                LogToFileUtils.write(GPSSTR);//写入日志
            }
            String sendtext = "*" + IMEI + ",1,"
                    + GPSSTR + "#";
            if (!client.send(sendtext)) {
                zaixianflag = zaixianflag + 1;
                if (zaixianflag > 8) {
                    iszaixian.setText("离线");
                }
            } else {
                iszaixian.setText("在线");
                zaixianflag = 0;
            }


            //  textGps.setText(location.getLongitude() + "," + location.getLatitude()+","+location.getSpeed()+","+location.getBearing());
        }
    }

    @Override
    public void onConnectHotSpotMessage(String s, int i) {

    }


    //   EventListener  回调方法
    @Override
    public void onEvent(String name, String params, byte[] data, int offset, int length) {
        // String logTxt = "name: " + name;
        if (params != null && !params.isEmpty()) {
            try {
                JSONObject jsonObject = new JSONObject(params);
                String word = jsonObject.getString("word");
                if (word.equals("拍照")) {

                    if (IsXiumian == true) {
                        cameraSurfaceView.openCamera();
                        LogToFileUtils.write("openCamera");//写入日志
                        cameraSurfaceView.startPreview();
                    }
                    // (0 前置摄像头,1 后置摄像头)
                    cameraSurfaceView.capture();
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
            //  logTxt += " ;params :" + params;
        } else if (data != null) {
            //  logTxt += " ;data length=" + data.length;
        }

    }


    private String GetDirection(float fangxiangInteger) {

        if (fangxiangInteger > 0 && fangxiangInteger < 90) {
            return "东北";
        } else if (fangxiangInteger == 90) {
            return "正北";

        } else if (fangxiangInteger > 90 && fangxiangInteger < 180) {
            return "西北";
        } else if (fangxiangInteger == 180) {
            return "正西";
        } else if (fangxiangInteger > 180 && fangxiangInteger < 270) {
            return "西南";
        } else if (fangxiangInteger == 270) {
            return "正南";
        } else if (fangxiangInteger > 270 && fangxiangInteger < 360) {
            return "东南";
        } else if (fangxiangInteger == 360) {
            return "正东";
        } else {
            return "方向获取失败";
        }
    }

    public void saveFile(final String path, final boolean IsZhualu) {
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
                                     LogToFileUtils.write(s);//写入日志
                                     JSONObject mJsonObject = new JSONObject(s);
                                     if (mJsonObject.getBoolean("success")) {
                                         cishu = 0;
                                         String backurl = mJsonObject.getString("msg");
                                         //    backurl = backurl.substring(0, backurl.length() - 1);
                                         if (IsZhualu) {
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
                                     LogToFileUtils.write(e.toString());//写入日志
                                     e.printStackTrace();
                                 }
                             }

                             @Override
                             public void onError(boolean isFromCache, Call call, @Nullable Response
                                     response, @Nullable Exception e) {
                                 LogToFileUtils.write(e.toString());//写入日志
                                 cishu = cishu + 1;
                                 if (cishu < 3) {
                                     saveFile(path, IsZhualu);
                                 }
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
                                     LogToFileUtils.write(s);//写入日志
                                     JSONObject mJsonObject = new JSONObject(s);
                                     if (mJsonObject.getBoolean("success")) {
                                         cishu = 0;
                                         deleteFile(new File(path));
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
                                 LogToFileUtils.write(e.toString());//写入日志
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
        if (fileList.size() > 8) {
            for (int i = 0; i < 8; i++) {
                File f = new File(fileList.get(i).getPath());
                if (f.exists() && f.isFile()) {
                    f.delete();
                }
            }
        }
        float sdFree = FileUtil.getSDAvailableSize(rootPath);
        float sdTotal = FileUtil.getSDTotalSize(rootPath);
        int intSdFree = (int) sdFree;
        if (sdFree < sdTotal * 0.2
                && intSdFree < 2000) {
            DeleteFile();
        }
    }

    public void IntoXiumian() {
        ZhuapaiStatus = 7;
        new Thread(runnable_zhuai).start();
    }

    public void OutXiumian() {
        ZhuapaiStatus = 8;//碰撞抓拍
        new Thread(runnable_zhuai).start();
    }

    private void IntoPengZhuang() {
        IsPengZhuang = true;
        if (IsXiumian) {
            ZhuapaiStatus = 3;//碰撞抓拍
            new Thread(runnable_zhuai).start();
           /* cameraSurfaceView.openCamera();
            LogToFileUtils.write("openCamera");//写入日志
            cameraSurfaceView.startPreview();
            cameraSurfaceView.capture();*/

        } else {
            ZhuapaiStatus = 9;//碰撞抓拍
            new Thread(runnable_zhuai).start();
            IsPengZhuang = false;
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
     * 删除单个文件
     *
     * @param
     * @return 单个文件删除成功返回true，否则返回false
     */
    public boolean deleteFile(File file) {
        // 如果文件路径所对应的文件存在，并且是一个文件，则直接删除
        if (file.exists() && file.isFile()) {
            if (file.delete()) {

                return true;
            } else {

                return false;
            }
        } else {
            return false;
        }
    }

    public class ZhuapaiRunnable implements Runnable {
        public void run() {
            synchronized (this) {
                int zhuapaistatus = ZhuapaiStatus;
                boolean isbackcamera = IsBackCamera;
                boolean isxiumian = IsXiumian;
                switch (zhuapaistatus) {
                    case 1: //微信抓拍
                        if (isxiumian) {
                            if (isbackcamera) {
                                try {
                                    cameraSurfaceView.openCamera();
                                    Thread.sleep(1200);
                                    cameraSurfaceView.startPreview();
                                    Thread.sleep(1200);
                                    cameraSurfaceView.capture();
                                    Thread.sleep(1500);
                                    cameraSurfaceView.closeCamera();
                                    Intent intent = new Intent();
                                    intent.setAction("com.dashcam.intent.TAKE_CAPTURE");
                                    LogToFileUtils.write("guangbo send" );//写入日志
                                    sendBroadcast(intent);
                                } catch (Exception e) {
                                    LogToFileUtils.write("weixin zhuapai xiumian houzhi failed" + e.toString());//写入日志
                                }
                            } else {
                                try {
                                    cameraSurfaceView.openCamera();
                                    Thread.sleep(1200);
                                    cameraSurfaceView.startPreview();
                                    Thread.sleep(1200);
                                    cameraSurfaceView.setDefaultCamera(false);
                                    Thread.sleep(1300);
                                    cameraSurfaceView.capture();
                                    Thread.sleep(1600);
                                    cameraSurfaceView.setDefaultCamera(true);
                                    Thread.sleep(1200);
                                    IsBackCamera = true;
                                    cameraSurfaceView.closeCamera();
                                    Intent intent = new Intent();
                                    intent.setAction("com.dashcam.intent.TAKE_CAPTURE");
                                    LogToFileUtils.write("guangbo send" );//写入日志
                                    sendBroadcast(intent);
                                } catch (Exception e) {
                                    LogToFileUtils.write("weixin zhuapai xiumian qianzhi failed" + e.toString());//写入日志
                                }
                            }
                        } else {
                            if (isbackcamera) {
                                try {
                                    cameraSurfaceView.capture();
                                } catch (Exception e) {
                                    LogToFileUtils.write("weixin zhuapai noxiumian houzhi failed" + e.toString());//写入日志
                                }
                            } else {
                                try {
                                    cameraSurfaceView.stopRecord();
                                    Thread.sleep(1200);
                                    cameraSurfaceView.setDefaultCamera(false);
                                    Thread.sleep(1300);
                                    cameraSurfaceView.capture();
                                    Thread.sleep(1600);
                                    cameraSurfaceView.setDefaultCamera(true);
                                    Thread.sleep(1200);
                                    cameraSurfaceView.startRecord();

                                } catch (Exception e) {
                                    LogToFileUtils.write("weixin zhuapai noxiumian qianzhi failed" + e.toString());//写入日志
                                }
                            }
                        }
                        break;
                    case 2: //电源键抓拍
                        if (isxiumian) {
                            try {
                                cameraSurfaceView.openCamera();
                                Thread.sleep(1200);
                                cameraSurfaceView.startPreview();
                                Thread.sleep(1200);
                                cameraSurfaceView.capture();
                                Thread.sleep(1500);
                                cameraSurfaceView.closeCamera();
                                Intent intent = new Intent();
                                intent.setAction("com.dashcam.intent.TAKE_CAPTURE");
                                LogToFileUtils.write("guangbo send" );//写入日志
                                sendBroadcast(intent);
                            } catch (Exception e) {
                                LogToFileUtils.write("weixin zhuapai xiumian houzhi failed" + e.toString());//写入日志
                            }
                        } else {
                            try {
                                cameraSurfaceView.capture();
                            } catch (Exception e) {
                                LogToFileUtils.write("weixin zhuapai noxiumian houzhi failed" + e.toString());//写入日志
                            }

                        }
                        break;
                    case 3: //碰撞抓拍
                        try {
                            cameraSurfaceView.openCamera();
                            Thread.sleep(1200);
                            cameraSurfaceView.startPreview();
                            Thread.sleep(1300);
                            cameraSurfaceView.capture();
                          /*  Thread.sleep(100);
                            cameraSurfaceView.setDefaultCamera(false);
                            Thread.sleep(100);
                            cameraSurfaceView.capture();*/
                            Thread.sleep(1500);
                            cameraSurfaceView.closeCamera();
                            Intent intent = new Intent();
                            intent.setAction("com.dashcam.intent.TAKE_CAPTURE");
                            sendBroadcast(intent);
                            LogToFileUtils.write("guangbo send" );//写入日志
                            LogToFileUtils.write("intopengzhuangzhupai");//写入日志
                        } catch (Exception e) {
                            LogToFileUtils.write("pengzhuang zhuapai failed" + e.toString());//写入日志
                        }
                        break;
                    case 4: //语音抓拍
                        if (isxiumian) {
                            if (isbackcamera) {
                                try {
                                    cameraSurfaceView.openCamera();
                                    Thread.sleep(1200);
                                    cameraSurfaceView.startPreview();
                                    Thread.sleep(1200);
                                    cameraSurfaceView.capture();
                                    Thread.sleep(1500);
                                    cameraSurfaceView.closeCamera();
                                    Intent intent = new Intent();
                                    intent.setAction("com.dashcam.intent.TAKE_CAPTURE");
                                    LogToFileUtils.write("guangbo send" );//写入日志
                                    sendBroadcast(intent);
                                } catch (Exception e) {
                                    LogToFileUtils.write("weixin zhuapai xiumian houzhi failed" + e.toString());//写入日志
                                }
                            }
                        } else {
                            if (isbackcamera) {
                                try {
                                    cameraSurfaceView.capture();
                                } catch (Exception e) {
                                    LogToFileUtils.write("weixin zhuapai noxiumian houzhi failed" + e.toString());//写入日志
                                }
                            }
                        }
                        break;
                    case 5: //充电抓拍
                        if (isxiumian) {
                            if (isbackcamera) {
                                try {
                                    cameraSurfaceView.openCamera();
                                    Thread.sleep(1200);
                                    cameraSurfaceView.startPreview();
                                    Thread.sleep(1200);
                                    cameraSurfaceView.capture();
                                    Thread.sleep(1500);
                                    cameraSurfaceView.closeCamera();
                                } catch (Exception e) {
                                    LogToFileUtils.write("kaiji zhuapai xiumian houzhi failed" + e.toString());//写入日志
                                }
                            }
                        } else {
                            if (isbackcamera) {
                                try {
                                    cameraSurfaceView.capture();
                                } catch (Exception e) {
                                    LogToFileUtils.write("kaiji zhuapai noxiumian houzhi failed" + e.toString());//写入日志
                                }
                            }
                        }
                        break;
                    case 6: //抓录视频
                        if (isxiumian) {
                            try {
                                cameraSurfaceView.openCamera();
                                Thread.sleep(1200);
                                LogToFileUtils.write("openCamera");//写入日志
                                cameraSurfaceView.startPreview();
                                Thread.sleep(1200);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }

                        } else {
                            cameraSurfaceView.stopRecord();
                        }
                        IsZhualu = true;
                        CameraSurfaceView.Recordtime = 1000 * 6;
                        CameraSurfaceView.VIDEO_SIZE = new int[]{640, 480};
                        CameraSurfaceView.mencode = MediaRecorder.VideoEncoder.H264;
                        if (!isbackcamera) {
                            try {
                                cameraSurfaceView.setDefaultCamera(false);
                                IsBackCamera = false;
                            } catch (Exception e) {
                            }
                        }
                        cameraSurfaceView.startRecord();
                        break;
                    case 7: //进入休眠
                        if (!IsXiumian) {
                            //   cameraSurfaceView.stopRecord();
                            cameraSurfaceView.closeCamera();
                            LogToFileUtils.write("closeCamera");//写入日志
                            mLocationUtil.stopLocate();
                            // mVideoServer.stop();
                            final String intoxiumiantext = "*" + IMEI + ",18,"
                                    + 0 + "#";
                            client.send(intoxiumiantext);
                            IsXiumian = true;
                            LogToFileUtils.write("intoxiumian");//写入日志
                            //  closeWifiHotspot();
                        }
                        break;
                    case 8: //退出休眠
                        if (IsXiumian) {
                            if (!cameraSurfaceView.isRecording) {
                                try {
                                    cameraSurfaceView.openCamera();
                                    LogToFileUtils.write("openCamera");//写入日志
                                    Thread.sleep(100);
                                    cameraSurfaceView.startPreview();
                                    Thread.sleep(100);
                                    cameraSurfaceView.startRecord();
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                            mLocationUtil.startLocate();
                            String outxiumiantext = "*" + IMEI + ",18,"
                                    + 1 + "#";
                            client.send(outxiumiantext);
                            LogToFileUtils.write("outxiumian");//写入日志
                            IsXiumian = false;
                            //createWifiHotspot("", "");
                        }
                        break;
                    case 9://碰撞移动视频
                        final long currenttime = System.currentTimeMillis();
                        try {
                            cameraSurfaceView.stopRecord();
                            Thread.sleep(200);
                            cameraSurfaceView.startRecord();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        Timer timer = new Timer();//实例化Timer类
                        timer.schedule(new TimerTask() {
                            public void run() {
                                FileUtil.MoveFiletoDangerFile(currenttime, rootPath);
                                this.cancel();
                            }
                        }, 3000);//3秒
                        LogToFileUtils.write("intopengzhuangshipin");//写入日志
                        break;
                    default:
                        break;

                }
            }
        }
    }
    public void Testbaidu() {

        OkHttpUtils.get("https://www.baidu.com/")     // 请求方式和请求url
                .tag(this)
                // 请求的 tag, 主要用于取消对应的请求
                .execute(new StringCallback() {
                             @Override
                             public void onResponse(boolean isFromCache, String s, Request
                                     request, @Nullable Response response) {

                             }

                             @Override
                             public void onError(boolean isFromCache, Call call, @Nullable Response
                                     response, @Nullable Exception e) {

                             }
                         }
                );

    }
}

