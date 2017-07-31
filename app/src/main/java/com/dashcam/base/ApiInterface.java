package com.dashcam.base;

import android.util.Log;

/**
 * Created by Administrator on 2016/7/9.
 */
public class ApiInterface {


    public static final String MAIN_URL = "http://zfcg.cqxyyxxkj.cn:9090/CPS/";

    /**
     * 上传图片
     */
    public static final String savePicture = MAIN_URL + "appUpLoadPic/savePicture?";

    /**
     * 上传图片
     */
    public static final String saveFile = MAIN_URL + "appUpLoadPic/saveFile?";

}
