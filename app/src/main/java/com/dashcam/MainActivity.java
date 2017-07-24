package com.dashcam;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.location.Location;
import android.media.AudioManager;
import android.net.Uri;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
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

public class MainActivity extends AppCompatActivity {
    @Bind(R.id.imei)
    TextView imei;
    @Bind(R.id.text_gps)
    TextView textGps;
    @Bind(R.id.btn_udpClose)
    Button btnUdpClose;//关闭连接
    @Bind(R.id.senddata)
    Button btnSenddata;//发送数据
    @Bind(R.id.btn_udpConn)
    Button btnUdpConn; //建立连接
    @Bind(R.id.txt_Send)
    TextView txtSend;
    @Bind(R.id.textview)
    TextView textview;
    @Bind(R.id.text_state)
    TextView textState;
    @Bind(R.id.btn_gps)
    Button btnGps;
    private GPSLocationManager gpsLocationManager;
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
 //   private Timer timer2 = null;//录制视频定时器
  //  private Timer timer3 = null;//清除TP卡内容定时器
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
  //  private int OpenRecord = 0;//0,开启录像 1,停止录像
    private boolean IsExit=false;
   // private Timer recordtimer;
    private class MyHandler extends Handler {

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case 1:

                    udpSendStrBuf.append("发送的信息：" + msg.obj.toString());
                    txtSend.setText(udpSendStrBuf.toString());
                    break;
                case 2:
                    udpSendStrBuf.append("发送的信息：" + msg.obj.toString());
                    txtSend.setText(udpSendStrBuf.toString());
                    break;
                case 3:
                    udpSendStrBuf.append("接收到信息：" + msg.obj.toString());
                    txtSend.setText(udpSendStrBuf.toString());
                    break;
                case 4:
                  /*  if (!IsRecording && OpenRecord == 0) {
                        cameraSurfaceView.startRecord();
                        IsRecording = true;
                    } else {
                        cameraSurfaceView.stopRecord();
                        if (OpenRecord == 0) {
                            cameraSurfaceView.startRecord();
                        }
                    }*/
                    break;
                case 5:
                    Toast.makeText(MainActivity.this, "存储已满，请手动删除", Toast.LENGTH_SHORT);
                    break;
                case 6:
                    Toast.makeText(MainActivity.this, "存储已满，正在删除加锁视频", Toast.LENGTH_SHORT);
                    break;
                case 7:
                    udpSendStrBuf.append("当前余额：" + msg.obj.toString());
                    txtSend.setText(udpSendStrBuf.toString());
                    break;
                case 8:
                    Toast.makeText(MainActivity.this, msg.obj.toString(), Toast.LENGTH_SHORT);
                    break;
                case 9:
                    cameraSurfaceView.stopRecord();
                    new clearTFCardethread().start();
                    if (!IsExit) {
                        StartRecord();
                    }
                    break;
            }
        }
    }

    private static final String DEFAULT_FILE_PATH = Environment.getExternalStorageDirectory() + "/vedio/";
    private static final int VIDEO_WIDTH = 320;
    private static final int VIDEO_HEIGHT = 240;
    private VideoServer mVideoServer;
    private MyBroadcastReceiver myBroadcastReceiver = new MyBroadcastReceiver();
    private boolean IsRecording = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        EventBus.getDefault().register(this);
        initData();
        initViews();
        context = this;
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
    }

    private void initViews() {
        imei.setText(getid());
        cameraSurfaceView = (CameraSurfaceView) findViewById(R.id.cameraSurfaceView);
        findViewById(R.id.capture).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cameraSurfaceView.capture();
            }
        });
        gpsLocationManager.start(new MyListener());
        ExecutorService exec = Executors.newCachedThreadPool();
        client = new UDPClient();
        exec.execute(client);
        btnUdpClose.setEnabled(true);
        btnSenddata.setEnabled(true);
        if (timer1 == null) {
            timer1 = new Timer();
            timer1.schedule(task, 3000, 30000);
            //  timer.schedule(recordtask, 5000, 1000 * 4 * 3);
            //  timer.schedule(clearTFtask, 13000, 1000 * 60 * 5);
        }

        smsReceiver = new SmsReceiver();
        IntentFilter filter = new IntentFilter("android.provider.Telephony.SMS_RECEIVED");
        filter.setPriority(Integer.MAX_VALUE);
        registerReceiver(smsReceiver, filter);
        mVideoServer = new VideoServer(DEFAULT_FILE_PATH, VIDEO_WIDTH, VIDEO_HEIGHT, VideoServer.DEFAULT_SERVER_PORT);
        ((ToggleButton) findViewById(R.id.record)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(final CompoundButton buttonView, boolean isChecked) {

                if (isChecked) {
                  //  cameraSurfaceView.startRecord();
                    IsExit = false;
                    StartRecord();
                    //设置录制时长为10秒视频
//                    cameraSurfaceView.startRecord(10000, new MediaRecorder.OnInfoListener() {
//                        @Override
//                        public void onInfo(MediaRecorder mr, int what, int extra) {
//                            cameraSurfaceView.stopRecord();
//                            buttonView.setChecked(false);
//                        }
//                    });
                }
                else
                   // StopRecord();
                    IsExit = true;
                    cameraSurfaceView.stopRecord();
              /*  if (isChecked) {
                    if (timer2 == null) {
                        timer2 = new Timer();
                        timer2.schedule(new recordtask(), 1000, 1000 * 60 * 3);
                        //  timer.schedule(recordtask, 5000, 1000 * 4 * 3);
                        //  timer.schedule(clearTFtask, 13000, 1000 * 60 * 5);
                    }
                    if (timer3 == null) {
                        timer3 = new Timer();
                        timer3.schedule(new clearTFtask(), 13000, 1000 * 60 * 5);
                        //  timer.schedule(recordtask, 5000, 1000 * 4 * 3);
                        //  timer.schedule(clearTFtask, 13000, 1000 * 60 * 5);
                    }
                } else {
                    if (timer2 != null) {
                        timer2.cancel();
                        timer2 = null;
                    }
                    if (timer3 != null) {
                        timer3.cancel();
                        timer3 = null;
                    }
                    cameraSurfaceView.stopRecord();
                }*/

            }
        });
        ((ToggleButton) findViewById(R.id.runBack)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                cameraSurfaceView.setRunBack(isChecked);
            }
        });
        ((ToggleButton) findViewById(R.id.switchCamera)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                cameraSurfaceView.setDefaultCamera(!isChecked);
            }
        });
        ((ToggleButton) findViewById(R.id.openwifi)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    setBrightnessMode();

                } else {
                    closeWifiHotspot();
                }

            }
        });
    }

    class MyListener implements GPSLocationListener {

        @Override
        public void UpdateLocation(Location location) {
            if (location != null) {
                textGps.setText(location.getLongitude() + "," + location.getLatitude());
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

    public String getid() {
        TelephonyManager TelephonyMgr = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        String ID = TelephonyMgr.getDeviceId();
        return ID;
    }


    @OnClick({R.id.btn_gps, R.id.testled, R.id.btn_udpConn, R.id.btn_udpClose, R.id.voladd, R.id.voljian,
            R.id.yue, R.id.iplist, R.id.updatewifipassword, R.id.network, R.id.reboot, R.id.senddata})
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_gps:
                //   gpsLocationManager.start(new MyListener());
                break;
            case R.id.btn_udpConn:
                try {
                    mVideoServer.start();
                    //   btnGps.setText("请在远程浏览器中输入:\n\n"+getLocalIpStr(this)+":"+VideoServer.DEFAULT_SERVER_PORT);
                    btnGps.setText("请在远程浏览器中输入:192.168.43.1" + ":" + VideoServer.DEFAULT_SERVER_PORT);
                } catch (IOException e) {
                    e.printStackTrace();
                    Toast.makeText(MainActivity.this, "HTTP服务器开启失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
             /*   ExecutorService exec = Executors.newCachedThreadPool();
                client = new UDPClient();
                exec.execute(client);
                btnUdpClose.setEnabled(true);
                btnSenddata.setEnabled(true);*/
                break;
            case R.id.btn_udpClose:
                client.setUdpLife(false);
                btnUdpConn.setEnabled(true);
                btnUdpClose.setEnabled(false);
                btnSenddata.setEnabled(false);
                break;
            case R.id.senddata:
              /*  Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        Message message = new Message();
                        message.what = 2;
                        if (textGps.getText().toString() != "") {
                            String sendtext="*"+imei.getText().toString().trim()+"1,"
                                    +textGps.getText().toString().trim()+",0,0#";
                            client.send(sendtext);
                            message.obj = sendtext;
                            myHandler.sendMessage(message);
                        }
                    }
                });
                thread.start();*/
                break;
            case R.id.testled:
                cameraSurfaceView.switchLight(true);

                break;
            case R.id.voladd:
                curVolume += stepVolume;
                if (curVolume >= maxVolume) {
                    curVolume = maxVolume;
                }
                // 调整音量
                adjustVolume();
                break;
            case R.id.voljian:
                curVolume -= stepVolume;
                if (curVolume <= 0) {
                    curVolume = 0;
                }
                // 调整音量
                adjustVolume();
                break;
            case R.id.yue:
                FileUtil.sendSMS("10086", "cxye");
                break;
            case R.id.iplist:
                ArrayList<String> connectedIP = getConnectedIP();
                StringBuilder resultList = new StringBuilder();
                for (String ip : connectedIP) {
                    resultList.append(ip);
                    resultList.append(",");
                }
                resultList.substring(0, resultList.length() - 1);
                Toast.makeText(MainActivity.this, resultList, Toast.LENGTH_LONG);
                break;
            case R.id.updatewifipassword:
                closeWifiHotspot();
                SPUtils.put(MainActivity.this, "wifipassword", "789456123");
                createWifiHotspot();
                break;
            case R.id.network:
                FileUtil.getCurrentNetDBM(MainActivity.this);
                break;
            case R.id.reboot:
                PowerManager pManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
                pManager.reboot("重启");
                ;
                break;
        }
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
            if (enable) {
                textview.setText("热点已开启 SSID:" + WIFI_HOTSPOT_SSID + MacUtils.getMacAddr() + " password:123456789");

            } else {
                textview.setText("创建热点失败");
            }
            return enable;
        } catch (Exception e) {
            e.printStackTrace();
            textview.setText("创建热点失败");
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
        textview.setText("热点已关闭");
        textState.setText("wifi已关闭");
    }

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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraSurfaceView.closeCamera();
        mVideoServer.stop();
        task.cancel();
        IsExit = true;
        unregisterReceiver(smsReceiver);
        EventBus.getDefault().unregister(this);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            moveTaskToBack(false);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    TimerTask task = new TimerTask() {
        @Override
        public void run() {
            Message message = new Message();
            message.what = 2;
            //  if (textGps.getText().toString() != "") {
            String sendtext = "*" + imei.getText().toString().trim() + ",1,"
                    + textGps.getText().toString().trim() + ",0,0#";
            client.send(sendtext);
            message.obj = sendtext;
            myHandler.sendMessage(message);
            // }
        }
    };
  /*  class recordtask extends TimerTask {
        @Override
        public void run() {
            if (!IsRecording && OpenRecord == 0) {
                cameraSurfaceView.startRecord();
                IsRecording = true;
            } else {
                cameraSurfaceView.stopRecord();
                if (OpenRecord == 0) {
                    cameraSurfaceView.startRecord();
                }
            }
         /*   Message message = new Message();
            message.what = 4;
            myHandler.sendMessage(message);
        }
    };*/
    class  clearTFCardethread extends Thread {
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
                                         backurl = "*" + imei.getText().toString().trim() + ",2," + backurl + "#";
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
        if (refresh.getYwlx() == 1) {
            savePicture(refresh.getPhotopath());
        } else if (refresh.getYwlx() == 2) {

            String sendtext = "*" + imei.getText().toString().trim() + ",6,"
                    + refresh.getPhotopath() + "#";
            client.send(sendtext);
           /* Message message = Message.obtain();
            message.what = 7;
            message.obj = refresh.getPhotopath();
            myHandler.sendMessage(message);*/
        } else if (refresh.getYwlx() == 3) {
            String sendtext = "*" + imei.getText().toString().trim() + ",8,"
                    + refresh.getPhotopath() + "#";
            client.send(sendtext);
           /* Message message = Message.obtain();
            message.what = 8;
            message.obj = refresh.getPhotopath();
            myHandler.sendMessage(message);*/
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

                    final String sendtext = "*" + imei.getText().toString().trim() + ",3,"
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
                            updatewifetext = "*" + imei.getText().toString().trim() + ",4,"
                                    + 0 + "#";
                        } else {
                            updatewifetext = "*" + imei.getText().toString().trim() + ",4,"
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
                        final String volumetext = "*" + imei.getText().toString().trim() + ",7,"
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

                        final String volumetext = "*" + imei.getText().toString().trim() + ",10,"
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
                        StartRecord();

                   /* if (!cameraSurfaceView.isRecording) {
                        if (timer2 == null) {
                            timer2 = new Timer();
                            timer2.schedule(new recordtask(), 0, 1000 * 60 * 3);
                            //  timer.schedule(recordtask, 5000, 1000 * 4 * 3);
                            //  timer.schedule(clearTFtask, 13000, 1000 * 60 * 5);
                        }
                        if (timer3 == null) {
                            timer3 = new Timer();
                            timer3.schedule(new clearTFtask(), 13000, 1000 * 60 * 5);
                            //  timer.schedule(recordtask, 5000, 1000 * 4 * 3);
                            //  timer.schedule(clearTFtask, 13000, 1000 * 60 * 5);
                        }
                        IsRecording = cameraSurfaceView.isRecording;
                        if (IsRecording) {
                            OpenRecord = 0;
                        }*/
                        IsRecording = cameraSurfaceView.isRecording;
                        new Handler().postDelayed(new Runnable() {
                            public void run() {
                                //execute the task
                                final String opentext = "*" + imei.getText().toString().trim() + ",11," +
                                        +(IsRecording == true ? 0 : 1) + "#";

                                new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        client.send(opentext);
                                    }
                                }).start();
                            }
                        }, 2000);
                        ;
                    } else {
                        recordopentext = "*" + imei.getText().toString().trim() + ",11," +
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
                  //  StopRecord();
                 /*   if (timer2 != null) {
                        timer2.cancel();
                        timer2 = null;
                    }
                    if (timer3 != null) {
                        timer3.cancel();
                        timer3 = null;
                    }
                    cameraSurfaceView.stopRecord();
                    if (!cameraSurfaceView.isRecording) {
                        OpenRecord = 1;
                    }*/
                    IsExit = true;
                    cameraSurfaceView.stopRecord();
                    recordclosetext = "*" + imei.getText().toString().trim() + ",12," +
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
                        String time1= types[2];
                        String time2 = types[3];
                        filenamestext = FileUtil.GetTimeFiles(DEFAULT_FILE_PATH,time1,time2);
                    }
                    filenamestext = "*" + imei.getText().toString().trim() + ",13," +
                            filenamestext + "#";
                    final String  sendfilenamestext = filenamestext;
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            client.send(sendfilenamestext);
                        }
                    }).start();
                    break;
                case "87"://提取录像平台发送  *终端编号,87,文件名#
                    if (types.length == 3) {
                        String filename= types[2];
                        saveFile(DEFAULT_FILE_PATH+filename);

                    }
                    break;

            }

        }
    }

   private void StartRecord(){
       if (!cameraSurfaceView.isRecording) {
           try {
               cameraSurfaceView.startRecord();
               myHandler.postDelayed(new Runnable() {
                   @Override
                   public void run() {
                       myHandler.sendEmptyMessage(9);
                   }
               },3*60*1000);

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
                                         backurl = "*" + imei.getText().toString().trim() + ",14," + backurl + "#";
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
}

