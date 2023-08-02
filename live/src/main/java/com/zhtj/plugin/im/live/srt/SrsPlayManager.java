package com.zhtj.plugin.im.live.srt;

import android.util.Log;


/**
 * Created by Leo Ma on 2016/7/25.
 */
public class SrsPlayManager {

    private final String TAG = SrsPlayManager.class.getSimpleName();

    private boolean mExit = false;
    private SLSTSDemuxer mTSDemuxer = new SLSTSDemuxer();
    private SLSSurfaceView mSurfaceView = null;
    private SLSMediaCodec mVideoDecoder = new SLSVideoDecoder();
    private SLSMediaCodec mAudioDecoder = new SLSAudioDecoder();
    private SrsPublisher mDataReceiver = null;

    private boolean mPlaying = false;

    public SrsPlayManager() {
    }

    public void setSurfaceView(SLSSurfaceView sv) {
        mSurfaceView = sv;
    }

    public long getNetDelay() {
        if (null != mTSDemuxer)
            return mTSDemuxer.getNetDelay();
        return 0;
    }

    public String getVideoSize() {
        if (null != mVideoDecoder)
            return mVideoDecoder.getVideoSize();
        return "";
    }

    public long getDecodedDuration() {
        if (null != mVideoDecoder)
            return mVideoDecoder.getDecodedDuration();
        return 0;
    }


    public boolean isPlaying() {
        return mPlaying;
    }

    public boolean start(String netUrl) {

        if (mPlaying)
            return false;

        if (mSurfaceView == null)
            return false;
        if (netUrl.substring(0, 6).equals("udp://")) {
            mDataReceiver = new SrsMultiCastPublisher();
        } else if (netUrl.substring(0, 6).equals("srt://")) {
            mDataReceiver = new SrsSRTPublisher();
        } else {
            Log.i(TAG, String.format("wrong netUrl='%s'", netUrl));
            return false;
        }
        //
        mVideoDecoder.setSurface(mSurfaceView.getSurface());
        mVideoDecoder.init();
        mAudioDecoder.init();
        mTSDemuxer.setVideoDecoder(mVideoDecoder);
        mTSDemuxer.setAudioDecoder(mAudioDecoder);
        mTSDemuxer.start();
        mDataReceiver.setDemuxer(mTSDemuxer);
        mDataReceiver.open(netUrl);
        mDataReceiver.startRecv();

        return true;
    }

    public boolean stop() {
        if (!mPlaying)
            return false;
        mExit = true;
        mDataReceiver.stop();
        mDataReceiver.close();
        mTSDemuxer.stop();
        ;
        mVideoDecoder.uninit();
        mAudioDecoder.uninit();
        mPlaying = false;

        return true;
    }

    public boolean reset() {
        if (!mPlaying)
            return false;
        mTSDemuxer.stop();
        ;
        mVideoDecoder.uninit();
        mAudioDecoder.uninit();

        mVideoDecoder.init();
        mAudioDecoder.init();
        mTSDemuxer.start();
        return true;

    }


}
