package com.dashcam;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import com.dashcam.photovedio.CameraSurfaceView;
import com.dashcam.photovedio.FileUtil;

/**
 * Created by Administrator on 2017/9/24.
 */

public class USBDiskReceiver extends BroadcastReceiver {


    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        String path = intent.getData().getPath();
        if (!TextUtils.isEmpty(path)){
            if ("android.intent.action.MEDIA_UNMOUNTED".equals(action)) {
              String  rootPath = FileUtil.getStoragePath(context, true);
                if (rootPath == null) {
                    rootPath = FileUtil.getStoragePath(context, false);
                }
               MainActivity.rootPath =rootPath;
                CameraSurfaceView.rootPath =rootPath;
            }
            if ("android.intent.action.MEDIA_MOUNTED".equals(action)) {
                String  rootPath = FileUtil.getStoragePath(context, true);
                if (rootPath == null) {
                    rootPath = FileUtil.getStoragePath(context, false);
                }
                MainActivity.rootPath =rootPath;
                CameraSurfaceView.rootPath =rootPath;
            }
        }


    }
}
