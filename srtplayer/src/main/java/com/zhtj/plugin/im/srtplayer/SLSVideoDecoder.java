package com.zhtj.plugin.im.srtplayer;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @author edward.wu
 * @date 2017/11/22 上午10:38
 * @desc
 */

public class SLSVideoDecoder extends SLSMediaCodec {

	private final String TAG = "SLSMediaCodec";

	//设置解码分辨率
	private final int VIDEO_WIDTH  = 640;//1080;//2592
	private final int VIDEO_HEIGHT = 480;//720;//1520

	//解码帧率 1s解码30帧
	private final int FRAME_RATE = 30;

	//支持格式
	private final String VIDEOFORMAT_H264  = "video/avc";
	private final String VIDEOFORMAT_MPEG4 = "video/mp4v-es";
	private final String VIDEOFORMAT_HEVC  = "video/hevc";

	//默认格式
	private String mMimeType = VIDEOFORMAT_H264;//VIDEOFORMAT_H264;

	private MediaCodec   mVideoDecoder;
	private DecodeThread mDecodeThread;


	private int mVideoWidth;
	private int mVideoHeight;


    @Override
    public void init() {

		Log.i(TAG, "init");

		if (mDecodeThread != null) {
			mDecodeThread.stopThread();
			try {
				mDecodeThread.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			mDecodeThread = null;
		}

		if (mVideoDecoder != null) {
			mVideoDecoder.stop();
			mVideoDecoder.release();
			mVideoDecoder = null;
		}


		try {
			//通过多媒体格式名创建一个可用的解码器
			mVideoDecoder = MediaCodec.createDecoderByType(mMimeType);
		} catch (IOException e) {
			e.printStackTrace();
			Log.e(TAG, "Init Exception " + e.getMessage());
		}
		//初始化解码器格式
		MediaFormat mediaformat = MediaFormat.createVideoFormat(mMimeType, VIDEO_WIDTH, VIDEO_HEIGHT);
		//设置帧率
		mediaformat.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
		//crypto:数据加密 flags:编码器/编码器
		mVideoDecoder.configure(mediaformat, mSurface, null, 0);
		mVideoDecoder.start();
		mDecodeThread = new DecodeThread();
		mDecodeThread.start();
	}

	@Override
	public void uninit() {
		if (mDecodeThread != null) {
			mDecodeThread.stopThread();
			try {
				mDecodeThread.join(300);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			mDecodeThread = null;
		}
		try {
			if (mVideoDecoder != null) {
				mVideoDecoder.stop();
				mVideoDecoder.release();
				mVideoDecoder = null;
			}
		} catch (IllegalStateException e) {
			e.printStackTrace();
		}

		mFrmList.clear();
	}

	/**
	 * @author zed
	 * @description 解码线程
	 * @time 2017/11/22
	 */
	private class DecodeThread extends Thread {

		private boolean isRunning = true;

		public synchronized void stopThread() {
			isRunning = false;
		}

		public boolean isRunning() {
			return isRunning;
		}

		@Override
		public void run() {

			Log.i(TAG, "===start DecodeThread===");

			//存放目标文件的数据
			ByteBuffer byteBuffer = null;
			//解码后的数据，包含每一个buffer的元数据信息，例如偏差，在相关解码器中有效的数据大小
			MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
			long startMs = System.currentTimeMillis();
			DataInfo dataInfo = null;
			boolean decoded_sps = false;
			byte[] sps = new byte[2];
			sps[0] = 0x01;
			sps[1] = 0x7;


			while (isRunning) {

				if (mFrmList.isEmpty()) {
					try {
						Thread.sleep(10);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					continue;
				}
				dataInfo = mFrmList.remove(0);

				if (!decoded_sps) {
					if (dataInfo.mDataBytes.length < 100)
						Log.e(TAG, "NUL len=" + dataInfo.mDataBytes.length);

					if (dataInfo.mDataBytes[3] != sps[0] || (dataInfo.mDataBytes[4] & 0x1F) != sps[1])
						continue;
					Log.e(TAG, "SPS is ready.");
					decoded_sps = true;
				}

				long tm = System.currentTimeMillis();
				mlistRecvTime.push(tm);

				//1 准备填充器
				int inIndex = -1;

				try {
					inIndex = mVideoDecoder.dequeueInputBuffer(dataInfo.receivedDataTime);
				} catch (IllegalStateException e) {
					e.printStackTrace();
					Log.e(TAG, "IllegalStateException dequeueInputBuffer ");
				}

				if (inIndex >= 0) {
					//2 准备填充数据
					if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
						byteBuffer = mVideoDecoder.getInputBuffers()[inIndex];
						byteBuffer.clear();
					} else {
						byteBuffer = mVideoDecoder.getInputBuffer(inIndex);
					}

					if (byteBuffer == null) {
						continue;
					}

					byteBuffer.put(dataInfo.mDataBytes, 0, dataInfo.mDataBytes.length);
					//3 把数据传给解码器
					mVideoDecoder.queueInputBuffer(inIndex, 0, dataInfo.mDataBytes.length, 0, 0);

				} else {
					SystemClock.sleep(50);
					continue;
				}

				int outIndex = MediaCodec.INFO_TRY_AGAIN_LATER;
				//4 开始解码
				try {
					outIndex = mVideoDecoder.dequeueOutputBuffer(info, 0);
				} catch (IllegalStateException e) {
					e.printStackTrace();
					Log.e(TAG, "IllegalStateException dequeueOutputBuffer " + e.getMessage());
				}

				if (outIndex >= 0) {

					boolean doRender = (info.size != 0);
					//对outputbuffer的处理完后，调用这个函数把buffer重新返回给codec类。
					//调用这个api之后，SurfaceView才有图像
					mVideoDecoder.releaseOutputBuffer(outIndex, doRender);
					if (!mlistRecvTime.isEmpty()) {
						tm = mlistRecvTime.poll();
					}
					mDecodedDuration = System.currentTimeMillis() - tm;

					//Log.i(TAG, "DecodeThread delay = " + (System.currentTimeMillis() - dataInfo.receivedDataTime) + " spent = " + (System.currentTimeMillis() - startDecodeTime) + " size = " + mFrmList.size());
					System.gc();

				} else {
					switch (outIndex) {
						case MediaCodec.INFO_TRY_AGAIN_LATER: {

						}
						break;
						case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED: {
							MediaFormat newFormat = mVideoDecoder.getOutputFormat();
							mVideoWidth = newFormat.getInteger("width");
							mVideoHeight = newFormat.getInteger("height");
							mVideoSize = String.format("%dx%d", mVideoWidth, mVideoHeight);

							//是否支持当前分辨率
							String support = MediaCodecUtils.getSupportMax(mMimeType);
							if (support != null) {
								String width = support.substring(0, support.indexOf("x"));
								String height = support.substring(support.indexOf("x") + 1);
								Log.i(TAG, " current " + mVideoWidth + "x" + mVideoHeight + " mMimeType " + mMimeType);
								Log.i(TAG, " Max " + width + "x" + height + " mMimeType " + mMimeType);
							}
						}
						break;
						case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED: {

						}
						break;
						default: {

						}
					}
				}
			}

			Log.i(TAG, "===stop DecodeThread===");
		}

	}
	public boolean addESData(byte[] data, int len, long dts, long pts) {
		DataInfo dataInfo = new DataInfo();
		dataInfo.mDataBytes = data;
		dataInfo.receivedDataTime = dts;
		mFrmList.add(dataInfo);
		return true;
	}

}
