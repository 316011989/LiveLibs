package com.zhtj.plugin.im.live.srt;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.SystemClock;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Edward.Wu on 2019/03/28.
 * to POST the h.264/avc annexb frame over multi cast.
 *
 * @see android.media.MediaMuxer https://developer.android.com/reference/android/media/MediaMuxer.html
 */
public class SrsTSMuxer {

    private volatile boolean connected = false;
    private SrsSRTPublisher mPublisher = null;
    //private RtmpHandler mHandler;

    private Thread worker;
    private final Object txFrameLock = new Object();

    private static final int PES_MAX_LEN = 2 * 1024 * 1024;
    private static final int SPSPPS_MAX_LEN = 256;
    private static final int TS_PACK_LEN = 188;
    private static final int TS_UDP_PACK_LEN = 7 * TS_PACK_LEN;
    private static final byte TS_SYNC_BYTE = 0x47;

    private static final int TS_90K_CLOCK = 90;
    private static final int TS_27M_CLOCK = 90 * 300;
    private static final int TS_PCR_DTS_DELAY = 500 * TS_90K_CLOCK;//ms
    private static final int TS_PATPMT_INTERVAL = 400 * TS_90K_CLOCK;//ms
    private static final int TS_SYSTIME_INTERVAL = 5000;//ms

    private static final int AAC_ADTS_HEADER_LEN = 7;//fixed


    private static final short TS_VIDEO_PID = 0x100;
    private static final short TS_AUDIO_PID = 0x101;
    private static final byte TS_VIDEO_SID = (byte) 0xe0;
    private static final byte TS_AUDIO_SID = (byte) 0xc0;

    private static final int VIDEO_TRACK = 100;
    private static final int AUDIO_TRACK = 101;

    private long mLastSendPATDTS = 0;
    private long mLastSendSysTime = 0;
    private byte mPATPMTCC = 0;

    private long mLastSendPCR = 0;


    private SrsRawH264Stream avc = new SrsRawH264Stream();
    private SrsAVCMedia mVideoMedia = new SrsAVCMedia();
    private SrsAACMedia mAudioMedia = new SrsAACMedia();

    private SrsUDPPackList mTSUDPPackList = new SrsUDPPackList();

    private static final String TAG = "SrsTSMuxer";

    private static byte TS_PAT_DATA[] = new byte[]{
            /* TS */
            (byte) 0x47, (byte) 0x40, (byte) 0x00, (byte) 0x10, (byte) 0x00,
            /* PSI */
            (byte) 0x00, (byte) 0xb0, (byte) 0x0d, (byte) 0x00, (byte) 0x01, (byte) 0xc1, (byte) 0x00, (byte) 0x00,
            /* PAT */
            (byte) 0x00, (byte) 0x01, (byte) 0xf0, (byte) 0x01,
            /* CRC */
            (byte) 0x2e, (byte) 0x70, (byte) 0x19, (byte) 0x05,
            /* stuffing 167 bytes */
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff
    };

    private static byte TS_PMT_DATA[] = new byte[]{
            /* TS */
            (byte) 0x47, (byte) 0x50, (byte) 0x01, (byte) 0x10, (byte) 0x00,
            /* PSI */
            (byte) 0x02, (byte) 0xb0, (byte) 0x17, (byte) 0x00, (byte) 0x01, (byte) 0xc1, (byte) 0x00, (byte) 0x00,
            /* PMT */
            (byte) 0xe1, (byte) 0x00,
            (byte) 0xf0, (byte) 0x00,
            (byte) 0x1b, (byte) 0xe1, (byte) 0x00, (byte) 0xf0, (byte) 0x00, /* h264 */
            (byte) 0x0f, (byte) 0xe1, (byte) 0x01, (byte) 0xf0, (byte) 0x00, /* aac */
            /*0x03, (byte)0xe1, (byte)0x01, (byte)0xf0, (byte)0x00,*/ /* mp3 */
            /* CRC */
            0x2f, (byte) 0x44, (byte) 0xb9, (byte) 0x9b, /* crc for aac */
            /* stuffing 157 bytes */
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff

    };

    private static byte TS_NULL_DATA[] = new byte[]{
            /* TS */
            (byte) 0x47, (byte) 0x5F, (byte) 0xFF, (byte) 0x10, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff
    };

    /**
     * constructor.
     */
    public SrsTSMuxer() {

    }

    void setPublisher(SrsSRTPublisher publisher) {
        mPublisher = publisher;
    }

    /**
     * get cached video frame number in mPublisher
     */
    public AtomicInteger getVideoFrameCacheNumber() {
        return mPublisher == null ? null : mPublisher.getVideoFrameCacheNumber();
    }

    /**
     * set video resolution for mPublisher
     *
     * @param width  width
     * @param height height
     */
    public void setVideoResolution(int width, int height) {
        if (mPublisher != null) {
            mPublisher.setVideoResolution(width, height);
        }
    }

    /**
     * Adds a track with the specified format.
     *
     * @param format The media format for the track.
     * @return The track index for this newly added track.
     */
    public int addTrack(MediaFormat format) {
        if (format.getString(MediaFormat.KEY_MIME).contentEquals(SrsEncoder.VCODEC)) {
            //ts.setVideoTrack(format);
            return VIDEO_TRACK;
        } else {
            //ts.setAudioTrack(format);
            return AUDIO_TRACK;
        }
    }


    private int sendTSPack(ByteBuffer pack) {
        if (pack == null || mPublisher == null) {
            return 0;
        }
        return mPublisher.send(pack);
    }

    private void reset() {
        mLastSendPATDTS = 0;
        mPATPMTCC = 0;
        mLastSendPCR = 0;

        mAudioMedia.reset();
        mVideoMedia.reset();
        mTSUDPPackList.reset();
    }

    /**
     * start to the remote server for remux.
     */
    public void start(final String netUrl) {
        reset();
        if (null == mPublisher) {
            return;
        }
        boolean connected = mPublisher.open(netUrl);

        worker = new Thread(() -> {
            while (!Thread.interrupted() && connected) {
                while (!mTSUDPPackList.isEmpty()) {
                    ByteBuffer tsUDPPack = mTSUDPPackList.poll();
                    int ret = sendTSPack(tsUDPPack);
                    if (ret <= 0) {
                        //send failure, reconnect
                        if (mPublisher.state() != 5) {
                            mPublisher.close();
                            mPublisher.open(netUrl);
                            SystemClock.sleep(1000);
                        }
                    }
                }
                // Waiting for next frame
                synchronized (txFrameLock) {
                    try {
                        // isEmpty() may take some time, so we set timeout to detect next frame
                        txFrameLock.wait(10);
                    } catch (InterruptedException ie) {
                        worker.interrupt();
                    }
                }
            }
        });
        worker.start();
    }

    /**
     * stop the muxer, disconnect RTMP connection.
     */
    public void stop() {
        mTSUDPPackList.clear();
        if (worker != null) {
            worker.interrupt();
            try {
                worker.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
                worker.interrupt();
            }
            worker = null;
        }
        Log.i(TAG, "SrsTSMuxer closed");

        mPublisher.close();
    }

    /**
     * send the annexb frame over RTMP.
     *
     * @param trackIndex The track index for this sample.
     * @param byteBuf    The encoded sample.
     * @param bufferInfo The buffer information related to this sample.
     */
    public void writeSampleData(int trackIndex, ByteBuffer byteBuf, MediaCodec.BufferInfo bufferInfo, long tm) {
        if (VIDEO_TRACK == trackIndex) {
            writeVideoSample(byteBuf, bufferInfo);
        } else {
            writeAudioSample(byteBuf, bufferInfo);
        }
    }

    /*
     * mux audio data
     * */
    public void writeAudioSample(final ByteBuffer bb, MediaCodec.BufferInfo bi) {
        //new
        long pcr = TS_90K_CLOCK * bi.presentationTimeUs / 1000;// for pcr
        long pts = pcr + TS_PCR_DTS_DELAY;
        //FIXME: need to consider dts of B frame

        mAudioMedia.mAudioFrame.real_pts = pts;
        mAudioMedia.mAudioFrame.duration = pts - mAudioMedia.mAudioFrame.pts;
        mAudioMedia.mAudioFrame.pts = pts;
        mAudioMedia.mAudioFrame.dts = pts;
        mAudioMedia.mAudioFrame.pcr = pcr;
        //old
//        long base = bi.presentationTimeUs / 1000;
//        long pts = TS_90K_CLOCK * base + TS_PCR_DTS_DELAY;
//
//        long d = pts - mAudioMedia.mAudioFrame.real_pts;
//        mAudioMedia.mAudioFrame.real_pts = pts;
//
//        //pts = mAudioMedia.mCalcPTS.PutPTS(pts);
//        mAudioMedia.mAudioFrame.duration = pts - mAudioMedia.mAudioFrame.pts;
//        mAudioMedia.mAudioFrame.pts = pts;
//        mAudioMedia.mAudioFrame.dts = pts;

//        Log.i(TAG, String.format("audio frame=%d, pts=%d(%d), real_pts=%d, duration=%d, real_duration=%d.",
//                mAudioMedia.mAudioFrame.index, pts, pts/TS_90K_CLOCK, mAudioMedia.mAudioFrame.real_pts,
//                mAudioMedia.mAudioFrame.duration/TS_90K_CLOCK, pcr/TS_90K_CLOCK));

        byte[] header = new byte[]{(byte) 0xFF, (byte) 0xF1, (byte) 0x50, (byte) 0x80, (byte) 0x2F, (byte) 0x7F, (byte) 0xFC};
        byte tmp = 0;

//        byte[] csd_0 = bb.array();
//        Log.i(TAG, String.format("audio frame, csd_0, 0x%x, 0x%x.", (int)csd_0[0], (int)csd_0[1]));

        byte aac_packet_type = 1; // 1 = AAC raw
        if (!mAudioMedia.aac_specific_config_got) {
            // @see aac-mp4a-format-ISO_IEC_14496-3+2001.pdf
            // AudioSpecificConfig (), page 33
            // 1.6.2.1 AudioSpecificConfig
            // audioObjectType; 5 bslbf
            tmp = bb.get(0);
            byte ch = (byte) (tmp & 0xf8);
            tmp = bb.get(1);
            // 3bits left.

            // samplingFrequencyIndex; 4 bslbf
            byte samplingFrequencyIndex = 0x04;
            if (mAudioMedia.asample_rate == SrsCodecAudioSampleRate.R22050) {
                samplingFrequencyIndex = 0x07;
            } else if (mAudioMedia.asample_rate == SrsCodecAudioSampleRate.R11025) {
                samplingFrequencyIndex = 0x0a;
            }
            ch |= (samplingFrequencyIndex >> 1) & 0x07;
            //mAudioMedia.audio_tag.put(ch, 2);

            ch = (byte) ((samplingFrequencyIndex << 7) & 0x80);
            // 7bits left.

            // channelConfiguration; 4 bslbf
            byte channelConfiguration = 1;
            if (mAudioMedia.achannel == 2) {
                channelConfiguration = 2;
            }
            ch |= (channelConfiguration << 3) & 0x78;
            // 3bits left.

            // GASpecificConfig(), page 451
            // 4.4.1 Decoder configuration (GASpecificConfig)
            // frameLengthFlag; 1 bslbf
            // dependsOnCoreCoder; 1 bslbf
            // extensionFlag; 1 bslbf

            mAudioMedia.aac_specific_config_got = true;
            aac_packet_type = 0; // 0 = AAC sequence header
/*
            Log.i(TAG, String.format("audio adts header:%x,%x,%x,%x,%x,%x,%x",
                    mAudioMedia.adts_header.get(0),
                    mAudioMedia.adts_header.get(1),
                    mAudioMedia.adts_header.get(2),
                    mAudioMedia.adts_header.get(3),
                    mAudioMedia.adts_header.get(4),
                    mAudioMedia.adts_header.get(5),
                    mAudioMedia.adts_header.get(6)
            ));
            */

        } else {
            writeAdtsHeader(header, bi.size + 7);
            mAudioMedia.mAudioFrame.data.put(header);
            //mAudioMedia.adts_header.flip();

            mAudioMedia.mAudioFrame.data.put(bb);

            //Log.i(TAG, String.format("audio frame=%d, pts=%d(%d), adts_header len=%d, bb.size=%d",
            //        mAudioMedia.mAudioFrame.index, pts, pts/TS_90K_CLOCK, mAudioMedia.adts_header.position(), bi.size ));

            //start to read data
            mAudioMedia.mAudioFrame.data.flip();
            mAudioMedia.mAudioFrame.len = mAudioMedia.mAudioFrame.data.remaining();
            //mux to TS
            SrsPESToTS(mAudioMedia.mAudioFrame);
            //clear the pes frame buffer
            mAudioMedia.mAudioFrame.data.clear();
            mAudioMedia.mAudioFrame.index++;
        }
    }

    private void writeAdtsHeader(byte[] frame, int frame_len) {
        int offset = 0;
        // adts sync word 0xfff (12-bit)
        frame[offset] = (byte) 0xff;
        frame[offset + 1] = (byte) 0xf0;
        // versioin 0 for MPEG-4, 1 for MPEG-2 (1-bit)
        frame[offset + 1] |= 0 << 3;
        // layer 0 (2-bit)
        frame[offset + 1] |= 0 << 1;
        // protection absent: 1 (1-bit)
        frame[offset + 1] |= 1;
        // profile: audio_object_type - 1 (2-bit)
        frame[offset + 2] = (SrsAacObjectType.AacLC - 1) << 6;
        // sampling frequency index: 4 (4-bit)
        frame[offset + 2] |= (4 & 0xf) << 2;
        // channel configuration (3-bit)
        frame[offset + 2] |= (2 & (byte) 0x4) >> 2;
        frame[offset + 3] = (byte) ((2 & (byte) 0x03) << 6);
        // original: 0 (1-bit)
        frame[offset + 3] |= 0 << 5;
        // home: 0 (1-bit)
        frame[offset + 3] |= 0 << 4;
        // copyright id bit: 0 (1-bit)
        frame[offset + 3] |= 0 << 3;
        // copyright id start: 0 (1-bit)
        frame[offset + 3] |= 0 << 2;
        // frame size (13-bit)
        frame[offset + 3] |= ((frame_len) & 0x1800) >> 11;
        frame[offset + 4] = (byte) (((frame_len) & 0x7f8) >> 3);
        frame[offset + 5] = (byte) (((frame_len) & 0x7) << 5);
        // buffer fullness (0x7ff for variable bitrate)
        frame[offset + 5] |= (byte) 0x1f;
        frame[offset + 6] = (byte) 0xfc;
        // number of data block (nb - 1)
        frame[offset + 6] |= 0x0;
    }


    public void writeVideoSample(final ByteBuffer bb, MediaCodec.BufferInfo bi) {
        long pcr = TS_90K_CLOCK * bi.presentationTimeUs / 1000;// for pcr
        long pts = pcr + TS_PCR_DTS_DELAY;
        //FIXME: need to consider dts of B frame

        mVideoMedia.mVideoFrame.real_pts = pts;
        mVideoMedia.mVideoFrame.duration = pts - mVideoMedia.mVideoFrame.pts;
        mVideoMedia.mVideoFrame.pts = pts;
        mVideoMedia.mVideoFrame.dts = pts;
        mVideoMedia.mVideoFrame.pcr = pcr;

//        Log.i(TAG, String.format("video frame=%d, pts=%d(%d), real_pts=%d, duration=%d, real_duration=%d.",
//                mVideoMedia.mVideoFrame.index, pts, pts/TS_90K_CLOCK, mVideoMedia.mVideoFrame.real_pts,
//                mVideoMedia.mVideoFrame.duration/TS_90K_CLOCK, pcr/TS_90K_CLOCK));

        // send each frame.
        while (bb.position() < bi.size) {

            ///*
            SrsPESFrame frame = avc.demuxAnnexb(bb, bi);

            // 5bits, 7.3.1 NAL unit syntax,
            // H.264-AVC-ISO_IEC_14496-10.pdf, page 44.
            // 7: SPS, 8: PPS, 5: I Frame, 1: P Frame
            int nal_unit_type = frame.nal_unit_type;

            // ignore the nalu type aud(9)
            if (nal_unit_type == SrsAvcNaluType.AccessUnitDelimiter) {
                continue;
            }

            // for sps
            if (avc.isSps(frame)) {
                if (!frame.data.equals(mVideoMedia.h264_sps.data)) {
                    mVideoMedia.h264_sps.data.put(frame.data);
                    mVideoMedia.h264_sps.data.flip();
                    mVideoMedia.h264_sps.b_changed = true;
                }
                continue;
            }

            // for pps
            if (avc.isPps(frame)) {
                if (!frame.data.equals(mVideoMedia.h264_pps.data)) {
                    mVideoMedia.h264_pps.data.put(frame.data);
                    mVideoMedia.h264_pps.data.flip();
                    mVideoMedia.h264_pps.b_changed = true;
                }
                continue;
            }
            // for IDR frame, the frame is keyframe.
            mVideoMedia.mVideoFrame.key_frame = false;
            if (nal_unit_type == SrsAvcNaluType.IDR || nal_unit_type == SrsAvcNaluType.NonIDR) {

                if (nal_unit_type == SrsAvcNaluType.IDR) {
                    mVideoMedia.mVideoFrame.key_frame = true;
                    //insert sps and pps in the front of the IDR
                    mVideoMedia.mVideoFrame.data.put(mVideoMedia.h264_sps.data);
                    mVideoMedia.h264_sps.data.flip();
                    mVideoMedia.mVideoFrame.data.put(mVideoMedia.h264_pps.data);
                    mVideoMedia.h264_pps.data.flip();
                }

                //pes frame data
                mVideoMedia.mVideoFrame.data.put(frame.data);

                //start to read data
                mVideoMedia.mVideoFrame.data.flip();
                mVideoMedia.mVideoFrame.len = mVideoMedia.mVideoFrame.data.remaining();
                //mux to TS
                SrsPESToTS(mVideoMedia.mVideoFrame);
                //clear the pes frame buffer
                mVideoMedia.mVideoFrame.data.clear();


                //Log.i(TAG, String.format("frame=%d, annexb mux %dB, len=%dB, nalu=%d, base=%d, pts=%d(%d), d=%d, pcr=%d(%d)",
                //        mVideoMedia.mVideoFrame.index, bi.size, frame.len, nal_unit_type, base, pts, pts/TS_90K_CLOCK, mVideoMedia.mVideoFrame.duration/TS_90K_CLOCK, pcr, pcr/TS_90K_CLOCK ));
                mVideoMedia.mVideoFrame.index++;

            } else {
                //other NAL
                //pes frame data
                //mVideoMedia.mVideoFrame.data.put(frame.data);
            }
        }
        //Log.w(TAG, String.format("TEST:writeSampleData, after writeVideoSample, pts=%dms, d=%dms", base, System.nanoTime() / 1000000 - base));

    }


    private static byte[] longToBytes(long v, boolean littleEndian) {
        long[] la = new long[1];
        la[0] = v;
        ByteBuffer bb = ByteBuffer.allocate(la.length * 8);
        if (littleEndian) {
            // ByteBuffer.order(ByteOrder) 方法指定字节序,即大小端模式(BIG_ENDIAN/LITTLE_ENDIAN)
            // ByteBuffer 默认为大端(BIG_ENDIAN)模式
            bb.order(ByteOrder.LITTLE_ENDIAN);
        }
        bb.asLongBuffer().put(la);
        return bb.array();
    }


    private synchronized void SrsPESToTS(SrsPESFrame pes_frame) {
        ByteBuffer ts_pack = ByteBuffer.allocateDirect(TS_PACK_LEN);
        ByteBuffer ts_header = ByteBuffer.allocateDirect(4);
        ByteBuffer pes_header = ByteBuffer.allocateDirect(19);//9(header)+5(pts)+5(dts)
        ByteBuffer adaptation_field = ByteBuffer.allocateDirect(TS_PACK_LEN - 4);
        byte[] dst;


        boolean first = true;
        byte temp;

        int header_size;
        int flags;

        int pes_size = 0;
        int body_size;
        int copy_size;
        int in_size;
        int stuff_size;
        int i = 0;

        //check pcr
        //if (pes_frame.dts - mLastSendPCR >= TS_PCR_INTERVAL) {
        //    ts_udp_pack.PutData(TS_PCR_DATA);
        //    mLastSendPCR = pes_frame.pcr ;
        //}


        //check patpmt interval
        if (pes_frame.dts - mLastSendPATDTS >= TS_PATPMT_INTERVAL) {
            TS_PAT_DATA[3] = (byte) (TS_PAT_DATA[3] & 0xF0 | mPATPMTCC);
            mTSUDPPackList.SrsPutTSData(ByteBuffer.wrap(TS_PAT_DATA));
            TS_PMT_DATA[3] = (byte) (TS_PMT_DATA[3] & 0xF0 | mPATPMTCC);
            mPATPMTCC += 1;
            mPATPMTCC &= 0x0F;
            mTSUDPPackList.SrsPutTSData(ByteBuffer.wrap(TS_PMT_DATA));
            mLastSendPATDTS = pes_frame.dts;
        }
        //check patpmt interval
        long tm = System.currentTimeMillis();
        if (tm - mLastSendSysTime >= TS_SYSTIME_INTERVAL) {
            byte[] sysTM = longToBytes(tm, false);
            TS_NULL_DATA[4] = sysTM[0];
            TS_NULL_DATA[5] = sysTM[1];
            TS_NULL_DATA[6] = sysTM[2];
            TS_NULL_DATA[7] = sysTM[3];
            TS_NULL_DATA[8] = sysTM[4];
            TS_NULL_DATA[9] = sysTM[5];
            TS_NULL_DATA[10] = sysTM[6];
            TS_NULL_DATA[11] = sysTM[7];
            mTSUDPPackList.SrsPutTSData(ByteBuffer.wrap(TS_NULL_DATA));
            mLastSendSysTime = tm;
            Log.i(TAG, String.format("SrsPESToTS net send tm=%d", tm));

        }

        while (pes_frame.data.hasRemaining()) {

            //ts header
            ts_header.put(TS_SYNC_BYTE);
            temp = (byte) (pes_frame.pid >> 8);
            if (first) {
                temp = (byte) (temp | 0x40);
            }
            ts_header.put(temp);
            ts_header.put((byte) pes_frame.pid);
            temp = (byte) (0x10 | pes_frame.cc);
            ts_header.put(temp);

            if (first) {

                if (pes_frame.key_frame) { //key frame
                    // pcr
                    ts_header.put(3, (byte) (ts_header.get(3) | 0x20));

                    adaptation_field.put((byte) 0x7);
                    adaptation_field.put((byte) 0x50);
                    SrsWritePCR(adaptation_field, pes_frame.pcr);
                    Log.i(TAG, String.format("frame=%d, write pcr=%d(%d ms)",
                            pes_frame.index, pes_frame.pcr, pes_frame.pcr / TS_90K_CLOCK));

                }

                //pes header
                pes_header.put((byte) 0x00);
                pes_header.put((byte) 0x00);
                pes_header.put((byte) 0x01);
                pes_header.put((byte) pes_frame.sid);

                header_size = 5;
                flags = 0x80;

                if (pes_frame.dts != pes_frame.pts) {
                    header_size += 5;
                    flags = flags | 0x40;/* DTS */
                }

                pes_size = pes_frame.len + header_size + 3;
                if (pes_size > 0xFFFF || pes_frame.sid == TS_VIDEO_SID || pes_frame.sid == TS_AUDIO_SID) {
                    pes_size = 0;
                }

                temp = (byte) (pes_size >> 8);
                pes_header.put(temp);
                temp = (byte) pes_size;
                pes_header.put(temp);
                pes_header.put((byte) 0x80);//
                pes_header.put((byte) flags);
                pes_header.put((byte) header_size);

                //write pts
                SrsWritePTS(pes_header, flags >> 6, pes_frame.pts);//*TS_90K_CLOCK);
                //Log.i(TAG, String.format("write pts frame=%d, nalu=%d, pts=%d, duration=%d",
                //        pes_frame.len, pes_frame.nal_unit_type, pes_frame.pts, pes_frame.duration));

                if (pes_frame.pts != pes_frame.dts) {
                    SrsWritePTS(pes_header, 1, pes_frame.dts);//*TS_90K_CLOCK);
                }

                first = false;
            }

            body_size = TS_PACK_LEN - ts_header.position() - adaptation_field.position() - pes_header.position();
            in_size = pes_frame.data.remaining();

            if (body_size > in_size) {
                stuff_size = body_size - in_size;

                if (adaptation_field.position() > 0) {
                    //has adaptation
                    adaptation_field.put(0, (byte) (adaptation_field.position() + stuff_size));
                    if (adaptation_field.remaining() < stuff_size)
                        Log.i(TAG, String.format("ts len = %d,  not 188.", ts_pack.position()));

                    for (i = 0; i < stuff_size; i++) {
                        adaptation_field.put((byte) 0xff);
                    }
                } else {
                    /* no adaptation */
                    temp = ts_header.get(3);
                    temp = (byte) (temp | 0x20);
                    ts_header.put(3, temp);

                    if (adaptation_field.remaining() < stuff_size)
                        Log.i(TAG, String.format("ts len = %d,  not 188.", ts_pack.position()));

                    adaptation_field.put((byte) (stuff_size - 1));
                    if (stuff_size >= 2) {
                        adaptation_field.put((byte) 0x0);

                        for (i = 0; i < stuff_size - 2; i++) {
                            adaptation_field.put((byte) 0xff);
                        }
                    }
                }
            }

            ts_header.flip();
            ts_pack.clear();
            ts_pack.put(ts_header);
            ts_header.clear();

            if (adaptation_field.position() > 0) {
                adaptation_field.flip();
                ts_pack.put(adaptation_field);
                adaptation_field.clear();
            }

            if (pes_header.position() > 0) {
                pes_header.flip();
                ts_pack.put(pes_header);
                pes_header.clear();
            }

            copy_size = Math.min(body_size, in_size);
            if (ts_pack.remaining() < copy_size)
                Log.i(TAG, String.format("ts len = %d,  not 188.", ts_pack.position()));

            dst = new byte[copy_size];
            pes_frame.data.get(dst, 0, copy_size);
            ts_pack.put(dst);

            ts_pack.flip();
            //INFO: check ts pack len

            temp = (byte) ((ts_pack.get(3) & 0x30) >> 4);

            pes_frame.ts_index++;
            pes_frame.cc = (byte) ((pes_frame.cc + 1) & 0x0F);

            mTSUDPPackList.SrsPutTSData(ts_pack);
            ts_pack.clear();
            mTSUDPPackList.ts_count++;
        }
        //clear data
        pes_frame.data.clear();
    }

    private void SrsWritePCR(ByteBuffer data, long pcr) {
        byte temp;
        temp = (byte) (pcr >> 25);
        data.put(temp);
        temp = (byte) (pcr >> 17);
        data.put(temp);
        temp = (byte) (pcr >> 9);
        data.put(temp);
        temp = (byte) (pcr >> 1);
        data.put(temp);
        temp = (byte) (pcr << 7 | 0x7e);
        data.put(temp);
        temp = 0;
        data.put(temp);

    }

    private void SrsWritePTS(ByteBuffer data, int fb, long pts) {

        long val = 0;
        byte b;
        short s;

        val = fb << 4 | (((pts >> 30) & 0x07) << 1) | 1;
        data.put((byte) val);

        val = (((pts >> 15) & 0x7fff) << 1) | 1;

        data.put((byte) (val >> 8));
        data.put((byte) (val));

        val = (((pts) & 0x7fff) << 1) | 1;
        data.put((byte) (val >> 8));
        data.put((byte) (val));

    }

    /**
     * the aac object type, for RTMP sequence header
     * for AudioSpecificConfig, @see aac-mp4a-format-ISO_IEC_14496-3+2001.pdf, page 33
     * for audioObjectType, @see aac-mp4a-format-ISO_IEC_14496-3+2001.pdf, page 23
     */
    private class SrsAacObjectType {
        public final static int Reserved = 0;

        // Table 1.1 – Audio Object Type definition
        // @see @see aac-mp4a-format-ISO_IEC_14496-3+2001.pdf, page 23
        public final static int AacMain = 1;
        public final static int AacLC = 2;
        public final static int AacSSR = 3;

        // AAC HE = LC+SBR
        public final static int AacHE = 5;
        // AAC HEv2 = LC+SBR+PS
        public final static int AacHEV2 = 29;
    }

    /**
     * the aac profile, for ADTS(HLS/TS)
     *
     * @see <a href="https://github.com/simple-rtmp-server/srs/issues/310">...</a>
     */
    private class SrsAacProfile {
        public final static int Reserved = 3;

        // @see 7.1 Profiles, aac-iso-13818-7.pdf, page 40
        public final static int Main = 0;
        public final static int LC = 1;
        public final static int SSR = 2;
    }

    /**
     * the FLV/RTMP supported audio sample rate.
     * Sampling rate. The following values are defined:
     * 0 = 5.5 kHz = 5512 Hz
     * 1 = 11 kHz = 11025 Hz
     * 2 = 22 kHz = 22050 Hz
     * 3 = 44 kHz = 44100 Hz
     */
    private class SrsCodecAudioSampleRate {
        // set to the max value to reserved, for array map.
        public final static int Reserved = 4;

        public final static int R5512 = 0;
        public final static int R11025 = 1;
        public final static int R22050 = 2;
        public final static int R44100 = 3;
    }


    /**
     * Table 7-1 – NAL unit type codes, syntax element categories, and NAL unit type classes
     * H.264-AVC-ISO_IEC_14496-10-2012.pdf, page 83.
     */
    private class SrsAvcNaluType {
        // Unspecified
        public final static int Reserved = 0;

        // Coded slice of a non-IDR picture slice_layer_without_partitioning_rbsp( )
        public final static int NonIDR = 1;
        // Coded slice data partition A slice_data_partition_a_layer_rbsp( )
        public final static int DataPartitionA = 2;
        // Coded slice data partition B slice_data_partition_b_layer_rbsp( )
        public final static int DataPartitionB = 3;
        // Coded slice data partition C slice_data_partition_c_layer_rbsp( )
        public final static int DataPartitionC = 4;
        // Coded slice of an IDR picture slice_layer_without_partitioning_rbsp( )
        public final static int IDR = 5;
        // Supplemental enhancement information (SEI) sei_rbsp( )
        public final static int SEI = 6;
        // Sequence parameter set seq_parameter_set_rbsp( )
        public final static int SPS = 7;
        // Picture parameter set pic_parameter_set_rbsp( )
        public final static int PPS = 8;
        // Access unit delimiter access_unit_delimiter_rbsp( )
        public final static int AccessUnitDelimiter = 9;
        // End of sequence end_of_seq_rbsp( )
        public final static int EOSequence = 10;
        // End of stream end_of_stream_rbsp( )
        public final static int EOStream = 11;
        // Filler data filler_data_rbsp( )
        public final static int FilterData = 12;
        // Sequence parameter set extension seq_parameter_set_extension_rbsp( )
        public final static int SPSExt = 13;
        // Prefix NAL unit prefix_nal_unit_rbsp( )
        public final static int PrefixNALU = 14;
        // Subset sequence parameter set subset_seq_parameter_set_rbsp( )
        public final static int SubsetSPS = 15;
        // Coded slice of an auxiliary coded picture without partitioning slice_layer_without_partitioning_rbsp( )
        public final static int LayerWithoutPartition = 19;
        // Coded slice extension slice_layer_extension_rbsp( )
        public final static int CodedSliceExt = 20;
    }

    /**
     * the search result for annexb.
     */
    private class SrsAnnexbSearch {
        public int nb_start_code = 0;
        public int nal_unit_type = 0;
        public boolean match = false;
    }

    private class SrsTSUdpPack {
        private ByteBuffer ts_udp_pack = ByteBuffer.allocateDirect(TS_UDP_PACK_LEN);

        boolean PutData(ByteBuffer src) {
            if (src.remaining() != TS_PACK_LEN) {
                Log.i(TAG, String.format("PutData error, ByteBuffer src len = %d, not 188.", src.remaining()));
                return false;
            }
            if (ts_udp_pack.remaining() >= TS_PACK_LEN) {
                ts_udp_pack.put(src);
                return true;
            }

            Log.i(TAG, String.format("PutData error, ts_udp_pack.remainder len = %d, < 188.", ts_udp_pack.remaining()));
            return false;
        }

        ByteBuffer GetData() {
            return ts_udp_pack;
        }

        boolean IsFull() {
            //Log.i(TAG, String.format("IsFull, position=%d, limit=%d.", ts_udp_pack.position(), ts_udp_pack.limit()));
            return ts_udp_pack.position() == ts_udp_pack.limit();
        }

    }

    private class SrsUDPPackList {

        private ConcurrentLinkedQueue<ByteBuffer> udp_packs = new ConcurrentLinkedQueue<>();
        public SrsTSUdpPack ts_udp_pack;
        private int ts_count = 0;

        private final Object publishLock = new Object();

        public boolean add(ByteBuffer data) {
            return udp_packs.add(data);
        }

        public ByteBuffer poll() {
            return udp_packs.poll();
        }

        public void clear() {
            udp_packs.clear();
        }

        public boolean isEmpty() {
            return udp_packs.isEmpty();
        }

        boolean SrsPutTSData(ByteBuffer ts_pack) {
            synchronized (publishLock) {//multiple thread for audio and video encoder

                if (null == ts_udp_pack) {
                    ts_udp_pack = new SrsTSUdpPack();
                }

                if (ts_udp_pack.IsFull()) {
                    ts_udp_pack.GetData().flip();
                    udp_packs.add(ts_udp_pack.GetData());
                    ts_udp_pack = new SrsTSUdpPack();
                }
                return ts_udp_pack.PutData(ts_pack);
            }
        }

        public void reset() {
            udp_packs.clear();
            if (null != ts_udp_pack) {
                ts_udp_pack.GetData().clear();
            }
        }

    }


    private class SrsAACMedia {
        public boolean aac_specific_config_got;
        private SrsPESFrame mAudioFrame = new SrsPESFrame();
        public ByteBuffer adts_header = ByteBuffer.allocateDirect(AAC_ADTS_HEADER_LEN);

        private int achannel;
        private int asample_rate;

        public SrsCalcPTS mCalcPTS = new SrsCalcPTS();

        public SrsAACMedia() {
            mAudioFrame.pid = TS_AUDIO_PID;
            mAudioFrame.sid = TS_AUDIO_SID;

        }

        public void reset() {
            mAudioFrame.reset();
            mCalcPTS.reset();
        }


    }


    private class SrsAVCMedia {
        private SrsAVCSpsPps h264_sps = new SrsAVCSpsPps();
        private SrsAVCSpsPps h264_pps = new SrsAVCSpsPps();
        private SrsPESFrame mVideoFrame = new SrsPESFrame();

        public SrsCalcPTS mCalcPTS = new SrsCalcPTS();

        public SrsAVCMedia() {
            mVideoFrame.pid = TS_VIDEO_PID;
            mVideoFrame.sid = TS_VIDEO_SID;
        }

        public void reset() {
            mVideoFrame.reset();
            mCalcPTS.reset();
        }

    }

    /**
     * the demuxed tag frame.
     */
    private class SrsPESFrame {
        public ByteBuffer data = ByteBuffer.allocateDirect(PES_MAX_LEN);
        public int index = 0;
        public int ts_index = 0;
        public int len = 0;
        public int nal_unit_type;
        public boolean key_frame = false;


        long dts = 0;
        long pts = 0;
        long real_pts = 0;
        long pcr = 0;
        long duration = 0;

        byte cc = 0;
        short pid = 0;
        byte sid = 0;


        public void reset() {
            index = 0;
            ts_index = 0;
            len = 0;
            key_frame = false;


            dts = 0;
            pts = 0;
            real_pts = 0;
            pcr = 0;
            duration = 0;

            cc = 0;

            data.clear();

        }

    }

    private class SrsCalcPTS {
        private List<Long> pts_list = new ArrayList<Long>();
        private long pts_d_sum = 0;
        private final int max_count = 5000;
        private long last_real_pts = 0;
        private long last_pts = 0;

        private boolean b_start = false;

        public void reset() {
            pts_d_sum = 0;
            last_real_pts = 0;
            last_pts = 0;
            b_start = false;

            pts_list.clear();
        }

        public long PutPTS(long pts) {
            long temp = 0;

            if (!b_start) {
                if (pts_list.size() == 0) {
                    last_real_pts = pts;
                    last_pts = pts;
                    b_start = true;
                    return pts;
                }
            }
            if (pts_list.size() >= max_count) {
                temp = pts_list.remove(0);
                pts_d_sum -= temp;
            }

            temp = pts - last_real_pts;
            last_real_pts = pts;

            pts_list.add(temp);
            pts_d_sum += temp;

            temp = pts_d_sum / pts_list.size();
            last_pts += temp;
            return last_pts;

        }
    }


    private class SrsAVCSpsPps {
        public ByteBuffer data = ByteBuffer.allocateDirect(SPSPPS_MAX_LEN);
        public boolean b_changed = true;
    }


    private class SrsRawH264Stream {
        private final static String TAG = "SrsFlvMuxer";

        private SrsAnnexbSearch annexb = new SrsAnnexbSearch();

        public boolean isSps(SrsPESFrame frame) {
            return frame.len >= 1 && frame.nal_unit_type == SrsAvcNaluType.SPS;
        }

        public boolean isPps(SrsPESFrame frame) {
            return frame.len >= 1 && frame.nal_unit_type == SrsAvcNaluType.PPS;
        }


        private SrsAnnexbSearch searchAnnexb(ByteBuffer bb, MediaCodec.BufferInfo bi) {
            annexb.match = false;
            annexb.nb_start_code = 0;

            for (int i = bb.position(); i < bi.size - 3; i++) {
                // not match.
                if (bb.get(i) != 0x00 || bb.get(i + 1) != 0x00) {
                    break;
                }

                // match N[00] 00 00 01, where N>=0
                if (bb.get(i + 2) == 0x01) {
                    annexb.match = true;
                    annexb.nb_start_code = i + 3 - bb.position();
                    annexb.nal_unit_type = bb.get(i + 3) & 0x1f;
                    break;
                }
            }

            return annexb;
        }

        public SrsPESFrame demuxAnnexb(ByteBuffer bb, MediaCodec.BufferInfo bi) {
            SrsPESFrame tbb = new SrsPESFrame();

            while (bb.position() < bi.size) {
                // each frame must prefixed by annexb format.
                // about annexb, @see H.264-AVC-ISO_IEC_14496-10.pdf, page 211.
                SrsAnnexbSearch tbbsc = searchAnnexb(bb, bi);
                if (!tbbsc.match || tbbsc.nb_start_code < 3) {
                    Log.e(TAG, "annexb not match.");
                    //mHandler.notifyRtmpIllegalArgumentException(new IllegalArgumentException(
                    //    String.format("annexb not match for %dB, pos=%d", bi.size, bb.position())));
                }
                tbb.nal_unit_type = tbbsc.nal_unit_type;

                // find out the frame size.
                tbb.data = bb.slice();
                int pos = bb.position();

                // the start codes.
                for (int i = 0; i < tbbsc.nb_start_code; i++) {
                    bb.get();
                }

                while (bb.position() < bi.size) {
                    SrsAnnexbSearch bsc = searchAnnexb(bb, bi);
                    if (bsc.match) {
                        break;
                    }
                    bb.get();
                }

                tbb.len = bb.position() - pos;
                break;
            }

            return tbb;
        }
    }

}
