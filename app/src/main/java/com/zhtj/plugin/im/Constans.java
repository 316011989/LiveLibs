package com.zhtj.plugin.im;

import android.provider.Settings;

public class Constans {

    String ANDROID_ID = Settings.System.getString(FFmpegApplication.getInstance().getContentResolver(), Settings.System.ANDROID_ID);

    //rtmp推流地址,'android'是流唯一编码,可以任意编写
    public static final String LIVE_URL = "rtmp://192.168.10.115:32497/live/android";


//    http://127.0.0.1:8080/live/sea.m3u8
//    public static final String LIVE_URL = "http://192.168.10.200:8936/live/android.flv";

    //rtmp播放地址
//    public static final String PLAY_URL = "rtmp://192.168.10.200:1935/live/android";

    //http flv播放地址
    public static final String PLAY_URL = "rtmp://192.168.10.115:32497/live/android";

    //http live stream播放地址
//    public static final String PLAY_URL = "http://192.168.10.200:8181/live/android.m3u8";

    public static final String PLAYER_INIT_PARAMS = "video_hwaccel=1;init_timeout=2000;auto_reconnect=2000;audio_bufpktn=4;video_bufpktn=1;rtsp_transport=2;";

}
