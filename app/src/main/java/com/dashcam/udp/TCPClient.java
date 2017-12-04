package com.dashcam.udp;

import android.content.Intent;
import android.util.Log;

import com.dashcam.MainActivity;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * Created by Administrator on 2017/11/28.
 */

public class TCPClient implements Runnable{
    private String TAG = "TcpClient";
    private String  serverIP = "47.104.8.174";
    private int serverPort = 9999;

    private PrintWriter pw;
    private InputStream is;
    private DataInputStream dis;
    private boolean isRun = true;
    private Socket socket = null;
    byte buff[]  = new byte[4096];
    private String rcvMsg;
    private int rcvLen;

    public TCPClient(String ip , int port){
        this.serverIP = ip;
        this.serverPort = port;
    }

    public void closeSelf(){
        isRun = false;
    }

    public void send(String msg){
        pw.println(msg);
        pw.flush();
    }

    @Override
    public void run() {
        try {
            socket = new Socket(serverIP,serverPort);
            socket.setSoTimeout(5000);
            pw = new PrintWriter(socket.getOutputStream(),true);
            is = socket.getInputStream();
            dis = new DataInputStream(is);
        } catch (IOException e) {
            e.printStackTrace();
        }
        while (isRun){
            try {

                rcvLen = dis.read(buff);
                rcvMsg = new String(buff,0,rcvLen,"utf-8");
                Log.i(TAG, "run: 收到消息:"+ rcvMsg);
                Intent RcvIntent = new Intent();
                RcvIntent.setAction("udpRcvMsg");
                RcvIntent.putExtra("udpRcvMsg", rcvMsg);
                MainActivity.context.sendBroadcast(RcvIntent);
                if (rcvMsg.equals("QuitClient")){   //服务器要求客户端结束
                    isRun = false;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
        try {
            pw.close();
            is.close();
            dis.close();
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}