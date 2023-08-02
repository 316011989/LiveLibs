
package com.zhtj.plugin.im.live.srt;

import android.os.SystemClock;
import android.util.Log;

import java.nio.ByteBuffer;

//multicast


/**
 * Srs implementation of an RTMP publisher
 * 
 * @author francois, leoma
 */
public class SrsSRTPublisher extends SrsPublisher{

    //multicast
    private JNISrt mSrt = new JNISrt();
    private static final String TAG = "SrsSRTPublisher";
    private String mNetURL;
    private boolean mExit = false;
    private boolean mConnected = false;
    private static int mInitCount = -1;

    @Override
    public boolean open(String url) {
        mNetURL = url;
        init();
        super.open(url);
        mExit = false;
        if (!mConnected) {
            mConnected = mSrt.open(url);
        }
        return mConnected;
    }

    @Override
    public void close() {

        mExit = true;
        if (mSrt != null && mConnected){
            mSrt.close();
            mConnected = false;
        }
        super.close();
        uninit();
        return;

    }

    @Override
    public int send(ByteBuffer data) {
        int ret = -1;
        if (mSrt != null){
            byte[] dst = byteBuffer2Byte(data);
            ret = mSrt.send(dst);
            data.flip();
        }
        super.send(data);
        return ret;
    }


    @Override
    public int state()
    {
        if (mSrt != null){
            return mSrt.state();
        }
        return -1;
    }

    private static int init()
    {
        mInitCount ++;
        if (mInitCount == 0) {
            int ret = JNISrt.srtStartup();
            if (ret != 0) {
                Log.i(TAG, "srtStartup faild, ret" + ret);
                return -1;
            }
        }
        return mInitCount;
    }
    private static int uninit()
    {
        if (mInitCount == -1) {
            return -1;
        }
        if (mInitCount > 0) {
            mInitCount--;
        }

        if (mInitCount == 0) {
            int ret = JNISrt.srtCleanup();
            if (ret != 0) {
                Log.i(TAG, "srtStartup faild, ret" + ret);
                return -1;
            }
        }
        return mInitCount;
    }
    /**
     *
     */
    @Override
    public boolean RecvData() {

        if (mSrt == null) {
            Log.i(TAG, "open url faild, " + mNetURL);
            return false;
        }

        while (!mExit) {
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

}
