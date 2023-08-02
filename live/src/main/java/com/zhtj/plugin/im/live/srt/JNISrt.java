package com.zhtj.plugin.im.live.srt;

import android.util.Log;

public class JNISrt {
    private final String TAG = JNISrt.class.getSimpleName();

    JNISrt() {
        System.loadLibrary("live");
    }

    private long mSRT = 0;

    public boolean open(String url) {
        Log.i(TAG, "JNISRT: open" + url);
        mSRT = srtOpen(url);
        if (mSRT > 0)
            return true;
        return false;
    }

    public boolean close() {
        Log.i(TAG, "JNISRT: close");
        if (mSRT <= 0)
            return false;
        int ret = srtClose(mSRT);
        mSRT = 0;
        return ret == 0;
    }

    public int send(byte[] data) {
        if (mSRT <= 0)
            return 0;
        return srtSend(mSRT, data);
    }

    public byte[] recv() {
        Log.i(TAG, "JNISRT: recv");
        if (mSRT <= 0)
            return null;
        return srtRecv(mSRT);
    }

    public int state() {
        if (mSRT <= 0)
            return -1;
        return srtGetSockState(mSRT);
    }

    public static native int srtStartup();

    public static native int srtCleanup();


    public native long srtOpen(String url);

    public native int srtClose(long srt);

    public native int srtSend(long srt, byte[] data);

    public native byte[] srtRecv(long srt);

    public native int srtGetSockState(long srt);

    //tools
    public static native int yv12RotationAnti(byte[] src, byte[] dst, int w, int h, int angle);

}
