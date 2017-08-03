package com.dashcam.location;

import android.content.Context;

import com.baidu.location.BDLocationListener;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;


/**
 * 定位工具
 * Created by chenfei on 2016/6/20.
 */
public class LocationUtil {

    private LocationClient mLocationClient;

    public LocationUtil(Context context, BDLocationListener listener) {
        mLocationClient = new LocationClient(context);
        mLocationClient.registerLocationListener(listener);
        initLocation();
    }


    /**
     * 初始化定位参数
     */
    private void initLocation() {
        LocationClientOption option = new LocationClientOption();
        option.setLocationMode(LocationClientOption.LocationMode.Hight_Accuracy);
        option.setOpenGps(true); // 打开gps
        option.setCoorType("bd09ll"); // 设置坐标类型
        option.setScanSpan(5000);
        option.setIsNeedAddress(true);
        mLocationClient.setLocOption(option);
        mLocationClient.start();
    }


    /**
     * 开始定位
     */
    public void startLocate() {
        mLocationClient.start();
    }

    /**
     * 关闭定位服务
     */
    public void stopLocate() {
        mLocationClient.stop();
    }

}
