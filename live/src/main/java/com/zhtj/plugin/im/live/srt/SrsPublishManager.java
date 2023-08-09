package com.zhtj.plugin.im.live.srt;

import android.util.Log;


/**
 * Created by Leo Ma on 2016/7/25.
 */
public class SrsPublishManager {

    private final String TAG = SrsPublishManager.class.getSimpleName();

    protected SrsCameraView mCameraView;
    protected SrsAudioCapture mAudioCapture;

    protected boolean sendVideoOnly = false;
    protected boolean sendAudioOnly = false;
    protected int videoFrameCount;
    protected long lastTimeMillis;
    protected double mSamplingFps;

    protected SrsTSMuxer mTSMuxer = null;
    protected SrsEncoder mEncoder = null;
    protected SrsSRTPublisher mPublisher = null;

    private boolean mPublishing = false;

    public SrsPublishManager() {
    }

    public void setCameraView(SrsCameraView view) {
        mCameraView = view;
        mCameraView.setPreviewCallback((data, width, height) -> {
            calcSamplingFps();
            if (!sendAudioOnly) {
                mEncoder.onGetRgbaFrame(data, width, height);
            }
        });
    }

    public long getEncodedDuration() {
        if (mEncoder != null) {
            return mEncoder.getEncodedDuration();
        }
        return 0;
    }

    public boolean isPublishing() {
        return mPublishing;
    }

    public boolean start(String netUrl) {
        if (mPublishing)
            return false;

        if (null == mEncoder)
            mEncoder = new SrsEncoder();

        if (null == mTSMuxer) {
            mTSMuxer = new SrsTSMuxer();
        }


        startCamera();
        startAudio();
        startPublish(netUrl);
        startEncode();
        mPublishing = true;

        return true;
    }

    public boolean stop() {
        if (!mPublishing)
            return false;
        stopAudio();
        stopCamera();
        stopEncode();
        stopPublish();
        mPublishing = false;

        return true;
    }

    public boolean reset() {
        if (!mPublishing)
            return false;
        stopAudio();
        stopCamera();
        stopEncode();

        startCamera();
        startAudio();
        startEncode();
        return true;
    }

    private void calcSamplingFps() {
        // Calculate sampling FPS
        if (videoFrameCount == 0) {
            lastTimeMillis = System.nanoTime() / 1000000;
            videoFrameCount++;
        } else {
            if (++videoFrameCount >= SrsEncoder.VGOP) {
                long diffTimeMillis = System.nanoTime() / 1000000 - lastTimeMillis;
                mSamplingFps = (double) videoFrameCount * 1000 / diffTimeMillis;
                videoFrameCount = 0;
            }
        }
    }

    public void startCamera() {
        mCameraView.setEncoder(mEncoder);
        mCameraView.startCamera();
    }

    public void stopCamera() {

        mCameraView.stopCamera();
    }

    public void startAudio() {
        if (null == mAudioCapture)
            mAudioCapture = new SrsAudioCapture();
        mAudioCapture.setEncoder(mEncoder);
        mAudioCapture.startAudio();
    }

    public void stopAudio() {
        if (null != mAudioCapture)
            mAudioCapture.stopAudio();
    }

    public void startEncode() {
        mEncoder.setTSMuxer(mTSMuxer);
        mEncoder.setOutputResolution(mCameraView.getPreviewWidht(), mCameraView.getPreviewHeight());
        mEncoder.start();
    }

    public void stopEncode() {
        mEncoder.stop();
    }


    public void startPublish(String netUrl) {
        if (netUrl.startsWith("udp://")) {
//            mPublisher = new SrsMultiCastPublisher();
        } else if (netUrl.startsWith("srt://")) {
            mPublisher = new SrsSRTPublisher();
        } else {
            Log.i(TAG, String.format("wrong netUrl='%s'", netUrl));
            return;
        }
        if (mTSMuxer != null) {
            mTSMuxer.setPublisher(mPublisher);
            mTSMuxer.start(netUrl);
            mTSMuxer.setVideoResolution(mEncoder.getOutputWidth(), mEncoder.getOutputHeight());
        }
    }

    public void stopPublish() {
        stopEncode();
        if (mTSMuxer != null) {
            mTSMuxer.stop();
        }
    }


    public double getmSamplingFps() {
        return mSamplingFps;
    }

    public int getCamraId() {
        return mCameraView.getCameraId();
    }


    public void setSendAudioOnly(boolean flag) {
        sendAudioOnly = flag;
    }

    public void switchCameraFace() {
        mCameraView.stopCamera();
        mCameraView.switchCameraFace();
        /*
        if (id == 0) {
            mEncoder.setCameraBackFace();
        } else {
            mEncoder.setCameraFrontFace();
        }
        if (mEncoder != null && mEncoder.isEnabled()) {
            //mCameraView.enableEncoding();
        }
        */
        mCameraView.startCamera();
    }

}
