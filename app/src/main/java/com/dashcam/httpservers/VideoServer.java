package com.dashcam.httpservers;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import android.util.Log;
import android.widget.Toast;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response.Status;
/**
 * Created by Administrator on 2017/7/3.
 */

public class VideoServer extends NanoHTTPD {

    public static final int DEFAULT_SERVER_PORT = 8080;
    public static final String TAG = VideoServer.class.getSimpleName();

    private static final String REQUEST_ROOT = "/";

    private String mVideoFilePath;
    private int mVideoWidth  = 1280;
    private int mVideoHeight = 720;
    private String mp4str ="";

    public VideoServer(String filepath) {
        super(DEFAULT_SERVER_PORT);
        mVideoFilePath = filepath;

    }

    @Override
    public Response serve(IHTTPSession session) {
        Log.d(TAG,"OnRequest: "+session.getUri());
         String uri = session.getUri();
        if (session.getUri().contains("all")){

            return responseRootPage2(session);

        }
       else  if (session.getUri().contains(".mp4")){

            return responseRootPage(session);

        }
       else if(REQUEST_ROOT.equals(session.getUri())) {
          //  return responseRootPage(session);

           return responseRootPage(session);
        }
        else if(mVideoFilePath.equals(session.getUri())) {
            return responseVideoStream(session);
        }
        return response404(session,session.getUri());
    }
 /*   @Override
    public Response serve(IHTTPSession session) {
        Map<String, List<String>> decodedQueryParameters =
                decodeParameters(session.getQueryParameterString());

        StringBuilder sb = new StringBuilder();
        sb.append("<html>");
        sb.append("<head><title>Debug Server</title></head>");
        sb.append("<body>");
        sb.append("<h1>视频文件列表</h1>");

        sb.append("<p><blockquote><b>URI</b> = ").append(
                String.valueOf(session.getUri())).append("<br />");

        sb.append("<b>Method</b> = ").append(
                String.valueOf(session.getMethod())).append("</blockquote></p>");

        sb.append("<h3>Headers</h3><p><blockquote>").
                append(toString(session.getHeaders())).append("</blockquote></p>");

        sb.append("<h3>Parms</h3><p><blockquote>").
                append(toString(session.getParms())).append("</blockquote></p>");

        sb.append("<h3>Parms (multi values?)</h3><p><blockquote>").
                append(toString(decodedQueryParameters)).append("</blockquote></p>");

        try {
            Map<String, String> files = new HashMap<String, String>();
            session.parseBody(files);
            sb.append("<h3>Files</h3><p><blockquote>").
                    append(toString(files)).append("</blockquote></p>");
        } catch (Exception e) {
            e.printStackTrace();
        }

        sb.append("</body>");
        sb.append("</html>");
        return new Response(sb.toString());
    }*/

    private String toString(Map<String, ? extends Object> map) {
        if (map.size() == 0) {
            return "";
        }
        return unsortedList(map);
    }

    private String unsortedList(Map<String, ? extends Object> map) {
        StringBuilder sb = new StringBuilder();
        sb.append("<ul>");
        for (Map.Entry entry : map.entrySet()) {
            listItem(sb, entry);
        }
        sb.append("</ul>");
        return sb.toString();
    }

    private void listItem(StringBuilder sb, Map.Entry entry) {
        sb.append("<li><code><b>").append(entry.getKey()).
                append("</b> = ").append(entry.getValue()).append("</code></li>");
    }

    public Response responseRootPage(IHTTPSession session) {
        File file = new File(mVideoFilePath);
        if(!file.exists()) {
            return response404(session,mVideoFilePath);
        }
        StringBuilder builder = new StringBuilder();
        builder.append("<!DOCTYPE html><html><body>");
        builder.append("<video ");
        builder.append("width="+getQuotaStr(String.valueOf(mVideoWidth))+" ");
        builder.append("height="+getQuotaStr(String.valueOf(mVideoHeight))+" ");
        builder.append("controls>");
        builder.append("<source src="+getQuotaStr(mVideoFilePath)+" ");
        builder.append("type="+getQuotaStr("video/mp4")+">");
        builder.append("Your browser doestn't support HTML5");
        builder.append("</video>");
        builder.append("</body></html>\n");
        mp4str=session.getUri();
        return new Response(builder.toString());
    }
 public Response responseRootPage2(IHTTPSession session) {
     File file = new File(mVideoFilePath);
     if(!file.exists()) {
         return response404(session,mVideoFilePath);
     }
     //取出文件列表：
     final File[] files = file.listFiles();
     List<String> specs = new ArrayList<String>();
     for(File spec : files){
         specs.add(spec.getName());
     }
     StringBuilder builder = new StringBuilder();
  //   builder.append("<!DOCTYPE html><html><body>");
     builder.append(listToString(specs,"%"));
   //  builder.append("</body></html>\n");
     return new Response(builder.toString());
  }
    public Response responseVideoStream(IHTTPSession session) {
        try {
            FileInputStream fis = new FileInputStream(mVideoFilePath+mp4str);
            return new NanoHTTPD.Response(Status.OK, "video/mp4", fis);
        }
        catch (FileNotFoundException e) {
            e.printStackTrace();
            return response404(session,mVideoFilePath);
        }
    }

    public Response response404(IHTTPSession session,String url) {
        StringBuilder builder = new StringBuilder();
        builder.append("<!DOCTYPE html><html><body>");
        builder.append("Sorry, Can't Found "+url + " !");
        builder.append("</body></html>\n");
        return new Response(builder.toString());
    }


    protected String getQuotaStr(String text) {
        return "\"" + text + "\"";
    }
    public String listToString(List list, String separator) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++)
        {        sb.append(list.get(i)).append(separator);    }
        return sb.toString().substring(0,sb.toString().length()-1);}
}
