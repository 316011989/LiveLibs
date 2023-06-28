
#include <cstring>
#include <sstream>
#include <jni.h>
#include "AudioStream.h"
#include "PushInterface.h"

AudioStream::AudioStream() {

}

void AudioStream::setAudioCallback(AudioCallback callback) {
    audioCallback = callback;
}


int AudioStream::setAudioEncInfo(int samplesInHZ, int channels) {
    sampleRate = samplesInHZ;
    m_channels = channels;
    //分配编码器实例
    if (aacEncOpen(&aacEncoder, 0, MODE_1) != AACENC_OK) {
        LOGE("编码器实例分配失败");
        return -1;
    }
    //设置AOT参数，编码后的数据格式(AOT_ER_AAC_ELD),因为eld格式的不可以在本地直接播放所以可以先设置为lc本地测试一下,要注意这个AOT参数和下方的封装格式有一定的对应关系不要瞎写
//    aacEncoder_SetParam(aacEncoder, AACENC_AOT, AOT_AAC_LC);
    aacEncoder_SetParam(aacEncoder, AACENC_AOT, AOT_ER_AAC_ELD);
    //设置采样率
    aacEncoder_SetParam(aacEncoder, AACENC_SAMPLERATE, sampleRate);
    //设置声道数(单声道)
    aacEncoder_SetParam(aacEncoder, AACENC_CHANNELMODE, MODE_1);
    //设置声道顺序默认为1
    aacEncoder_SetParam(aacEncoder, AACENC_CHANNELORDER, 1);
    //设置比特率
    aacEncoder_SetParam(aacEncoder, AACENC_BITRATE, bitRate);
    //告诉编码器封装格式(TT_MP4_RAW表示封装为裸AAC码流)
//    aacEncoder_SetParam(aacEncoder, AACENC_TRANSMUX, TT_MP4_ADTS);
    aacEncoder_SetParam(aacEncoder, AACENC_TRANSMUX, TT_MP4_RAW);

    //初始化编码器实例
    if (aacEncEncode(aacEncoder, NULL, NULL, NULL, NULL) != AACENC_OK) {
        LOGE("初始化编码器实例失败");
        return -1;
    }
    //获取编码器实例信息
    if (aacEncInfo(aacEncoder, &info) != AACENC_OK) {
        LOGE("获取编码器实例信息失败");
        return -1;
    }
    //获取每帧通道采样点数(单通道是1024),这里要注意一下inputSize的长度一定要等于准备编码的PCM数据每次传过来的字节长度否则编码会出问题
    inputSize = 1 * 2 * info.frameLength;
    return 0;
}

int AudioStream::getInputSamples() const {
    return static_cast<int>(sampleRate);
}


void AudioStream::encodeData(JNIEnv *env, jbyte *pcmData) {
    //接收音频缓冲区大小,pcmLength的长度要与初始化中的inputSize 相等
    int in_size = inputSize;
    //接收音频buffer
    void *inBuffers = pcmData;
    //编码器输入缓冲区
    AACENC_BufDesc in_buf = {0};
    //编码器输出缓冲区
    AACENC_BufDesc out_buf = {0};
    //编码器的输入参数
    AACENC_InArgs in_args = {0};
    //编码器的输出参数
    AACENC_OutArgs out_args = {0};
    //缓冲区描述标识符
    int in_identifier = IN_AUDIO_DATA;
    //一个采样点占两个字节
    int in_elem_size = 2;
    //采样点个数
    in_args.numInSamples = inputSize / 2;  //size为pcm字节数
    //缓冲区数量
    in_buf.numBufs = 1;
    //编码前数据指针
    in_buf.bufs = &inBuffers;
    //每个缓冲区元素的标识符
    in_buf.bufferIdentifiers = &in_identifier;
    //每个缓冲区的大小（以8位字节为单位）
    in_buf.bufSizes = &in_size;
    //每个缓冲区元素的大小（字节）
    in_buf.bufElSizes = &in_elem_size;
    //输出音频缓冲区大小
    int outBufferSize = 20480;
    //输出音频buffer
    int8_t outBuffer[outBufferSize];
    //输出音频标识符
    int outIdentifier = OUT_BITSTREAM_DATA;
    //输出缓冲区的大小
    int bufElSizes = 1;
    //输出buffer指针
    void *out_ptr = outBuffer;
    //缓冲区数量
    out_buf.numBufs = 1;
    //编码后数据指针
    out_buf.bufs = &out_ptr;
    //缓冲区标识符
    out_buf.bufferIdentifiers = &outIdentifier;
    //每个缓冲区大小
    out_buf.bufSizes = &outBufferSize;
    //每个缓冲区元素大小
    out_buf.bufElSizes = &bufElSizes;
    //开始编码
    int ret = aacEncEncode(aacEncoder, &in_buf, &out_buf, &in_args, &out_args);
    if (ret == AACENC_ENCODE_EOF) {
        LOGE("编码结束,已到达文件末尾");
    } else if (ret == AACENC_OK) {
        //得到结果,调用java方法输出
        jbyteArray aacArray = env->NewByteArray(out_args.numOutBytes);
        jsize byteLen = (*env).GetArrayLength(aacArray);
        LOGE("编码pcm长度:%d", byteLen);
        int bodySize = 2 + byteLen;
        auto *packet = new RTMPPacket();
        RTMPPacket_Alloc(packet, bodySize);
        //stereo
        packet->m_body[0] = 0xAF;
        if (m_channels == 1) {
            packet->m_body[0] = 0xAE;
        }

        packet->m_body[1] = 0x01;
        memcpy(&packet->m_body[2], out_buf.bufs, static_cast<size_t>(byteLen));

        packet->m_hasAbsTimestamp = 0;
        packet->m_nBodySize       = bodySize;
        packet->m_packetType      = RTMP_PACKET_TYPE_AUDIO;
        packet->m_nChannel        = 0x11;
        packet->m_headerType      = RTMP_PACKET_SIZE_LARGE;
        audioCallback(packet);
    } else {
        LOGE("失败");
    }
}


AudioStream::~AudioStream() {
    if (aacEncoder == nullptr) {
        aacEncClose(&aacEncoder);
    }
}



