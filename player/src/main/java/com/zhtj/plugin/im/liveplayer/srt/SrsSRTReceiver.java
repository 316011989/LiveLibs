
package com.zhtj.plugin.im.liveplayer.srt;

import android.os.SystemClock;
import android.util.Log;

//multicast


/**
 * Srs implementation of an RTMP publisher
 *
 * @author francois, leoma
 */
public class SrsSRTReceiver {

    //multicast
    private final JniPull mSrt = new JniPull();
    private static final String TAG = SrsSRTReceiver.class.getSimpleName();
    private boolean mConnected = false;
    private static int mInitCount = -1;
    protected SLSTSDemuxer mTSDemuxer = null;

    private String mNetURL;

    public boolean open(String url) {
        mNetURL = url;
        init();
        if (!mConnected) {
            mConnected = mSrt.open(url);
        }
        return mConnected;
    }


    public void close() {
        if (mSrt != null && mConnected) {
            mSrt.close();
            mConnected = false;
        }
        uninit();
    }


    public int state() {
        if (mSrt != null) {
            return mSrt.state();
        }
        return -1;
    }

    private int init() {
        mInitCount++;
        if (mInitCount == 0) {
            int ret = mSrt.srtStartup();
            if (ret != 0) {
                Log.i(TAG, "srtStartup faild, ret" + ret);
                return -1;
            }
        }
        return mInitCount;
    }

    private int uninit() {
        if (mInitCount == -1) {
            return -1;
        }
        if (mInitCount > 0) {
            mInitCount--;
        }

        if (mInitCount == 0) {
            int ret = mSrt.srtCleanup();
            if (ret != 0) {
                Log.i(TAG, "srtStartup faild, ret" + ret);
                return -1;
            }
        }
        return mInitCount;
    }

    public void setDemuxer(SLSTSDemuxer demuxer) {
        mTSDemuxer = demuxer;
    }

    public boolean startRecv() {
        new Thread() {
            @Override
            public void run() {
                super.run();
                RecvData();
            }
        }.start();
        return true;
    }

    public boolean RecvData() {

        if (mSrt == null) {
            Log.i(TAG, "open url faild, " + mNetURL);
            return false;
        }

        while (mConnected) {
            byte[] data = mSrt.recv();
            if (data == null) {
                //*
                if (mSrt.state() != 5) {//SRTS_CONNECTED)
                    mSrt.close();
                    mSrt.open(mNetURL);
                    SystemClock.sleep(1000);
                }
                //*/
                SystemClock.sleep(10);
                continue;
            }
            if (mTSDemuxer != null) {
                //Log.i(TAG, "startRecvData len=" + data.length);
                mTSDemuxer.addTSPack(data);
            }
        }
        return true;
    }

    public boolean stop() {
        return false;
    }
}
