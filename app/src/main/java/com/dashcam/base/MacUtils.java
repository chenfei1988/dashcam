package com.dashcam.base;

import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;

/**
 * Created by Administrator on 2017/7/10.
 */

public class MacUtils {

    public static String getMacAddr() {
        try {
            List<NetworkInterface> all = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface nif : all) {
                if (!nif.getName().equalsIgnoreCase("wlan0")) continue;

                byte[] macBytes = nif.getHardwareAddress();
                if (macBytes == null) {
                    return "";
                }

                StringBuilder res1 = new StringBuilder();
                for (byte b : macBytes) {
                    res1.append(String.format("%02X:",b));
                }

                if (res1.length() > 0) {
                    res1.deleteCharAt(res1.length() - 1);
                }
                return res1.toString().replace(":","").substring(8);
               // return res1.toString().substring();
            }
        } catch (Exception ex) {
        }
        return "02:00:00:00:00:00";
    }
}
