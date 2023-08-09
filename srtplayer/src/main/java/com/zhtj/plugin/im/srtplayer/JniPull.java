package com.zhtj.plugin.im.srtplayer;

import android.util.Log;

public class JniPull {
    private final String TAG = JniPull.class.getSimpleName();

    JniPull() {
        System.loadLibrary("JNISrt");
    }

    private long mSRT = 0;

    public boolean open(String url) {
        Log.i(TAG, "JNISRT: open " + url);
        mSRT = srtOpen(url);
        if (mSRT != 0 && mSRT != -1)
            return true;
        return false;
    }

    public boolean close() {
        Log.i(TAG, "JNISRT: close");
        if (mSRT == 0 || mSRT == -1)
            return false;
        int ret = srtClose(mSRT);
        mSRT = 0;
        return ret == 0;
    }


    public byte[] recv() {
        Log.i(TAG, "JNISRT: recv");
        if (mSRT == 0 || mSRT == -1)
            return null;
        return srtRecv(mSRT);
    }

    public int state() {
        if (mSRT == 0 || mSRT == -1)
            return -1;
        return srtGetSockState(mSRT);
    }

    public native int srtStartup();

    public native int srtCleanup();


    public native long srtOpen(String url);

    public native int srtClose(long srt);

    public native byte[] srtRecv(long srt);

    public native int srtGetSockState(long srt);

}
