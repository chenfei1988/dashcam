package com.dashcam.photovedio;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.dashcam.MainActivity;
import com.dashcam.base.DateUtil;
import com.dashcam.base.FileSUtil;
import com.dashcam.base.MyAPP;
import com.dashcam.base.RefreshEvent;
import com.itgoyo.logtofilelibrary.LogToFileUtils;

import org.greenrobot.eventbus.EventBus;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import static android.hardware.Camera.Parameters.FLASH_MODE_OFF;


public class CameraSurfaceView extends SurfaceView implements SurfaceHolder.Callback
    //    , Camera.PreviewCallback
{

    /* protected static final int[] VIDEO_320 = {320, 240};
     protected static final int[] VIDEO_480 = {640, 480};*/
    private int PIC_SIZE_WIDTH = 1280;
    private int PIC_SIZE_HEIGHT = 720;
    protected static final int[] VIDEO_2160 = {3600, 2160};
    protected static final int[] VIDEO_1080 = {1920, 1080};

    private int screenOritation = Configuration.ORIENTATION_PORTRAIT;
    private boolean mOpenBackCamera = true;
    private boolean isTakePic = false;
    private SurfaceHolder mSurfaceHolder;
    private SurfaceTexture mSurfaceTexture;
    private boolean mRunInBackground = false;
    boolean isAttachedWindow = false;
    private Camera mCamera;
    private Camera.Parameters mParam;
    private byte[] previewBuffer;
    private int mCameraId;
    protected int previewformat = ImageFormat.NV21;
    Context context;
    private long starttimelamp = 0;
    //    Compressor mCompressor;
    private String currentVediopah = "";
    //  private DriveVideo driveVideo;
    //public static String COMPRESSOR_DIR = Environment.getExternalStorageDirectory() + File.separator  + "photo" + File.separator + "photomini" + File.separator;
    public static String rootPath = "";
    public static int Recordtime = 1000 * 60 * 3;
    public static int[] VIDEO_SIZE = {1280, 720};
    public static int mencode = MediaRecorder.VideoEncoder.MPEG_4_SP;


    public CameraSurfaceView(Context context) {
        super(context);
        init(context);
    }

    public CameraSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public CameraSurfaceView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    private void init(Context context) {
        this.context = context;
        rootPath = FileUtil.getStoragePath(context, true);
        if (rootPath == null) {
            rootPath = FileUtil.getStoragePath(context, false);
        }
        this.context = context;
        cameraState = CameraState.START;
        if (cameraStateListener != null) {
            cameraStateListener.onCameraStateChange(cameraState);
        }
        openCamera();
        if (context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            screenOritation = Configuration.ORIENTATION_LANDSCAPE;
        }
        mSurfaceHolder = getHolder();
        mSurfaceHolder.addCallback(this);
        mSurfaceTexture = new SurfaceTexture(10);
        post(new Runnable() {
            @Override
            public void run() {
                if (!isAttachedWindow) {
                    mRunInBackground = true;
                    startPreview();
                }
            }
        });

    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        isAttachedWindow = true;
    }

    public void openCamera() {
        if (mOpenBackCamera) {
            mCameraId = findCamera(false);
        } else {
            mCameraId = findCamera(true);
        }
        if (mCameraId == -1) {
            mCameraId = 0;
        }
        try {
            mCamera = Camera.open(mCameraId);
        } catch (Exception ee) {
            LogToFileUtils.write("findCamera:" + ee.toString());
            Log.e("findCamera:", "findCamera failed" + ee.toString());
            mCamera = null;
            cameraState = CameraState.ERROR;
            if (cameraStateListener != null) {
                cameraStateListener.onCameraStateChange(cameraState);
            }
        }
        if (mCamera == null) {
            LogToFileUtils.write(mOpenBackCamera ? "Car OutSide Carmera openCamera Failed" : "Car Inside Carmera openCamera Failed");//写入日志
            Log.e("OpenCarmera:", mOpenBackCamera ? "Car OutSide Carmera openCamera Failed" : "Car Inside Carmera openCamera Failed");

        } else {
            LogToFileUtils.write(mOpenBackCamera ? "Car OutSide Carmera openCamera Success" : "Car Inside Carmera openCamera Success");//写入日志
            Log.e("OpenCarmera:", mOpenBackCamera ? "Car OutSide Carmera openCamera Success" : "Car Inside Carmera openCamera Success");
        }
    }


    private int findCamera(boolean front) {
        int cameraCount;
        try {
            Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
            cameraCount = Camera.getNumberOfCameras();
            for (int camIdx = 0; camIdx < cameraCount; camIdx++) {
                Camera.getCameraInfo(camIdx, cameraInfo);
                int facing = front ? 1 : 0;
                if (cameraInfo.facing == facing) {
                    return camIdx;
                }
            }
        } catch (Exception e) {
            LogToFileUtils.write("findCamera:" + e.toString());
            Log.e("findCamera:", "findCamera failed" + e.toString());
            e.printStackTrace();
        }
        return -1;
    }

    public boolean setDefaultCamera(boolean backCamera) {
        if (mOpenBackCamera == backCamera) {
            LogToFileUtils.write("ChangeCamera:" + "now is backCamera");
            Log.e("ChangeCamera:", "now is backCamera");
            return false;
        }
        if (isRecording) {
            // LogToFileUtils.write("please stop recored video");
            //Toast.makeText(context, "请先结束录像", Toast.LENGTH_SHORT).show();
            LogToFileUtils.write("ChangeCamera:" + "please stop recored video");
            Log.e("ChangeCamera:", "please stop recored video");
            return false;
        }
        mOpenBackCamera = backCamera;
        if (mCamera != null) {
            closeCamera();
            LogToFileUtils.write("closeCamera:" + "");
            Log.e("closeCamera:", "");

            try {
                Thread.sleep(500);
                openCamera();
                LogToFileUtils.write("openCamera:" + "");
                Log.e("openCamera:", "");
                Thread.sleep(1500);
                startPreview();
                LogToFileUtils.write("startPreview:" + "");
                Log.e("startPreview:", "");
            } catch (Exception e) {
                LogToFileUtils.write("setDefaultCamera:" + e.toString());
                Log.e("setDefaultCamera:", e.toString());
                e.printStackTrace();
            }

        }
        LogToFileUtils.write("ChangeCamera:" + "success");
        Log.e("ChangeCamera:", "success");
        return true;
    }


    public void closeCamera() {
        stopRecord();
        stopPreview();
        releaseCamera();
    }


    private void releaseCamera() {

        try {
            if (mCamera != null) {
                mCamera.setPreviewCallback(null);
                mCamera.setPreviewCallbackWithBuffer(null);
                mCamera.stopPreview();
                mCamera.release();
                mCamera = null;
                LogToFileUtils.write("releaseCamera success");//写入日志
                Log.e("releaseCamera:", "success");
            }
        } catch (Exception ee) {
            LogToFileUtils.write("releaseCamera Failed" + ee.toString());//写入日志
            Log.e("releaseCamera:", "Failed");
        }
    }


    //设置Camera各项参数
    public void startPreview() {
        if (mCamera == null) return;
        try {
            if (mediaRecorder == null) {
                mCamera.lock();
                mParam = mCamera.getParameters();
            } else {
                mParam = mCamera.getParameters();
            }
            //  mParam = mCamera.getParameters();
            mParam.setPreviewFormat(previewformat);
            mParam.setFlashMode(FLASH_MODE_OFF);
            //  mParam.setRotation(0);
            //  Camera.Size previewSize = CamParaUtil.getSize(mParam.getSupportedPreviewSizes(), 1000,
            if (mOpenBackCamera) {
                PIC_SIZE_WIDTH = 1280;
                PIC_SIZE_HEIGHT = 720;
            } else {
                PIC_SIZE_WIDTH = 640;
                PIC_SIZE_HEIGHT = 480;
            }
            Camera.Size previewSize = CamParaUtil.getSize(mParam.getSupportedPreviewSizes(), 1000,
                    mCamera.new Size(PIC_SIZE_WIDTH, PIC_SIZE_HEIGHT));
            mParam.setPreviewSize(previewSize.width, previewSize.height);
            int yuv_buffersize = previewSize.width * previewSize.height * ImageFormat.getBitsPerPixel(previewformat) / 8;
            previewBuffer = new byte[yuv_buffersize];
            //  Camera.Size pictureSize = CamParaUtil.getSize(mParam.getSupportedPictureSizes(), 2000,
            Camera.Size pictureSize = CamParaUtil.getSize(null, 1000,
                    mCamera.new Size(PIC_SIZE_WIDTH, PIC_SIZE_HEIGHT));
            mParam.setPictureSize(pictureSize.width, pictureSize.height);
            if (CamParaUtil.isSupportedFormats(mParam.getSupportedPictureFormats(), ImageFormat.JPEG)) {
                mParam.setPictureFormat(ImageFormat.JPEG);
                mParam.setJpegQuality(95);
            }
        /*    if (CamParaUtil.isSupportedFocusMode(mParam.getSupportedFocusModes(), FOCUS_MODE_AUTO)) {
                mParam.setFocusMode(FOCUS_MODE_AUTO);
            }*/
            if (screenOritation != Configuration.ORIENTATION_LANDSCAPE) {
                mParam.set("orientation", "portrait");
                mCamera.setDisplayOrientation(90);
            } else {
                mParam.set("orientation", "landscape");
                mCamera.setDisplayOrientation(0);
            }
            mCamera.setPreviewTexture(mSurfaceTexture);
            if (mRunInBackground) {

                mCamera.addCallbackBuffer(previewBuffer);
//                mCamera.setPreviewCallbackWithBuffer(previewCallback);//设置摄像头预览帧回调
            } else {
                mCamera.setPreviewDisplay(mSurfaceHolder);
//                mCamera.setPreviewCallback(previewCallback);//设置摄像头预览帧回调
            }
            try {
                mCamera.setParameters(mParam);
            } catch (Exception ex) {
                LogToFileUtils.write(" mCamera.setParameters" + ex.toString());//写入日志
                ex.printStackTrace();
            }

            // mCamera.setParameters(mParam);
            mCamera.startPreview();
            if (cameraState != CameraState.START) {
                cameraState = CameraState.START;
                if (cameraStateListener != null) {
                    cameraStateListener.onCameraStateChange(cameraState);
                }
            }
          //  mCamera.setPreviewCallback(this);
            LogToFileUtils.write("startPreview success");//写入日志
        } catch (Exception e) {
            LogToFileUtils.write("startPreview Failed" + e.toString());//写入日志
            releaseCamera();
            //  openCamera();
            // startPreview();
        }
      /*  try {
            String mode = mCamera.getParameters().getFocusMode();
            if (("auto".equals(mode)) || ("macro".equals(mode))) {
                mCamera.autoFocus(null);
            }
        } catch (Exception e) {
        }*/
    }

    private void stopPreview() {
        if (mCamera == null) return;
        try {
            if (mRunInBackground) {
                mCamera.setPreviewCallbackWithBuffer(null);
                mCamera.stopPreview();
            } else {
                mSurfaceHolder.removeCallback(this);
                mCamera.setPreviewCallback(null);
                mCamera.stopPreview();
            }
            if (cameraState != CameraState.STOP) {
                cameraState = CameraState.STOP;
                if (cameraStateListener != null) {
                    cameraStateListener.onCameraStateChange(cameraState);
                }
            }
            LogToFileUtils.write("stopPreview Success");
        } catch (Exception ee) {
            LogToFileUtils.write("stopPreview failed" + ee.toString());
        }
    }


    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        stopPreview();
        if (mSurfaceHolder != null) {
            new Thread() {
                @Override
                public void run() {
                    super.run();
                    try {
                        Thread.sleep(500);
                    } catch (Exception e) {
                        LogToFileUtils.write("stopPreview failed" + e.toString());
                    }
                    startPreview();
                }
            }.start();

        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        LogToFileUtils.write("surfaceDestroyed:closeCamera");//写入日志
        closeCamera();
      /*  if (mRunInBackground)
            startPreview();*/
    }

    protected CameraState cameraState;
    private CameraStateListener cameraStateListener;


    public enum CameraState {
        START, PREVIEW, STOP, ERROR
    }

    public void setOnCameraStateListener(CameraStateListener listener) {
        this.cameraStateListener = listener;
    }

    public interface CameraStateListener {
        void onCameraStateChange(CameraState paramCameraState);
    }


    /**
     * ___________________________________以下为拍照模块______________________________________
     **/
/*
   @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        byte[] rawImage;
        Bitmap bitmap;
        try {
            if (isTakePic) {
                LogToFileUtils.write("onPreviewFrame Take takePicture begin");
                isTakePic = false;
              //  Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                mCamera.startPreview();
                Camera.Size previewSize = camera.getParameters().getPreviewSize();//获取尺寸,格式转换的时候要用到
                BitmapFactory.Options newOpts = new BitmapFactory.Options();
                newOpts.inJustDecodeBounds = true;
                YuvImage yuvimage = new YuvImage(
                        data,
                        ImageFormat.NV21,
                        previewSize.width,
                        previewSize.height,
                        null);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                yuvimage.compressToJpeg(new Rect(0, 0, previewSize.width, previewSize.height), 100, baos);// 80--JPG图片的质量[0-100],100最高
                rawImage = baos.toByteArray();
                //将rawImage转换成bitmap
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inPreferredConfig = Bitmap.Config.RGB_565;
                bitmap = BitmapFactory.decodeByteArray(rawImage, 0, rawImage.length, options);
                String photopath = saveBitmap(bitmap);
                bitmap.recycle();
                EventBus.getDefault().post(new RefreshEvent(1, photopath, ""));
            }
        } catch (Exception e) {
            LogToFileUtils.write("onPreviewFrame Take takePicture " + e.toString());
        }

    }*/

    public void capture() {

        if (mCamera == null) return;
   //     if (isRecording){
   //         isTakePic = true;
    //    }
   //    else{
            onAutoFocus(mCamera);
     //   }
    }

    public  void onAutoFocus(Camera camera) {
                if (!isTakePic) {
                    try {
                        isTakePic =true;
                        mCamera.takePicture(new Camera.ShutterCallback() {
                            @Override
                            public void onShutter() {
                                LogToFileUtils.write("paizhao voice" + "played");
                            }
                        }, null, new Camera.PictureCallback() {
                            @Override
                            public void onPictureTaken(byte[] data, Camera camera) {
                        /*     byte[] rawImage;
                             Bitmap bitmap;
                             mCamera.startPreview();
                             LogToFileUtils.write("huoqu pic data" + "success");
                             Camera.Size previewSize = camera.getParameters().getPreviewSize();//获取尺寸,格式转换的时候要用到
                             BitmapFactory.Options newOpts = new BitmapFactory.Options();
                             newOpts.inJustDecodeBounds = true;
                             YuvImage yuvimage = new YuvImage(
                                     data,
                                     ImageFormat.NV21,
                                     previewSize.width,
                                     previewSize.height,
                                     null);
                             ByteArrayOutputStream baos = new ByteArrayOutputStream();
                             yuvimage.compressToJpeg(new Rect(0, 0, previewSize.width, previewSize.height), 95, baos);// 80--JPG图片的质量[0-100],100最高
                             rawImage = baos.toByteArray();
                             //将rawImage转换成bitmap
                             BitmapFactory.Options options = new BitmapFactory.Options();
                             options.inPreferredConfig = Bitmap.Config.RGB_565;
                             bitmap = BitmapFactory.decodeByteArray(rawImage, 0, rawImage.length, options);
                             String photopath = saveBitmap(bitmap);
                             bitmap.recycle();
                             EventBus.getDefault().post(new RefreshEvent(1, photopath, ""));*/
                           //     EventBus.getDefault().post(new RefreshEvent(1, photopath, ""));
                                try {
                                    Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                                    String photopath = saveBitmap(bitmap);
                                    bitmap.recycle();
                                    Thread.sleep(3000);
                                    isTakePic = false;
                                   // mCamera.startPreview();
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }

                            }
                        });

                    } catch (Exception e) {
                      //  ResetCamara();
                      //  mCamera.startPreview();
                        LogToFileUtils.write("Take takePicture " + e.toString());
                    }
                }
    }


    /**
     * ___________________________________以下为视频录制模块______________________________________
     **/
    MediaRecorder mediaRecorder = new MediaRecorder();
    public boolean isRecording = false;

    public boolean isRecording() {
        return isRecording;
    }

    public boolean startRecord() {
        return startRecord(-1, null);
    }

    public boolean startRecord(int maxDurationMs, MediaRecorder.OnInfoListener onInfoListener) {
        if (mCamera == null) return false;
        mCamera.unlock();
        mediaRecorder.reset();
        mediaRecorder.setCamera(mCamera);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        AudioManager audioMgr = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        audioMgr.setParameters("SET_MIC_CHOOSE=2");
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mediaRecorder.setVideoEncoder(mencode);
        mediaRecorder.setVideoSize(VIDEO_SIZE[0], VIDEO_SIZE[1]);
        mediaRecorder.setVideoFrameRate(30);
        mediaRecorder.setVideoEncodingBitRate(10 * 1024 * 1024);
        currentVediopah = getMediaOutputPath();
        mediaRecorder.setOutputFile(currentVediopah);
        // 设置录制文件最长时间(3分钟)
        mediaRecorder.setMaxDuration(Recordtime);
        mediaRecorder.setOnInfoListener(new MediaRecorder.OnInfoListener() {
            @Override
            public void onInfo(MediaRecorder mr, int what, int extra) {
                switch (what) {
                    case MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED:
                        stopRecord();
                        if (mOpenBackCamera && !MainActivity.IsZhualu) {
                            startRecord();
                        }
                        break;
                    default:
                        break;
                }
            }
        });
        try {
            mediaRecorder.prepare();
            mediaRecorder.start();
            Calendar mCalendar = Calendar.getInstance();
            starttimelamp = mCalendar.getTimeInMillis();// 1393844912
            isRecording = true;
            Intent intent = new Intent();
            intent.setAction("com.dashcam.intent.START_RECORD");
            if (MyAPP.Debug) {
                context.sendBroadcast(intent);
            }
        } catch (Exception e) {
            LogToFileUtils.write("startrecording failed" + e.toString());
            e.printStackTrace();
            return false;
        }
        LogToFileUtils.write("startrecording success");
        return true;

    }


    public void stopRecord() {
        if (!isRecording) return;
        try {
            Calendar mCalendar = Calendar.getInstance();
            long tamp = mCalendar.getTimeInMillis();// 1393844912
            if (tamp - starttimelamp < 2000) {
                Thread.sleep(3000);
                // stopRecord();
            }
            mediaRecorder.setPreviewDisplay(null);
            mediaRecorder.stop();
            isRecording = false;
            LogToFileUtils.write("video have saved in rootmulu");
            //   Toast.makeText(context, "视频已保存在根目录", Toast.LENGTH_SHORT).show();
            if (MainActivity.IsZhualu) {
                EventBus.getDefault().post(new RefreshEvent(4, currentVediopah, ""));
            } else {
                Intent intent = new Intent();
                intent.setAction("com.dashcam.intent.STOP_RECORD");
                if (MyAPP.Debug) {
                    context.sendBroadcast(intent);
                }
            }

            LogToFileUtils.write("stopRecord Success");
            //  videoDb.addDriveVideo(driveVideo);
        } catch (Exception e) {
            LogToFileUtils.write("stopRecord failed" + e.toString());//写入日志
            e.printStackTrace();
        }
    }

    /**
     * _________________________________________________________________________________________
     **/

    //保存照片
    public String saveBitmap(Bitmap b) {
        b = FileSUtil.drawTextToBitmap(context, b, DateUtil.getCurrentTimeFormat() + "  " + MainActivity.GPSTRZW);
        String jpegName = rootPath + "/photo/" + getTime() + ".jpg";
        File file = new File(rootPath + "/photo");
        if (!file.exists()) {
            file.mkdirs();
        }
        try {
            FileOutputStream fout = new FileOutputStream(jpegName);
            BufferedOutputStream bos = new BufferedOutputStream(fout);
            b.compress(Bitmap.CompressFormat.JPEG, 100, bos);
            bos.flush();
            bos.close();
        } catch (IOException e) {
            LogToFileUtils.write("save Bitmap failed" + e.toString());
            e.printStackTrace();
            return "";
        }

        return jpegName;
    }

    //获取视频存储路径
    public String getMediaOutputPath() {
        String name = getTime();
        String filepath = rootPath + "/video";
        File file = new File(filepath);
        if (!file.exists()) {
            file.mkdirs();
        }
        /*
        此文件夹为不能删除的录像
         */
        String dangerfilepath = rootPath + "/EmergencyVideo";
        File dangerfile = new File(dangerfilepath);
        if (!dangerfile.exists()) {
            dangerfile.mkdirs();
        }
        String vediopath = rootPath + "/video/" + name + ".mp4";
        return vediopath;
    }

    private String getTime() {
        return new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date(System.currentTimeMillis()));
    }

    public void ResetCamara() {
        if (mCamera == null) return;

        mCamera.unlock();
        mediaRecorder.reset();
        mediaRecorder.setCamera(mCamera);
    }

}
