package com.camera.simplewebcam;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Environment;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.IOException;
import java.nio.ByteBuffer;

class CameraPreview extends SurfaceView implements SurfaceHolder.Callback, Runnable {

    private static final boolean DEBUG = true;
    private static final String TAG = "shebing";
    protected Context context;
    private SurfaceHolder holder;
    Thread mainLoop = null;
    private Bitmap bmp = null;

    private boolean cameraExists = false;
    private boolean shouldStop = false;

    private int cameraId = 0;
    private int cameraBase = 0;

    static final int IMG_WIDTH = 640;
    static final int IMG_HEIGHT = 480;

    private int winWidth = 0;
    private int winHeight = 0;
    private Rect rect;
    private int dw, dh;
    private float rate;
    private MediaCodec mediaCodec;
    private Surface inputSurface;
    private static final int COLOR_FMT = MediaCodecInfo.CodecCapabilities.COLOR_Format32bitARGB8888;

    // JNI functions
    public native int prepareCamera(int videoid);

    public native int prepareCameraWithBase(int videoid, int camerabase);

    public native void processCamera();

    public native void stopCamera();

    public native void pixeltobmp(Bitmap bitmap);

    static {
        System.loadLibrary("ImageProc");
    }

    public CameraPreview(Context context) {
        super(context);
        this.context = context;
        if (DEBUG) Log.d(TAG, "CameraPreview constructed");
        setFocusable(true);

        holder = getHolder();
        holder.addCallback(this);
        holder.setType(SurfaceHolder.SURFACE_TYPE_NORMAL);
    }


    public CameraPreview(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
        if (DEBUG) Log.d(TAG, "CameraPreview constructed");
        setFocusable(true);

        holder = getHolder();
        holder.addCallback(this);
        holder.setType(SurfaceHolder.SURFACE_TYPE_NORMAL);
    }

    private void flow() throws IOException {
        final MediaMuxer mediaMuxer = new MediaMuxer(Environment.getExternalStorageDirectory().getAbsolutePath() + "/test.mp4", MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

        MediaFormat mediaFormat = MediaFormat.createVideoFormat("video/avc", 640, 480);
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 1300 * 1000);
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 25);

        mediaCodec = MediaCodec.createEncoderByType("video/avc");
        mediaCodec.setCallback(new MediaCodec.Callback() {
            int videoTrack;
            long curentTime = System.nanoTime() / 1000;
            @Override
            public void onInputBufferAvailable(MediaCodec codec, int index) {

            }

            @Override
            public void onOutputBufferAvailable(MediaCodec codec, int index, MediaCodec.BufferInfo info) {
                ByteBuffer outputBuffer = codec.getOutputBuffer(index);
                Log.i(TAG," before presentationTimeUs:" + info.presentationTimeUs);
                if(info.presentationTimeUs != 0){
                    info.presentationTimeUs = info.presentationTimeUs - curentTime;
                }
                Log.i(TAG,"curentTime:" + curentTime);
                Log.i(TAG,"after presentationTimeUs:" + info.presentationTimeUs);
                mediaMuxer.writeSampleData(videoTrack, outputBuffer, info);
                codec.releaseOutputBuffer(index, false);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    mediaCodec.release();
                    mediaMuxer.release();
                    //为了方便看出流程没有写全局变量，这里应该释放
//                    inputSurface.release();
                }

            }

            @Override
            public void onError(MediaCodec codec, MediaCodec.CodecException e) {

            }

            @Override
            public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {
                videoTrack = mediaMuxer.addTrack(format);
                mediaMuxer.start();
            }
        });
        mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
//        methoh 1
        //必须在configure之后，start之前
//        inputSurface = mediaCodec.createInputSurface();

       // method 2
        inputSurface = MediaCodec.createPersistentInputSurface();

        //必须在configure之后，start之前
        mediaCodec.setInputSurface(inputSurface);
        mediaCodec.start();
    }

    @Override
    public void run() {
        Log.i(TAG, "cameraExists:" + cameraExists);
        Rect rect1 = null;
        while (true && cameraExists) {
            //obtaining display area to draw a large image
            if (winWidth == 0) {
                winWidth = this.getWidth();
                winHeight = this.getHeight();
                Log.i(TAG,"winWidth:" + winWidth + ";winHeight:" + winHeight);

                if (winWidth * 3 / 4 <= winHeight) {
                    dw = 0;
                    dh = (winHeight - winWidth * 3 / 4) / 2;
                    rate = ((float) winWidth) / IMG_WIDTH;
                    rect = new Rect(dw, dh, dw + winWidth - 1, dh + winWidth * 3 / 4 - 1);
                    rect1 = new Rect(dw, dh, dw + winWidth - 1, dh + winWidth * 3 / 4 - 1);
                } else {
                    dw = (winWidth - winHeight * 4 / 3) / 2;
                    dh = 0;
                    rate = ((float) winHeight) / IMG_HEIGHT;
                    rect = new Rect(dw, dh, dw + winHeight * 4 / 3 - 1, dh + winHeight - 1);
                    rect1 = new Rect(dw, dh, dw + winHeight * 4 / 3 - 1, dh + winHeight - 1);
                }
            }

            // obtaining a camera image (pixel data are stored in an array in JNI).
            processCamera();
            // camera image to bmp
            pixeltobmp(bmp);

            Canvas canvas = inputSurface.lockCanvas(rect);
            if (canvas != null) {
                // draw camera bmp on canvas
                canvas.drawBitmap(bmp, null, rect, null);

                inputSurface.unlockCanvasAndPost(canvas);
            }

            Canvas canvas1 = getHolder().lockCanvas(rect1);
            if(canvas1 != null){
                canvas1.drawBitmap(bmp, null, rect1, null);
                getHolder().unlockCanvasAndPost(canvas1);
            }

            if (shouldStop) {
                shouldStop = false;
                break;
            }
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (DEBUG) Log.d(TAG, "surfaceCreated");
        if (bmp == null) {
            bmp = Bitmap.createBitmap(IMG_WIDTH, IMG_HEIGHT, Bitmap.Config.ARGB_8888);
        }
        // /dev/videox (x=cameraId + cameraBase) is used
        int ret = prepareCameraWithBase(cameraId, cameraBase);

        if (ret != -1) cameraExists = true;

        try {
            flow();
        } catch (IOException e) {
            e.printStackTrace();
        }

        mainLoop = new Thread(this);
        mainLoop.start();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (DEBUG) Log.d(TAG, "surfaceChanged");
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (DEBUG) Log.d(TAG, "surfaceDestroyed");
        if (cameraExists) {
            shouldStop = true;
            while (shouldStop) {
                try {
                    Thread.sleep(100); // wait for thread stopping
                } catch (Exception e) {
                }
            }
        }
        stopCamera();
        mediaCodec.signalEndOfInputStream();
    }
}
