package com.zyl.livelibs;

import android.app.Application;

public class FFmpegApplication extends Application {

    private static FFmpegApplication context;

    static {
        System.loadLibrary("live");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        context = this;
    }

    public static FFmpegApplication getInstance() {
        return context;
    }


}
