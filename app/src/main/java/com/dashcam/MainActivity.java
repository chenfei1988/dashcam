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
import android.widget.TextView;
import android.widget.Toast;

import com.baidu.location.BDLocation;
import com.baidu.location.BDLocationListener;
import com.dashcam.base.ApiInterface;
import com.dashcam.base.DateUtils;
import com.dashcam.base.MacUtils;
import com.dashcam.base.MyAPP;
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
import okhttp3.Call;
import okhttp3.Request;
import okhttp3.Response;

import static com.dashcam.photovedio.FileUtil.getConnectedIP;

public class MainActivity extends AppCompatActivity implements SensorEventListener, BDLocationListener {

    @Bind(R.id.liuliang)
    TextView liuliang;
    // private GPSLocationManager gpsLocationManager;
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
    private static final String WIFI_HOTSPOT_SSID = "XiaoMa-";
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
    private String DEFAULT_FILE_PATH = "";
    private VideoServer mVideoServer;
    private MyBroadcastReceiver myBroadcastReceiver = new MyBroadcastReceiver();
    private boolean IsCharge = false;//是否充电
    private boolean IsCarStop = false;//超过5分钟静止
    private boolean IsXiumian = false;//是否是休眠状态
    LocationUtil mLocationUtil;
    private String rootPath = "";//存放视频的路径
    int Batterylevel = 100;//电池电量
    private String phonenumber = "";
    private String G4Itedbm = "";//4G信号强弱
    private boolean IsBackCamera = true;
    public static boolean IsZhualu = false;//是否在抓录视频
    private boolean IsFirstXiumian = false;

    @Override
    public void onReceiveLocation(BDLocation location) {
        if (location != null) {
            GPSSTR = location.getLongitude() + "," + location.getLatitude() + "," + location.getSpeed() + "," + location.getDirection();
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
                case 1:
                    break;
                case 2:
                    Calendar mCalendar = Calendar.getInstance();
                    long timestamp = mCalendar.getTimeInMillis() / 1000;// 1393844912
                    if (timestamp - lastaccelerometertimestamp > 100) {
                        IsCarStop = true;
                    } else {
                        IsCarStop = false;
                    }
                    if (IsCarStop == true && IsCharge == false) {
                        if (!IsFirstXiumian) {
                            IsFirstXiumian = true;
                            IsXiumian = true;
                            IsStopRecord = true;
                            cameraSurfaceView.stopRecord();
                            PlayMusic(MainActivity.this, 2);
                            final String xiumiantext = "*" + IMEI + ",18,"
                                    + 0 + "#";
                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    client.send(xiumiantext);
                                }
                            }).start();
                        }
                    } else {
                        IsXiumian = false;
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
                    if (!IsStopRecord && IsBackCamera&&!IsZhualu) { //不停止录像并且是后置摄像头，因为抓录前置摄像头时只用录像一次
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
        context = this;
        initData();
        initViews();
        registerReceiver(mbatteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        BindReceiver();
        //  setBrightnessMode();//开启Wifi

    }

    private void initData() {
        CheckPermissionsUtil checkPermissionsUtil = new CheckPermissionsUtil(this);
        checkPermissionsUtil.requestAllPermission(this);
        if (hasPermissionToReadNetworkStats()) {
            NetworkStatsManager networkStatsManager = (NetworkStatsManager) getSystemService(NETWORK_STATS_SERVICE);
            NetworkStats.Bucket bucket = null;
            // 获取到目前为止设备的Wi-Fi流量统计
            try {
                bucket = networkStatsManager.querySummaryForDevice(ConnectivityManager.TYPE_WIFI, "", 0, System.currentTimeMillis());
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            liuliang.setText(bucket.getRxBytes() + bucket.getTxBytes() + "B");
        }
        phonenumber = new PhoneInfoUtils(context).getNativePhoneNumber();
        FileUtil.getCurrentNetDBM(MainActivity.this);
        rootPath = FileUtil.getStoragePath(this, true);
        if (rootPath == null) {
            rootPath = FileUtil.getStoragePath(context, false);
        }
        DEFAULT_FILE_PATH = rootPath + "/vedio/";
        mLocationUtil = new LocationUtil(this, this);
        mLocationUtil.startLocate();
        wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        videoDb = new DriveVideoDbHelper(this);
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
        myHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                myHandler.sendEmptyMessage(10);
            }
        }, 4000);
    }

    private void initViews() {

        cameraSurfaceView = (CameraSurfaceView) findViewById(R.id.cameraSurfaceView);
        //cameraSurfaceView.setVisibility(View.GONE);
        //   gpsLocationManager.start(new MyListener());
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
            deleteOldestUnlockVideo();
        }
    };


    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void onEvnet(RefreshEvent refresh) {
        if (refresh.getYwlx() == 1) {  //上传图片
            savePicture(refresh.getPhotopath());
            if (!IsBackCamera) {
                cameraSurfaceView.setDefaultCamera(true);
                IsBackCamera = true;
                IsStopRecord = false;
                StartRecord();
            }
        } else if (refresh.getYwlx() == 2) { //获取余额

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
                cameraSurfaceView.setDefaultCamera(true);
                IsBackCamera = true;
                IsStopRecord = false;
                StartRecord();
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
        IntentFilter intentFilter = new IntentFilter("udpRcvMsg");
        registerReceiver(myBroadcastReceiver, intentFilter);
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
                    if (types.length == 3) {
                        String lushu = types[2];
                        // (0 前置摄像头,1 后置摄像头)
                        if (lushu.equals("1")) {
                            cameraSurfaceView.capture();
                        } else if (lushu.equals("0")) {
                            IsStopRecord = true;
                            cameraSurfaceView.stopRecord();
                            cameraSurfaceView.setDefaultCamera(false);
                            IsBackCamera = false;
                            cameraSurfaceView.capture();

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
                case "86"://开启WIFI  *终端编号,86#
                    closeWifiHotspot();
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

                        if (lushu.equals("1")) {
                            IsZhualu = true;
                            //  cameraSurfaceView.capture();
                        } else if (lushu.equals("0")) {
                            IsStopRecord = true;
                            cameraSurfaceView.stopRecord();
                            cameraSurfaceView.setDefaultCamera(false);
                            IsBackCamera = false;
                            IsZhualu = true;
                            StartRecord();

                        }
                    }
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
                if (type == 2) {
                } else {
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
            if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
                int status = intent.getIntExtra("status", BatteryManager.BATTERY_STATUS_UNKNOWN);
                // 电池当前的电量, 它介于0和 EXTRA_SCALE之间
                Batterylevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                // 电池电量的最大值
                if (status == BatteryManager.BATTERY_STATUS_CHARGING) {
                    IsCharge = true;
                    if (IsXiumian == true) {
                        IsFirstXiumian = false;
                        IsCarStop = false;
                        IsXiumian = false;
                        final String xiumiantext = "*" + IMEI + ",18,"
                                + 1 + "#";
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                client.send(xiumiantext);
                            }
                        }).start();
                        if (IsStopRecord == true) {
                            IsStopRecord = false;
                            StartRecord();
                        }
                    }
                    //唤醒休眠
                    Toast.makeText(MainActivity.this, "正在充电", Toast.LENGTH_LONG);
                } else {
                    IsCharge = false;
                    Toast.makeText(MainActivity.this, "停止充电", Toast.LENGTH_LONG);
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
            if (maxvalue > 3) {
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
                    if (IsStopRecord == true) {
                        IsStopRecord = false;
                        StartRecord();
                    }
                }
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
      /*  if (keyCode == KeyEvent.KEYCODE_BACK) {
            moveTaskToBack(false);
            return true;
        }*/
       /* if(keyCode == KeyEvent.KEYCODE_POWER){
            cameraSurfaceView.capture();
            cameraSurfaceView.capture();
            cameraSurfaceView.capture();
            cameraSurfaceView.capture();
            cameraSurfaceView.capture();
            cameraSurfaceView.setDefaultCamera(false);
            IsBackCamera = false;
            cameraSurfaceView.capture();
            cameraSurfaceView.capture();
            cameraSurfaceView.capture();
            cameraSurfaceView.capture();
            cameraSurfaceView.capture();
            cameraSurfaceView.setDefaultCamera(true);
            IsBackCamera = true;
            return true;
        }*/
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
        WifiConfiguration config = new WifiConfiguration();
        if (name.equals("")) {
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

        //   requestReadNetworkStats();
        return false;
    }

    // 打开“有权查看使用情况的应用”页面
    private void requestReadNetworkStats() {
        Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
        startActivity(intent);
    }

    /**
     * 删除最旧视频
     */
    private boolean deleteOldestUnlockVideo() {
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
                        File file = new File(rootPath + "/vedio/");
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
                                 String backurl = "";
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
                                                 myHandler.sendMessage(message);*/
                                             }
                                         }
                                 ).start();
                                 super.onError(isFromCache, call, response, e);

                             }
                         }
                );

    }

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
                                 super.onError(isFromCache, call, response, e);

                             }
                         }
                );

    }

}

