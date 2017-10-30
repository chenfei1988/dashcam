package com.dashcam;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;

import com.dashcam.base.CrashHandler;

/**
 * 基本功能：开机自动启动APP
 * 创建：chenfei
 * 创建时间：17/6/15
 * 邮箱：457771023@qq.com
 */
public class BootBroadcastReceiver extends BroadcastReceiver {


    @Override
    public void onReceive(Context context, Intent intent) {
            try {
                Thread.sleep(10000L);
                final Intent mainActivityIntent = new Intent(context, MainActivity.class);  // 要启动的Activity
                mainActivityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                final Context mContext = context;
                new Handler().postDelayed(new Runnable(){
                    public void run() {
                        mContext.startActivity(mainActivityIntent);
                    }
                }, 25000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        }

}
