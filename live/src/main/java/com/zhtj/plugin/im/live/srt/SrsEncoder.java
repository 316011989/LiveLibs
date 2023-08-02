package com.zhtj.plugin.im.live.srt;

import android.content.res.Configuration;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Leo Ma on 4/1/2016.
 */
public class SrsEncoder {
    private static final String TAG = "SrsEncoder";

    public static final String VCODEC = "video/avc";
    //public static final String VCODEC = "video/hevc";
    public static final String ACODEC = "audio/mp4a-latm";
    public static String x264Preset = "veryfast";
    public static int vPrevWidth = 640;
    public static int vPrevHeight = 360;
    public static int vPortraitWidth = 720;
    public static int vPortraitHeight = 1280;
    public static int vLandscapeWidth = 1280;
    public static int vLandscapeHeight = 720;
    public static int vOutWidth = 720;   // Note: the stride of resolution must be set as 16x for hard encoding with some chip like MTK
    public static int vOutHeight = 1280;  // Since Y component is quadruple size as U and V component, the stride must be set as 32x
    public static int vBitrate = 1200 * 1024;  // 1200 kbps
    public static final int VFPS = 20;
    public static final int VGOP = 40;
    public static final int ASAMPLERATE = 44100;
    public static int aChannelConfig = AudioFormat.CHANNEL_IN_STEREO;
    public static final int ABITRATE = 128 * 1024;  // 128 kbps

    //private SrsEncodeHandler mHandler;

    private SrsTSMuxer tsMuxer;

    private MediaCodecInfo vmci;
    private MediaCodec vencoder;
    private MediaCodec aencoder;
    private MediaCodec.BufferInfo vebi = new MediaCodec.BufferInfo();
    private MediaCodec.BufferInfo aebi = new MediaCodec.BufferInfo();


    private boolean networkWeakTriggered = false;
    private boolean mCameraFaceFront = true;
    private boolean useSoftEncoder = false;
    private boolean canSoftEncode = false;

    private long mPresentTimeUs;

    private int mVideoColorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar;

    private int videoFlvTrack;
    private int videoMp4Track;
    private int videoTSTrack;

    private int audioFlvTrack;
    private int audioMp4Track;
    private int audioTSTrack;

    private LinkedList<Long> mlistCaptureTime = new LinkedList<>();
    private long mTMEncodedDuration = 0;

    // Y, U (Cb) and V (Cr)
    // yuv420                     yuv yuv yuv yuv
    // yuv420p (planar)   yyyy*2 uu vv
    // yuv420sp(semi-planner)   yyyy*2 uv uv
    // I420 -> YUV420P   yyyy*2 uu vv
    // YV12 -> YUV420P   yyyy*2 vv uu
    // NV12 -> YUV420SP  yyyy*2 uv uv
    // NV21 -> YUV420SP  yyyy*2 vu vu
    // NV16 -> YUV422SP  yyyy uv uv
    // YUY2 -> YUV422SP  yuyv yuyv


    public SrsEncoder() {

    }

    public void setTSMuxer(SrsTSMuxer muxer) {
        tsMuxer = muxer;
    }

    public long getEncodedDuration() {
        return mTMEncodedDuration;
    }

    // choose the video encoder by name.
    private MediaCodecInfo chooseVideoEncoder(String name) {
        int nbCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < nbCodecs; i++) {
            MediaCodecInfo mci = MediaCodecList.getCodecInfoAt(i);
            if (!mci.isEncoder()) {
                continue;
            }

            String[] types = mci.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(VCODEC)) {
                    Log.i(TAG, String.format("vencoder %s types: %s", mci.getName(), types[j]));
                    if (name == null) {
                        return mci;
                    }

                    if (mci.getName().contains(name)) {
                        return mci;
                    }
                }
            }
        }

        return null;
    }



    public boolean start() {
        // the referent PTS for video and audio encoder.
        mPresentTimeUs = System.nanoTime() / 1000;

        // aencoder pcm to aac raw stream.
        // requires sdk level 16+, Android 4.1, 4.1.1, the JELLY_BEAN
        try {
            aencoder = MediaCodec.createEncoderByType(ACODEC);
        } catch (IOException e) {
            Log.e(TAG, "create aencoder failed.");
            e.printStackTrace();
            return false;
        }

        // setup the aencoder.
        // @see https://developer.android.com/reference/android/media/MediaCodec.html
        int ach = aChannelConfig == AudioFormat.CHANNEL_IN_STEREO ? 2 : 1;
        MediaFormat audioFormat = MediaFormat.createAudioFormat(ACODEC, ASAMPLERATE, ach);
        audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, ABITRATE);
        audioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 0);
        aencoder.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        // add the audio tracker to muxer.
        if (null != tsMuxer)
            audioTSTrack = tsMuxer.addTrack(audioFormat);


        // vencoder yuv to 264 es stream.
        // requires sdk level 16+, Android 4.1, 4.1.1, the JELLY_BEAN
        try {
            vencoder = MediaCodec.createEncoderByType(VCODEC);
        } catch (IOException e) {
            Log.e(TAG, "create vencoder failed.");
            e.printStackTrace();
            return false;
        }

        // setup the vencoder.
        // Note: landscape to portrait, 90 degree rotation, so we need to switch width and height in configuration
        //mVideoColorFormat = chooseVideoEncoder();
        MediaFormat videoFormat = MediaFormat.createVideoFormat(VCODEC, vOutWidth, vOutHeight);
        videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, mVideoColorFormat);
        videoFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 0);
        videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, vBitrate);
        videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, VFPS);
        videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, VGOP / VFPS);
        vencoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

        if (null != tsMuxer)
            // add the video tracker to muxer.
            videoTSTrack = tsMuxer.addTrack(videoFormat);

        // start device and encoder.
        vencoder.start();
        aencoder.start();
        return true;
    }

    public void stop() {
        if (useSoftEncoder) {
            //closeSoftEncoder();
            canSoftEncode = false;
        }

        if (aencoder != null) {
            Log.i(TAG, "stop aencoder");
            aencoder.stop();
            aencoder.release();
            aencoder = null;
        }

        if (vencoder != null) {
            Log.i(TAG, "stop vencoder");
            vencoder.stop();
            vencoder.release();
            vencoder = null;
        }
    }

    public void setCameraFrontFace() {
        mCameraFaceFront = true;
    }

    public void setCameraBackFace() {
        mCameraFaceFront = false;
    }

    public void setOutputResolution(int width, int height) {
        if (width <= height) {
            setPortraitResolution(width, height);
        } else {
            setLandscapeResolution(width, height);
        }
    }

    public void setPreviewResolution(int width, int height) {
        vPrevWidth = width;
        vPrevHeight = height;
    }

    public void setPortraitResolution(int width, int height) {
        vOutWidth = width;
        vOutHeight = height;
        vPortraitWidth = width;
        vPortraitHeight = height;
        vLandscapeWidth = height;
        vLandscapeHeight = width;
    }

    public void setLandscapeResolution(int width, int height) {
        vOutWidth = width;
        vOutHeight = height;
        vLandscapeWidth = width;
        vLandscapeHeight = height;
        vPortraitWidth = height;
        vPortraitHeight = width;
    }

    public void setVideoHDMode() {
        vBitrate = 1200 * 1024;  // 1200 kbps
        x264Preset = "veryfast";
    }

    public void setVideoSmoothMode() {
        vBitrate = 500 * 1024;  // 500 kbps
        x264Preset = "superfast";
    }

    public int getPreviewWidth() {
        return vPrevWidth;
    }

    public int getPreviewHeight() {
        return vPrevHeight;
    }

    public int getOutputWidth() {
        return vOutWidth;
    }

    public int getOutputHeight() {
        return vOutHeight;
    }

    public void setScreenOrientation(int orientation) {
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            vOutWidth = vPortraitWidth;
            vOutHeight = vPortraitHeight;
        } else if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            vOutWidth = vLandscapeWidth;
            vOutHeight = vLandscapeHeight;
        }

        // Note: the stride of resolution must be set as 16x for hard encoding with some chip like MTK
        // Since Y component is quadruple size as U and V component, the stride must be set as 32x
        if (!useSoftEncoder && (vOutWidth % 32 != 0 || vOutHeight % 32 != 0)) {
            if (vmci.getName().contains("MTK")) {
                //throw new AssertionError("MTK encoding revolution stride must be 32x");
            }
        }

        //setEncoderResolution(vOutWidth, vOutHeight);
    }

    private void onProcessedYuvFrame(byte[] yuvFrame, long pts, long tm) {
        ByteBuffer[] inBuffers = vencoder.getInputBuffers();
        ByteBuffer[] outBuffers = vencoder.getOutputBuffers();
        mlistCaptureTime.push(tm);

        int inBufferIndex = vencoder.dequeueInputBuffer(-1);
        if (inBufferIndex >= 0) {
            ByteBuffer bb = inBuffers[inBufferIndex];
            bb.clear();
            bb.put(yuvFrame, 0, yuvFrame.length);
            vencoder.queueInputBuffer(inBufferIndex, 0, yuvFrame.length, pts, 0);
        }

        for (; ; ) {
            int outBufferIndex = vencoder.dequeueOutputBuffer(vebi, 0);
            if (outBufferIndex >= 0) {
                ByteBuffer bb = outBuffers[outBufferIndex];
                if (!mlistCaptureTime.isEmpty()) {
                    tm = mlistCaptureTime.pop();
                }
                onEncodedAnnexbFrame(bb, vebi, tm);
                mTMEncodedDuration = System.currentTimeMillis() - tm;
                vencoder.releaseOutputBuffer(outBufferIndex, false);
            } else {
                break;
            }
        }
    }

    // when got encoded h264 es stream.
    private void onEncodedAnnexbFrame(ByteBuffer es, MediaCodec.BufferInfo bi, long tm) {
        if (null != tsMuxer )
            tsMuxer.writeSampleData(videoTSTrack, es.duplicate(), bi, tm);
    }

    // when got encoded aac raw stream.
    private void onEncodedAacFrame(ByteBuffer es, MediaCodec.BufferInfo bi, long tm) {
        if (null != tsMuxer )
            tsMuxer.writeSampleData(audioTSTrack, es.duplicate(), bi, tm);
    }

    public void onGetPcmFrame(byte[] data, int size) {
        if (aencoder == null) {
            return ;
        }
        try {

            ByteBuffer[] inBuffers = aencoder.getInputBuffers();
            ByteBuffer[] outBuffers = aencoder.getOutputBuffers();

            int inBufferIndex = aencoder.dequeueInputBuffer(-1);
            if (inBufferIndex >= 0) {
                ByteBuffer bb = inBuffers[inBufferIndex];
                bb.clear();
                bb.put(data, 0, size);
                long pts = System.nanoTime() / 1000 - mPresentTimeUs;
                aencoder.queueInputBuffer(inBufferIndex, 0, size, pts, 0);
            }

            for (; ; ) {
                int outBufferIndex = aencoder.dequeueOutputBuffer(aebi, 0);
                if (outBufferIndex >= 0) {
                    ByteBuffer bb = outBuffers[outBufferIndex];
                    onEncodedAacFrame(bb, aebi, 0);
                    aencoder.releaseOutputBuffer(outBufferIndex, false);
                } else {
                    break;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "onGetPcmFrame failed.");
            e.printStackTrace();
            return ;
        }
    }

    public void onGetRgbaFrame(byte[] data, int width, int height) {
        long tm = System.currentTimeMillis();
        // Check video frame cache number to judge the networking situation.
        // Just cache GOP / FPS seconds data according to latency.
        AtomicInteger videoFrameCacheNumber = new AtomicInteger(0);
        if (tsMuxer != null )
            videoFrameCacheNumber = tsMuxer.getVideoFrameCacheNumber();


        if (videoFrameCacheNumber != null && videoFrameCacheNumber.get() < VGOP) {
            long pts = System.nanoTime() / 1000 - mPresentTimeUs;
            //Log.w(TAG, String.format("TEST:onGetRgbaFrame pts=%dms, mPresentTimeUs=%dms", pts/1000, mPresentTimeUs/1000));
            if (useSoftEncoder) {
                //swRgbaFrame(data, width, height, pts);
            } else {
                byte[] processedData = hwRgbaFrame(data, width, height);
                if (processedData != null) {
                    onProcessedYuvFrame(processedData, pts, tm);
                } else {
                }
            }

            if (networkWeakTriggered) {
                networkWeakTriggered = false;
            }
        } else {
            networkWeakTriggered = true;
        }
    }

    private byte[] hwRgbaFrame(byte[] data, int width, int height) {
        byte[] frame = new byte[data.length];
       //*
        if (mVideoColorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar) {
            YV12toYUV420Planar(data, frame, width, height);
        } else if (mVideoColorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar) {
            YV12toYUV420PackedSemiPlanar(data, frame, width, height);
        } else if (mVideoColorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) {
            YV12toYUV420PackedSemiPlanar(data, frame, width, height);
        } else {
            System.arraycopy(data, 0, frame, 0, data.length);
        }
        //*/
        return frame;
    }

    public AudioRecord chooseAudioRecord() {
        return null;
    }
    // the color transform, @see http://stackoverflow.com/questions/15739684/mediacodec-and-camera-color-space-incorrect
    private static byte[] YV12toYUV420PackedSemiPlanar(final byte[] input, final byte[] output, final int width, final int height) {
        /*
         * COLOR_TI_FormatYUV420PackedSemiPlanar is NV12
         * We convert by putting the corresponding U and V bytes together (interleaved).
         */
        final int frameSize = width * height;
        final int qFrameSize = frameSize / 4;

        System.arraycopy(input, 0, output, 0, frameSize); // Y

        for (int i = 0; i < qFrameSize; i++) {
            output[frameSize + i * 2] = input[frameSize + i + qFrameSize]; // Cb (U)
            output[frameSize + i * 2 + 1] = input[frameSize + i]; // Cr (V)
        }
        return output;
    }

    private static byte[] YV12toYUV420Planar(byte[] input, byte[] output, int width, int height) {
        /*
         * COLOR_FormatYUV420Planar is I420 which is like YV12, but with U and V reversed.
         * So we just have to reverse U and V.
         */
        final int frameSize = width * height;
        final int qFrameSize = frameSize / 4;

        System.arraycopy(input, 0, output, 0, frameSize); // Y
        System.arraycopy(input, frameSize, output, frameSize + qFrameSize, qFrameSize); // Cr (V)
        System.arraycopy(input, frameSize + qFrameSize, output, frameSize, qFrameSize); // Cb (U)

        return output;
    }

/****
 * Audio
 */


}
