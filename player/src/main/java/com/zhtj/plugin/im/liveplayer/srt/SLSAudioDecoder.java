package com.zhtj.plugin.im.liveplayer.srt;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;


/**
 * @author lavender
 * @createtime 2018/5/24
 * @desc
 */

public class SLSAudioDecoder extends SLSMediaCodec {

    private MediaCodec audioDecoder;//音频解码器
    private static final String TAG = "AACDecoderUtil";
    //声道数
    private static final int KEY_CHANNEL_COUNT = 2;
    //采样率
    private static final int KEY_SAMPLE_RATE = 16000;
    private final Object obj = new Object();

    private Thread audioThread;
    private long prevPresentationTimes;

    private MediaCodec.BufferInfo encodeBufferInfo;
    private ByteBuffer[] encodeInputBuffers;
    private ByteBuffer[] encodeOutputBuffers;

    public static final String ACODEC = "audio/mp4a-latm";
    public static final int ASAMPLERATE = 44100;
    public static int aChannelConfig = AudioFormat.CHANNEL_IN_STEREO;
    public static final int ABITRATE = 128 * 1024;  // 128 kbps

    public boolean isStop = false;

    public SLSAudioDecoder() {
        prepare();

    }


    /**
     * 获取当前的时间戳
     *
     * @return
     */
    private long getPTSUs() {
        long result = System.nanoTime() / 1000;
        if (result < prevPresentationTimes) {
            result = (prevPresentationTimes - result) + result;
        }
        return result;
    }


    /**
     * 开启解码播放
     */
    @Override
    public void init() {
        isStop = false;
        prevPresentationTimes = 0;

        audioThread = new Thread(new AudioThread(),
                "Audio Thread");
        audioThread.start();
    }


    /**
     * 停止并且重启解码器以便下次使用
     */
    @Override
    public void uninit() {
        isStop = true;

        if (audioThread != null) {
            audioThread.interrupt();
            try {
                audioThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
                return;
            }
        }
        try {
            audioDecoder.stop();
            audioDecoder.release();
            prepare();
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
    }


    public class AudioThread implements Runnable {


        private AudioTrack audioTrack;
        private int buffSize = 0;
        private int bufferSizeInBytes = 0;
        int size = 0;


        public AudioThread() {

            bufferSizeInBytes = AudioTrack.getMinBufferSize(ASAMPLERATE, aChannelConfig, AudioFormat.ENCODING_PCM_16BIT);

            audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,//播放途径  外放
                    ASAMPLERATE,
                    AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSizeInBytes * 4,
                    AudioTrack.MODE_STREAM);
            //启动AudioTrack
            audioTrack.play();

        }

        @Override
        public void run() {
            while (!isStop) {

                if (mFrmList.isEmpty()) {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    continue;
                }
                DataInfo dataInfo = mFrmList.remove(0);
                if (dataInfo != null && dataInfo.mDataBytes != null)
                    decode(dataInfo.mDataBytes, 0, dataInfo.mDataBytes.length, audioTrack);
            }
        }
    }

    private int getPcmBufferSize() {
        int pcmBufSize = AudioRecord.getMinBufferSize(ASAMPLERATE, AudioFormat.CHANNEL_IN_STEREO,
                AudioFormat.ENCODING_PCM_16BIT) + 8191;
        return pcmBufSize - (pcmBufSize % 8192);
    }

    /**
     * 初始化音频解码器
     *
     * @return 初始化失败返回false，成功返回true
     */
    public boolean prepare() {
        // 初始化AudioTrack
        try {
            //初始化解码器
            audioDecoder = MediaCodec.createDecoderByType(ACODEC);

            //MediaFormat用于描述音视频数据的相关参数
            int ach = aChannelConfig == AudioFormat.CHANNEL_IN_STEREO ? 2 : 1;
            MediaFormat mediaFormat = MediaFormat.createAudioFormat(ACODEC, ASAMPLERATE, ach);
            //audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, ABITRATE);
            //audioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 0);
            //aencoder.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

            //MediaFormat mediaFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, 16000, 1);
            //数据类型
            mediaFormat.setString(MediaFormat.KEY_MIME, ACODEC);
            //采样率
            mediaFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, ASAMPLERATE);
            //声道个数
            mediaFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, ach);
            //mediaFormat.setInteger(MediaFormat.KEY_CHANNEL_MASK, AudioFormat.CHANNEL_IN_STEREO);
            //mediaFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 1024);//作用于inputBuffer的大小
            //比特率
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, ABITRATE);
            //用来标记AAC是否有adts头，1->有
            mediaFormat.setInteger(MediaFormat.KEY_IS_ADTS, 1);
            mediaFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            //ByteBuffer key（参数代表 单声道，16000采样率，AAC LC数据）
// *******************根据自己的音频数据修改data参数*************************************************
            /*
AAC Profile 5bits | 采样率 4bits | 声道数 4bits | 其他 3bits |

AAC Main 0x01

AAC LC 0x02

AAC SSR 0x03

采样率的参数为：

    0x00   96000
    0x01   88200
    0x02   64000
    0x03   48000
    0x04   44100
    0x05   32000
    0x06   24000
    0x07   22050
    0x08   16000
    0x09   12000
    0x0A   11025
    0x0B    8000
    0x0C   reserved
    0x0D   reserved
    0x0E   reserved
    0x0F   escape value

声道数：

    0x00 - defined in audioDecderSpecificConfig
    0x01 单声道（center front speaker）
    0x02 双声道（left, right front speakers）
    0x03 三声道（center, left, right front speakers）
    0x04 四声道（center, left, right front speakers, rear surround speakers）
    0x05 五声道（center, left, right front speakers, left surround, right surround rear speakers）
    0x06 5.1声道（center, left, right front speakers, left surround, right surround rear speakers, front low frequency effects speaker)
    0x07 7.1声道（center, left, right center front speakers, left, right outside front speakers, left surround, right surround rear speakers, front low frequency effects speaker)
    0x08-0x0F - reserved

    如果音频数据是：
      AAC-LC ,16000，单声道  ，参数分别是：0X02 0X08 0X01 0X00  取参数的后面两位，根据信息格式所占bit，换成二进制为：00010 1000 0001 000, 0x14, 0x08
      AAC-LC ,44100，双声道  ，参数分别是：0X02 0X04 0X02 0X00  取参数的后面两位，根据信息格式所占bit，换成二进制为：00010 0100 0010 000, 0x12, 0x10
             */
            byte[] data = new byte[]{(byte) 0x12, (byte) 0x10};
            ByteBuffer csd_0 = ByteBuffer.wrap(data);
            mediaFormat.setByteBuffer("csd-0", csd_0);
            //解码器配置
            audioDecoder.configure(mediaFormat, null, null, 0);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        if (audioDecoder == null) {
            return false;
        }
        audioDecoder.start();
        return true;
    }

    /**
     * aac音频解码+播放
     */
    public void decode(byte[] buf, int offset, int length, AudioTrack audioTrack) {
        //等待时间，0->不等待，-1->一直等待
        long kTimeOutUs = 0;
        encodeInputBuffers = audioDecoder.getInputBuffers();
        encodeOutputBuffers = audioDecoder.getOutputBuffers();
        try {
            //返回一个包含有效数据的input buffer的index,-1->不存在
            int inputBufIndex = audioDecoder.dequeueInputBuffer(kTimeOutUs);
            Log.i("inputBufIndex**", "\n" + inputBufIndex + "\n");
            if (inputBufIndex >= 0) {
                //获取当前的ByteBuffer
                ByteBuffer dstBuf = encodeInputBuffers[inputBufIndex];
                //清空ByteBuffer
                dstBuf.clear();
                //填充数据
                dstBuf.put(buf, 0, length);
                //将指定index的input buffer提交给解码器
                audioDecoder.queueInputBuffer(inputBufIndex, 0, length, getPTSUs(), 0);
            }

            encodeBufferInfo = new MediaCodec.BufferInfo();
            //返回一个output buffer的index，-1->不存在
            int outputBufferIndex = audioDecoder.dequeueOutputBuffer(encodeBufferInfo, kTimeOutUs);
            Log.i("outputBufferIndex**", "\n" + outputBufferIndex + "\n");
            ByteBuffer outputBuffer;
            while (outputBufferIndex > 0) {
                //获取解码后的ByteBuffer
                outputBuffer = encodeOutputBuffers[outputBufferIndex];
                //用来保存解码后的数据
                byte[] outData = new byte[encodeBufferInfo.size];
                outputBuffer.get(outData);
                //清空缓存
                outputBuffer.clear();

                Log.i("audioTrack--outData**", "\n" + outData.length + "\n");
                //播放解码后的数据
                audioTrack.write(outData, 0, outData.length);
                //释放已经解码的buffer
                audioDecoder.releaseOutputBuffer(outputBufferIndex, false);
                //解码未解完的数据
                outputBufferIndex = audioDecoder.dequeueOutputBuffer(encodeBufferInfo, kTimeOutUs);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
