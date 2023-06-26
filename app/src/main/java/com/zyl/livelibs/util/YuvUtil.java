package com.zyl.livelibs.util;

public class YuvUtil {

    public native void NV21toI420(byte[] src, byte[] dest, int width, int height);
    public native void rotate(byte[] src, byte[] dest, int width, int height,int rotation);
    public static native void NV21ToI420andRotate90(byte[] src, byte[] dest, int width, int height);
}
