package com.zyl.live.util;

public class YuvUtil {

    public static native void NV21toI420andRotate(byte[] src, byte[] dest, int width, int height,int degree);
    public static native void NV21toI420(byte[] src, byte[] dest, int width, int height);
    public static native void I420Mirror(byte[] src, byte[] dest, int width, int height);
    public static native void Rotate(byte[] src, byte[] dest, int width, int height,int degree);
}
