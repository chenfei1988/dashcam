package com.dashcam;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.net.Uri;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.telephony.TelephonyManager;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.dashcam.location.GPSLocationListener;
import com.dashcam.location.GPSLocationManager;
import com.dashcam.location.GPSProviderStatus;
import com.dashcam.photovedio.CameraSurfaceView;
import com.dashcam.photovedio.CheckPermissionsUtil;
import com.dashcam.udp.UDPClient;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends AppCompatActivity  {
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
    private GPSLocationManager gpsLocationManager;
    private UDPClient client = null;
    public static Context context;
    private final MyHandler myHandler = new MyHandler();
    private StringBuffer udpSendStrBuf = new StringBuffer();
    private CameraSurfaceView cameraSurfaceView;
    private WifiManager wifiManager;
    /**
     * 热点名称
     */
    private static final String WIFI_HOTSPOT_SSID = "TEST";

    private class MyHandler extends Handler {

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case 2:
                    udpSendStrBuf.append(msg.obj.toString());
                    txtSend.setText(udpSendStrBuf.toString());
                    break;
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        initData();
        initViews();
        context = this;

    }

    private void initData() {
        CheckPermissionsUtil checkPermissionsUtil = new CheckPermissionsUtil(this);
        checkPermissionsUtil.requestAllPermission(this);
        gpsLocationManager = GPSLocationManager.getInstances(MainActivity.this);
        wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
    }

    private void initViews() {
        imei.setText("(设备唯一串口)IMEI:" + getid());
        cameraSurfaceView = (CameraSurfaceView) findViewById(R.id.cameraSurfaceView);
        findViewById(R.id.capture).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cameraSurfaceView.capture();
            }
        });
        ((ToggleButton) findViewById(R.id.record)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(final CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    cameraSurfaceView.startRecord();
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
                    cameraSurfaceView.stopRecord();
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
                textGps.setText("经度：" + location.getLongitude() + "\n纬度：" + location.getLatitude());
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


    @OnClick({R.id.btn_gps, R.id.btn_udpConn, R.id.btn_udpClose, R.id.senddata})
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_gps:
                gpsLocationManager.start(new MyListener());
                break;
            case R.id.btn_udpConn:
                ExecutorService exec = Executors.newCachedThreadPool();
                client = new UDPClient();
                exec.execute(client);
                btnUdpClose.setEnabled(true);
                btnSenddata.setEnabled(true);
                break;
            case R.id.btn_udpClose:
                client.setUdpLife(false);
                btnUdpConn.setEnabled(true);
                btnUdpClose.setEnabled(false);
                btnSenddata.setEnabled(false);
                break;
            case R.id.senddata:
                Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        Message message = new Message();
                        message.what = 2;
                        if (textGps.getText().toString() != "") {
                            client.send(textGps.getText().toString() + imei.getText().toString());
                            message.obj = textGps.getText().toString() + imei.getText().toString();
                            myHandler.sendMessage(message);
                        }

                    }
                });
                thread.start();
                break;
        }
    }

    /**
     * 创建Wifi热点
     */
    private void createWifiHotspot() {
        if (wifiManager.isWifiEnabled()) {
            //如果wifi处于打开状态，则关闭wifi,
            wifiManager.setWifiEnabled(false);
        }
        WifiConfiguration config = new WifiConfiguration();
        config.SSID = WIFI_HOTSPOT_SSID;
        config.preSharedKey = "123456789";
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
                textview.setText("热点已开启 SSID:" + WIFI_HOTSPOT_SSID + " password:123456789");
            } else {
                textview.setText("创建热点失败");
            }
        } catch (Exception e) {
            e.printStackTrace();
            textview.setText("创建热点失败");
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
                Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS);
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
    }
}

