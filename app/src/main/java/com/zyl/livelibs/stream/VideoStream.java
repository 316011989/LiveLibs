package com.zyl.livelibs.stream;

import android.app.Activity;
import android.content.Context;
import android.hardware.Camera;
import android.view.SurfaceHolder;
import android.view.View;

import com.zyl.livelibs.camera.CameraHelper;
import com.zyl.livelibs.listener.OnFrameDataCallback;
import com.zyl.livelibs.param.VideoParam;

public class VideoStream extends VideoStreamBase implements Camera.PreviewCallback,
        CameraHelper.OnChangedSizeListener {

    private final OnFrameDataCallback mCallback;
    private final CameraHelper cameraHelper;
    private final int mBitrate;
    private final int mFrameRate;
    private boolean isLiving;
    private int previewWidth;
    private int previewHeight;
    private int rotateDegree = 90;

    public VideoStream(OnFrameDataCallback callback,
                       View view,
                       VideoParam videoParam,
                       Context context) {
        mCallback    = callback;
        mBitrate     = videoParam.getBitRate();
        mFrameRate   = videoParam.getFrameRate();
        cameraHelper = new CameraHelper((Activity) context,
                                        videoParam.getCameraId(),
                                        videoParam.getWidth(),
                                        videoParam.getHeight());
        cameraHelper.setPreviewCallback(this);
        cameraHelper.setOnChangedSizeListener(this);
    }

    @Override
    public void setPreviewDisplay(SurfaceHolder surfaceHolder) {
        cameraHelper.setPreviewDisplay(surfaceHolder);
    }

    @Override
    public void switchCamera() {
        cameraHelper.switchCamera();
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
        cameraHelper.release();
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        if (isLiving && mCallback != null) {
            mCallback.onVideoFrame(data, 1);
        }
    }

    @Override
    public void onChanged(int w, int h) {
        previewWidth = w;
        previewHeight = h;
        updateVideoCodecInfo(w, h, rotateDegree);
    }

    @Override
    public void onPreviewDegreeChanged(int degree) {
        updateVideoCodecInfo(previewWidth, previewHeight, degree);
    }

    private void updateVideoCodecInfo(int width, int height, int degree) {
        if (degree == 90 || degree == 270) {
            int temp = width;
            width = height;
            height = temp;
        }
        if (mCallback != null) {
            mCallback.onVideoCodecInfo(width, height, mFrameRate, mBitrate);
        }
    }

}
