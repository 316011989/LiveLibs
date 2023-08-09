
package com.zhtj.plugin.im.live.srt;

import android.util.Log;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

//multicast


/**
 * Srs implementation of an RTMP publisher
 *
 * @author francois, leoma
 */
public class SrsSRTPublisher{

    private AtomicInteger videoFrameCacheNumber = new AtomicInteger(10);
    //multicast
    private final JniPush mSrt = new JniPush();
    private static final String TAG = "SrsSRTPublisher";
    private boolean mConnected = false;
    private static int mInitCount = -1;

    public boolean open(String url) {
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

    public int send(ByteBuffer data) {
        int ret = -1;
        if (mSrt != null) {
            byte[] dst = byteBuffer2Byte(data);
            ret = mSrt.send(dst);
            data.flip();
        }
        return ret;
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

    public void setVideoResolution(int width, int height) {
        return;
    }

    public AtomicInteger getVideoFrameCacheNumber() {
        return videoFrameCacheNumber;
    }

    public byte[] byteBuffer2Byte(ByteBuffer byteBuffer){
        int len = byteBuffer.limit() - byteBuffer.position();
        byte[] bytes = new byte[len];

        if(byteBuffer.isReadOnly()){
            return null;
        }else {
            byteBuffer.get(bytes);
        }
        return bytes;
    }


}
