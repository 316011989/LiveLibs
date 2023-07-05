package com.zyl.live.stream;

import android.app.Activity;
import android.content.Context;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.TextureView;
import android.view.View;

import com.zyl.live.camera.Camera2Helper;
import com.zyl.live.camera.Camera2Listener;
import com.zyl.live.listener.OnFrameDataCallback;
import com.zyl.live.param.VideoParam;

/**
 * Pushing video stream: using Camera2
 * Created by frank on 2020/02/12.
 */
public class VideoStreamNew extends VideoStreamBase
        implements TextureView.SurfaceTextureListener, Camera2Listener {

    private static final String TAG = VideoStreamNew.class.getSimpleName();

    private int rotation = 0;
    private boolean isLiving;
    private Size previewSize;
    private final Context mContext;
    private Camera2Helper camera2Helper;
    private final VideoParam mVideoParam;
    private final TextureView mTextureView;
    private final OnFrameDataCallback mCallback;

    public VideoStreamNew(OnFrameDataCallback callback,
                          View view,
                          VideoParam videoParam,
                          Context context) {
        this.mCallback = callback;
        // just support TextureView now
        this.mTextureView = (TextureView) view;
        this.mVideoParam = videoParam;
        this.mContext = context;
        mTextureView.setSurfaceTextureListener(this);
    }

    /**
     * start previewing
     */
    private void startPreview() {
        if (mContext instanceof Activity) {
            rotation = ((Activity) mContext).getWindowManager().getDefaultDisplay().getRotation();
        }
        camera2Helper = new Camera2Helper.Builder()
                .cameraListener(this)
                .specificCameraId("" + mVideoParam.getCameraId())
                .context(mContext.getApplicationContext())
                .previewOn(mTextureView)
                .previewViewSize(new Point(mVideoParam.getWidth(), mVideoParam.getHeight()))
                .rotation(rotation)
                .rotateDegree(getPreviewDegree(rotation))
                .build();
        camera2Helper.start();
    }

    @Override
    public void setPreviewDisplay(SurfaceHolder surfaceHolder) {

    }

    @Override
    public void switchCamera() {
        if (camera2Helper != null) {
            camera2Helper.switchCamera();
        }
    }

    @Override
    public void startLive() {
        isLiving = true;
    }

    @Override
    public void stopLive() {
        isLiving = false;
    }

    @Override
    public void release() {
        if (camera2Helper != null) {
            camera2Helper.stop();
            camera2Helper.release();
            camera2Helper = null;
        }
    }

    /**
     * stop previewing
     */
    private void stopPreview() {
        if (camera2Helper != null) {
            camera2Helper.stop();
        }
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        Log.i(TAG, "onSurfaceTextureAvailable...");
        startPreview();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        Log.i(TAG, "onSurfaceTextureDestroyed...");
        stopPreview();
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
    }

    /**
     * Camera2 preview frame data
     *
     * @param yuvData data of yuv
     */
    @Override
    public void onPreviewFrame(byte[] yuvData) {
        if (isLiving && mCallback != null) {
            mCallback.onVideoFrame(yuvData, 2);
        }
    }

    private int getPreviewDegree(int rotation) {
        switch (rotation) {
            case Surface.ROTATION_0:
                return 90;
            case Surface.ROTATION_90:
                return 0;
            case Surface.ROTATION_180:
                return 270;
            case Surface.ROTATION_270:
                return 180;
            default:
                return -1;
        }
    }

    @Override
    public void onCameraOpened(Size previewSize, int displayOrientation) {
        Log.i(TAG, "onCameraOpened previewSize=" + previewSize.toString());
        this.previewSize = previewSize;
        updateVideoCodecInfo(getPreviewDegree(rotation));
    }

    @Override
    public void onCameraClosed() {
        Log.i(TAG, "onCameraClosed");
    }

    @Override
    public void onCameraError(Exception e) {
        Log.e(TAG, "onCameraError=" + e.toString());
    }

    @Override
    public void onPreviewDegreeChanged(int degree) {
        updateVideoCodecInfo(degree);
    }

    private void updateVideoCodecInfo(int degree) {
        if (camera2Helper != null) {//横屏进入项目时,camera2Helper还没有build
            camera2Helper.updatePreviewDegree(degree);
            if (mCallback != null && mVideoParam != null) {
                int width = previewSize.getWidth();
                int height = previewSize.getHeight();
                Log.i("john VideoCodecInfo", "width=" + width + ";height=" + height);
                if (degree == 90 || degree == 270) {
                    mCallback.onVideoCodecInfo(height, width, mVideoParam.getFrameRate(), mVideoParam.getBitRate());
                } else
                    mCallback.onVideoCodecInfo(width, height, mVideoParam.getFrameRate(), mVideoParam.getBitRate());
            }
        }
    }

}
