
#ifndef AUDIOSTREAM_H
#define AUDIOSTREAM_H

#include "fdk-aac/aacenc_lib.h"
#include "rtmp/rtmp.h"
#include <sys/types.h>

class AudioStream {
    typedef void (*AudioCallback)(RTMPPacket *packet);

private:
    AudioCallback audioCallback;
    int m_channels;
    HANDLE_AACENCODER aacEncoder;
    //编码器实例信息结构体
    AACENC_InfoStruct info = {0};
    //采样率
    int sampleRate = 8000;
    //码率
    int bitRate = 64000;
    //
    int inputSize;

public:
    AudioStream();

    ~AudioStream();

    int setAudioEncInfo(int samplesInHZ, int channels);

    void setAudioCallback(AudioCallback audioCallback);

    void encodeData(JNIEnv *env,int8_t *data);

    int getInputSamples() const;

};


#endif
