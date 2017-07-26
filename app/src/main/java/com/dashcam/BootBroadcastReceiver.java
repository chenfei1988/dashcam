package com.dashcam;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;

/**
 * 基本功能：开机自动启动APP
 * 创建：chenfei
 * 创建时间：17/6/15
 * 邮箱：457771023@qq.com
 */
public class BootBroadcastReceiver extends BroadcastReceiver {
    static final String ACTION = "android.intent.action.BOOT_COMPLETED";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(ACTION)) {
            final Intent mainActivityIntent = new Intent(context, MainActivity.class);  // 要启动的Activity
            mainActivityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            final Context mContext = context;
            new Handler().postDelayed(new Runnable(){
                public void run() {
                    mContext.startActivity(mainActivityIntent);
                }
            }, 3000);
        }
    }
}
