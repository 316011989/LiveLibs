package com.zhtj.plugin.im;

public class Constans {

    //推流地址
    public static final String LIVE_URL = "rtmp://192.168.10.115:32497/live/user_token";//内网服务器
//    public static final String LIVE_URL = "rtmp://124.71.38.39:1935/live/user_token";//华为服务器


    //播放地址
    public static final String STREAM_URL = "rtmp://192.168.10.115:32497/live/user_token";//rtmp播放地址
//    public static final String STREAM_URL = "http://192.168.10.115:8080/live/user_token.flv";//http flv播放地址
//     public static final String STREAM_URL ="http://192.168.10.115:8080/live/user_token.m3u8"
//    public static final String STREAM_URL = "rtmp://124.71.38.39:1935/live/user_token";//华为服务器

    //http live stream播放地址

    public static final String PLAYER_INIT_PARAMS =
            "video_hwaccel=1;" +
                    "init_timeout=2000;" +
                    "auto_reconnect=2000;" +
                    "audio_bufpktn=40;" +
                    "video_bufpktn=10;" +
//                    "video_frame_rate=30;" +
//                    "video_stream_total=1;" +
//                    "video_thread_count=2;" +
//                    "video_deinterlace=1;" +
//                    "audio_channels=2;" +
//                    "audio_sample_rate=44100;" +
//                    "audio_stream_total=1;" +
//                    "open_syncmode=1;" +
//                    "rtsp_transport=2;" +
                    "";

}
