package com.dashcam.base;


import android.app.AlarmManager;
import android.app.Application;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.sqlite.SQLiteDatabase;
import android.view.WindowManager;
import com.lzy.okhttputils.OkHttpUtils;
import com.lzy.okhttputils.cookie.store.PersistentCookieStore;
import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class MyAPP extends Application {


    private static MyAPP sInstance;
    // 单例模式中获取唯一的MyApplication实例
    public static MyAPP getInstance() {
        if (sInstance == null) {
            sInstance = new MyAPP();
        }
        return sInstance;
    }


    @Override
    public void onCreate() {
        // TODO Auto-generated method stub
        super.onCreate();
        sInstance = this;
        //必须调用初始化
        OkHttpUtils.init(this);
        //以下都不是必须的，根据需要自行选择
        OkHttpUtils.getInstance()//
                .debug("OkHttpUtils")                                              //是否打开调试
                .setConnectTimeout(20000)               //全局的连接超时时间
                .setReadTimeOut(20000)                  //全局的读取超时时间
                .setWriteTimeOut(20000)                 //全局的写入超时时间
                .addInterceptor(new RetryIntercepter())
//                .setCookieStore(new MemoryCookieStore())                           //cookie使用内存缓存（app退出后，cookie消失）
                .setCookieStore(new PersistentCookieStore());                     //cookie持久化存储，如果cookie不过期，则一直有效
        //	.addCommonHeaders(headers)                                         //设置全局公共头
        //	.addCommonParams(params);                                          //设置全局公共参数

    }
    public static String getVersion() {
        PackageManager packageManager = sInstance.getPackageManager();
        try {
            PackageInfo packInfo = packageManager.getPackageInfo(
                    sInstance.getPackageName(), 0);
            return packInfo.versionName;
        } catch (NameNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return "1.0";
    }

    public static int getWindowWith() {
        WindowManager wm = (WindowManager) sInstance
                .getSystemService(Context.WINDOW_SERVICE);

        int width = wm.getDefaultDisplay().getWidth();
        return width;
    }

    public static int getWindowHeight() {
        WindowManager wm = (WindowManager) sInstance
                .getSystemService(Context.WINDOW_SERVICE);
        int height = wm.getDefaultDisplay().getHeight();
        return height;
    }
    public class RetryIntercepter implements Interceptor {

        public int maxRetryCount = 3;
        private int retryCount = 0;

        @Override
        public Response intercept(Chain chain) throws IOException {
            Request request = chain.request();
            Response response = chain.proceed(request);
            while (!response.isSuccessful() && retryCount < maxRetryCount) {
                retryCount++;
                response = chain.proceed(request);
            }

            return response;
        }

    }
}
