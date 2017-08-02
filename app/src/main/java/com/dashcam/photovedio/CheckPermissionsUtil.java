package com.dashcam.photovedio;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

public class CheckPermissionsUtil {
    public static final String TAG = "CheckPermissionsUtil";

    Context mContext;

    public CheckPermissionsUtil(Context mContext) {
        this.mContext = mContext;
    }

    private String[] needPermissions = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WAKE_LOCK,
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.WRITE_SETTINGS,
            Manifest.permission.MOUNT_UNMOUNT_FILESYSTEMS
    };

    private boolean checkPermission(String... needPermissions) {
        for (String permission : needPermissions) {
            if (ActivityCompat.checkSelfPermission(mContext, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void requestPermission(Activity activity, int code, String... needPermissions) {
        ActivityCompat.requestPermissions(activity, needPermissions, code);
        Log.i(TAG, "request Permission...");
    }
    public void requestAllPermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= 23 && activity.getApplicationInfo().targetSdkVersion >= 23) {
            Log.i(TAG, "request All Permission...");
            for (String permission : needPermissions) {
                if (!checkPermission(permission)) {
                    requestPermission(activity, 0, permission);
                }
            }
        }
    }
}