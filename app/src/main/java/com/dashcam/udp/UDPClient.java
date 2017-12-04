package com.dashcam.udp;

import android.content.Intent;
import android.util.Log;

import com.dashcam.MainActivity;
import com.dashcam.base.DateUtil;
import com.itgoyo.logtofilelibrary.LogToFileUtils;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.URLDecoder;
import java.net.UnknownHostException;

/**
 * Created by lenovo on 2016/2/23.
 */
public class UDPClient implements Runnable {
    //final static int udpPort = 3001;
    final static int udpPort = 9999;
    final static String hostIp = "47.104.8.174";
    //final static String hostIp = "192.168.43.22";
    private static DatagramSocket socket = null;
    private static DatagramPacket packetSend, packetRcv;
    private boolean udpLife = true; //udp生命线程
    private byte[] msgRcv = new byte[1024]; //接收消息

    public UDPClient() {
        super();
    }

    //返回udp生命线程因子是否存活
    public boolean isUdpLife() {
        if (udpLife) {
            return true;
        }

        return false;
    }

    //更改UDP生命线程因子
    public void setUdpLife(boolean b) {
        udpLife = b;
    }

    //发送消息
    public boolean send(String msgSend) {
        if (msgSend == null) return false;
        InetAddress hostAddress = null;

        try {
            hostAddress = InetAddress.getByName(hostIp);
        } catch (UnknownHostException e) {
            Log.i("udpClient", "未找到服务器");
            LogToFileUtils.write("udpClient,未找到服务器" + e.toString());//写入日志
            e.printStackTrace();
            return false;
        }

/*        try {
            socket = new DatagramSocket();
        } catch (SocketException e) {
            Log.i("udpClient","建立发送数据报失败");
            e.printStackTrace();
        }*/
       /* ByteArrayOutputStream ostream = new ByteArrayOutputStream();
        DataOutputStream dataStream = new DataOutputStream(ostream);
        dataStream.writeUTF(msgSend);
        dataStream.close();
        byte[] data = ostream.toByteArray();*/
        // packetSend = new DatagramPacket(data , data.length,hostAddress,udpPort);
        String newmsg = null;
        try {
            newmsg = URLDecoder.decode(msgSend, "GB2312");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        packetSend = new DatagramPacket(newmsg.getBytes(), newmsg.getBytes().length, hostAddress, udpPort);
        try {
            socket.send(packetSend);
        } catch (IOException e) {
            e.printStackTrace();
            Log.i("udpClient", "发送失败");
            LogToFileUtils.write("udpClient,发送失败" + e.toString());//写入日志
            return false;
        }

        return true;
    }

    @Override
    public void run() {

        try {
            socket = new DatagramSocket();
            socket.setSoTimeout(30000);
        } catch (SocketException e) {
            LogToFileUtils.write("udpClient,建立接收数据报失败" + e.toString());//写入日志
            Log.i("udpClient", "建立接收数据报失败");
            e.printStackTrace();
        }
        packetRcv = new DatagramPacket(msgRcv, msgRcv.length);
        while (udpLife) {
            try {
                Log.i("udpClient", "UDP监听");
                // LogToFileUtils.write(DateUtil.getCurrentTimeFormat()+"udpClient, UDP监听");//写入日志
                socket.receive(packetRcv);
                String RcvMsg = new String(packetRcv.getData(), packetRcv.getOffset(), packetRcv.getLength());
                //将收到的消息发给主界面
                Intent RcvIntent = new Intent();
                RcvIntent.setAction("udpRcvMsg");
                RcvIntent.putExtra("udpRcvMsg", RcvMsg);
                MainActivity.context.sendBroadcast(RcvIntent);

                Log.i("Rcv", RcvMsg);
            } catch (IOException e) {
                Log.i("Udp", "接收超时");
                Intent RcvIntent = new Intent();
                RcvIntent.setAction("ReBootUDP");
                RcvIntent.putExtra("ReBootUDP", "111");
                MainActivity.context.sendBroadcast(RcvIntent);
                udpLife=false;
                e.printStackTrace();
            }
        }

        Log.i("udpClient", "UDP监听关闭");
        LogToFileUtils.write("udpClient, UDP监听关闭");//写入日志
        socket.close();
    }
}
