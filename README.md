# LiveLibs
参考Freya项目,编译ffmpeg,x264,fdkaac,librtmp,polarssl,libyuv
后续开发没有使用到librtmp,polarssl和fdkacc(替换为faac),所以去掉了这几个库

参考fanplayer项目,完成ffmpeg播放直播

20230707当前版本已经完成了rtmp协议的采集推流及播放流程

下一步要做其他协议,包含但不限于rtsp,srt,gb28181
封装格式音频要包含mp3和acc(现在用的就是)

