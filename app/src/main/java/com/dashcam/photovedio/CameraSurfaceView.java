package com.dashcam.photovedio;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Environment;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Toast;

import com.dashcam.MainActivity;
import com.dashcam.base.DateUtil;
import com.dashcam.base.FileSUtil;
import com.dashcam.base.RefreshEvent;

import org.greenrobot.eventbus.EventBus;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import static android.hardware.Camera.Parameters.FLASH_MODE_OFF;


public class CameraSurfaceView extends SurfaceView implements SurfaceHolder.Callback, Camera.AutoFocusCallback, View.OnClickListener {

    /* protected static final int[] VIDEO_320 = {320, 240};
     protected static final int[] VIDEO_480 = {640, 480};
     protected static final int[] VIDEO_720 = {1280, 720};

     protected static final int[] VIDEO_2160 = {3600, 2160};*/
    protected static final int[] VIDEO_1080 = {1920, 1080};

    private int screenOritation = Configuration.ORIENTATION_PORTRAIT;
    private boolean mOpenBackCamera = true;
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
        cameraState = CameraState.START;
        String photopath = rootPath + File.separator + "photomini" + File.separator;
        File mDirFile = new File(photopath);
        boolean success = false;
        if (!mDirFile.exists()) {
            success = mDirFile.mkdirs();
        }
        //     mCompressor = new Compressor.Builder(context)
        //            .setMaxWidth(1920)
        //           .setMaxHeight(1080).setQuality(100).setDestinationDirectoryPath(photopath).build();
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
        setOnClickListener(this);
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
            mCamera = null;
            cameraState = CameraState.ERROR;
            if (cameraStateListener != null) {
                cameraStateListener.onCameraStateChange(cameraState);
            }
        }
        if (mCamera == null) {
            Toast.makeText(context, "打开摄像头失败", Toast.LENGTH_SHORT).show();
            return;
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
            e.printStackTrace();
        }
        return -1;
    }

    public boolean setDefaultCamera(boolean backCamera) {
        if (mOpenBackCamera == backCamera) return false;
        if (isRecording) {
            Toast.makeText(context, "请先结束录像", Toast.LENGTH_SHORT).show();
            return false;
        }
        mOpenBackCamera = backCamera;
        if (mCamera != null) {
            closeCamera();
            openCamera();
            startPreview();
        }
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
            }
        } catch (Exception ee) {
        }
    }

    private boolean isSupportCameraLight() {
        boolean mIsSupportCameraLight = false;
        try {
            if (mCamera != null) {
                Camera.Parameters parameter = mCamera.getParameters();
                Object a = parameter.getSupportedFlashModes();
                if (a == null) {
                    mIsSupportCameraLight = false;
                } else {
                    mIsSupportCameraLight = true;
                }
            }
        } catch (Exception e) {
            mIsSupportCameraLight = false;
            e.printStackTrace();
        }
        return mIsSupportCameraLight;
    }


    private Camera.PreviewCallback previewCallback = new Camera.PreviewCallback() {
        public synchronized void onPreviewFrame(byte[] data, Camera camera) {
            if (data == null) {
                releaseCamera();
                return;
            }
            //you can code media here
            if (cameraState != CameraState.PREVIEW) {
                cameraState = CameraState.PREVIEW;
                if (cameraStateListener != null) {
                    cameraStateListener.onCameraStateChange(cameraState);
                }
            }
            mCamera.addCallbackBuffer(previewBuffer);
        }
    };

    //设置Camera各项参数
    public void startPreview() {
        if (mCamera == null) return;
        try {

            mParam = mCamera.getParameters();
            mParam.setPreviewFormat(previewformat);
            mParam.setFlashMode(FLASH_MODE_OFF);
            //  mParam.setRotation(0);
            Camera.Size previewSize = CamParaUtil.getSize(mParam.getSupportedPreviewSizes(), 1000,
                    mCamera.new Size(VIDEO_1080[1], VIDEO_1080[0]));
            mParam.setPreviewSize(previewSize.width, previewSize.height);
            int yuv_buffersize = previewSize.width * previewSize.height * ImageFormat.getBitsPerPixel(previewformat) / 8;
            previewBuffer = new byte[yuv_buffersize];
            Camera.Size pictureSize = CamParaUtil.getSize(mParam.getSupportedPictureSizes(), 1500,
                    mCamera.new Size(VIDEO_1080[1], VIDEO_1080[0]));
            mParam.setPictureSize(pictureSize.width, pictureSize.height);
            if (CamParaUtil.isSupportedFormats(mParam.getSupportedPictureFormats(), ImageFormat.JPEG)) {
                mParam.setPictureFormat(ImageFormat.JPEG);
                mParam.setJpegQuality(100);
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
            if (mRunInBackground) {
                mCamera.setPreviewTexture(mSurfaceTexture);
                mCamera.addCallbackBuffer(previewBuffer);
//                mCamera.setPreviewCallbackWithBuffer(previewCallback);//设置摄像头预览帧回调
            } else {
                mCamera.setPreviewDisplay(mSurfaceHolder);
//                mCamera.setPreviewCallback(previewCallback);//设置摄像头预览帧回调
            }
            mCamera.setParameters(mParam);
            mCamera.startPreview();
            if (cameraState != CameraState.START) {
                cameraState = CameraState.START;
                if (cameraStateListener != null) {
                    cameraStateListener.onCameraStateChange(cameraState);
                }
            }
        } catch (Exception e) {
            releaseCamera();
            return;
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
                mCamera.setPreviewCallback(null);
                mCamera.stopPreview();
            }
            if (cameraState != CameraState.STOP) {
                cameraState = CameraState.STOP;
                if (cameraStateListener != null) {
                    cameraStateListener.onCameraStateChange(cameraState);
                }
            }
        } catch (Exception ee) {
        }
    }

    @Override
    public void onClick(View v) {
        if (mCamera != null) {
            mCamera.autoFocus(null);
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        stopPreview();
        startPreview();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        stopPreview();
        if (mRunInBackground)
            startPreview();
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
     * ___________________________________前/后台运行______________________________________
     **/
    public void setRunBack(boolean b) {
        if (mCamera == null) return;
        if (b == mRunInBackground) return;
        if (!b && !isAttachedWindow) {
            Toast.makeText(context, "Vew未依附在Window,无法显示", Toast.LENGTH_SHORT).show();
            return;
        }
        mRunInBackground = b;
        if (b)
            setVisibility(View.GONE);
        else
            setVisibility(View.VISIBLE);
    }

    /**
     * ___________________________________开关闪光灯______________________________________
     **/
    public void switchLight(boolean open) {
        if (mCamera == null) return;
        try {
            if (mCamera != null) {
                if (open) {
                    Camera.Parameters parameter = mCamera.getParameters();
                    if (parameter.getFlashMode().equals("off")) {
                        parameter.setFlashMode("torch");
                        mCamera.setParameters(parameter);
                    } else {
                        parameter.setFlashMode("off");
                        mCamera.setParameters(parameter);
                    }
                } else {
                    Camera.Parameters parameter = mCamera.getParameters();
                    if ((parameter.getFlashMode() != null) &&
                            (parameter.getFlashMode().equals("torch"))) {
                        parameter.setFlashMode("off");
                        mCamera.setParameters(parameter);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * ___________________________________以下为拍照模块______________________________________
     **/
    public void capture() {

        if (mCamera == null) return;
        mCamera.autoFocus(this);
    }

    @Override
    public void onAutoFocus(boolean success, Camera camera) {
        try {
            mCamera.takePicture(new Camera.ShutterCallback() {
                @Override
                public void onShutter() {

                }
            }, null, new Camera.PictureCallback() {
                @Override
                public void onPictureTaken(byte[] data, Camera camera) {
                    Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                    String photopath = saveBitmap(bitmap);
                    startPreview();
                    EventBus.getDefault().post(new RefreshEvent(1, photopath, ""));
                  //  Toast.makeText(context, "拍照成功", Toast.LENGTH_SHORT).show();
                    if (MainActivity.IsPengZhuang) {
                        if (MainActivity.IsBackCamera) {
                            setDefaultCamera(false);
                            MainActivity.IsBackCamera = false;
                            capture();
                            try {
                                Thread.sleep(600);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            capture();
                            try {
                                Thread.sleep(600);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            setDefaultCamera(true);
                            MainActivity.IsBackCamera = true;
                            MainActivity.IsPengZhuang = false;
                            closeCamera();
                        }
                    }

                }
            });
        } catch (Exception e) {
            if (isRecording) {
                Toast.makeText(context, "请先结束录像", Toast.LENGTH_SHORT).show();
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
        //   mediaRecorder.setVideoSize(1280,720);
        //    mediaRecorder.setVideoFrameRate(20);
        //   mediaRecorder.setVideoEncodingBitRate(1 * 1024 * 1024);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setVideoEncoder(mencode);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mediaRecorder.setVideoSize(VIDEO_SIZE[0], VIDEO_SIZE[1]);
        // mediaRecorder.setVideoSize(720, 720);
        //  mediaRecorder.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_720P));
        mediaRecorder.setVideoFrameRate(30);
        mediaRecorder.setVideoEncodingBitRate(10 * 1024 * 1024);
       /* if (mOpenBackCamera) {
            mediaRecorder.setOrientationHint(90);
        } else {
            if (screenOritation == Configuration.ORIENTATION_LANDSCAPE)
                mediaRecorder.setOrientationHint(90);
            else
                mediaRecorder.setOrientationHint(270);
        }
        if (maxDurationMs != -1) {
            mediaRecorder.setMaxDuration(maxDurationMs);
            mediaRecorder.setOnInfoListener(onInfoListener);
        }*/
        //   driveVideo = getMediaOutputPath();
        //   mediaRecorder.setOutputFile(driveVideo.getPath());
        currentVediopah = getMediaOutputPath();
        mediaRecorder.setOutputFile(currentVediopah);
        // 设置录制文件最长时间(10分钟)
        mediaRecorder.setMaxDuration(Recordtime);
        mediaRecorder.setOnInfoListener(new MediaRecorder.OnInfoListener() {
            @Override
            public void onInfo(MediaRecorder mr, int what, int extra) {
                switch (what) {
                    case MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED:
                        stopRecord();
                        if (MainActivity.IsBackCamera && !MainActivity.IsZhualu) {
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
            starttimelamp = mCalendar.getTimeInMillis() / 1000;// 1393844912
            isRecording = true;
            Intent intent = new Intent();
            intent.setAction("com.dashcam.intent.START_RECORD");
            context.sendBroadcast(intent);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;

    }


    public void stopRecord() {
        if (!isRecording) return;
        mediaRecorder.setPreviewDisplay(null);
        try {
            Calendar mCalendar = Calendar.getInstance();
            long tamp = mCalendar.getTimeInMillis() / 1000;// 1393844912
            if (tamp - starttimelamp < 2) {
                Thread.sleep(1000);
                stopRecord();
            } else {
                mediaRecorder.stop();
                isRecording = false;

                Toast.makeText(context, "视频已保存在根目录", Toast.LENGTH_SHORT).show();
                if (MainActivity.IsZhualu) {
                    EventBus.getDefault().post(new RefreshEvent(4, currentVediopah, ""));
                } else {
                    Intent intent = new Intent();
                    intent.setAction("com.dashcam.intent.STOP_RECORD");
                    context.sendBroadcast(intent);
                }
            }

            ;

            //  videoDb.addDriveVideo(driveVideo);
        } catch (IllegalStateException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * _________________________________________________________________________________________
     **/


   /* private String compressor(String path) {
        return mCompressor.compressToFile(new File(path)).getAbsolutePath();
    }*/

    //保存照片
    public String saveBitmap(Bitmap b) {
        b = FileSUtil.drawTextToBitmap(context, b, DateUtil.getCurrentTimeFormat() + "  " + MainActivity.GPSSTR);
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
            e.printStackTrace();
            return "";
        }

        return jpegName;
    }

    //获取视频存储路径
 /*   public DriveVideo getMediaOutputPath() {
        String name = getTime();
        String filepath = rootPath + "/vedio";
        File file = new File(filepath);
        if (!file.exists()) {
            file.mkdirs();
        }

        String dangerfilepath = rootPath + "/dangervedio";
        File dangerfile = new File(dangerfilepath);
        if (!dangerfile.exists()) {
            dangerfile.mkdirs();
        }
        String vediopath = rootPath + "/vedio/" + name + ".mp4";
        return new DriveVideo(name, 0, 480, vediopath);
    }*/
    //获取视频存储路径
    public String getMediaOutputPath() {
        String name = getTime();
        String filepath = rootPath + "/vedio";
        File file = new File(filepath);
        if (!file.exists()) {
            file.mkdirs();
        }
        /*
        此文件夹为不能删除的录像
         */
        String dangerfilepath = rootPath + "/dangervedio";
        File dangerfile = new File(dangerfilepath);
        if (!dangerfile.exists()) {
            dangerfile.mkdirs();
        }
        String vediopath = rootPath + "/vedio/" + name + ".mp4";
        return vediopath;
    }

    /*  private  String getTime() {
          return new SimpleDateFormat("yyyyMMdd-HH:mm:ss").format(new Date(System.currentTimeMillis()));
      }*/
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
