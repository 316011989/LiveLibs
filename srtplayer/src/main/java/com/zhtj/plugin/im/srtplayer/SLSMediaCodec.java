package com.zhtj.plugin.im.srtplayer;

import android.util.Log;
import android.view.Surface;

import java.util.ArrayList;
import java.util.LinkedList;

/**
 * @author edward.wu
 * @date 2017/11/22 上午10:38
 * @desc
 */

public class SLSMediaCodec {

	private final String TAG = "SLSMediaCodec";

	//接收的视频帧队列
	protected volatile ArrayList<DataInfo> mFrmList = new ArrayList<>();
	protected Surface      mSurface = null;
	protected long         mDecodedDuration;
	protected String       mVideoSize = "";
	protected LinkedList<Long> mlistRecvTime = new LinkedList<>();

	public void init() {

		Log.i(TAG, "init");

	}

	public void uninit() {
		mFrmList.clear();
	}

	public void setSurface(Surface surface) {
		mSurface = surface;
	}
	public boolean addESData(byte[] data, int len, long dts, long pts) {
		DataInfo dataInfo = new DataInfo();
		dataInfo.mDataBytes = data;
		dataInfo.receivedDataTime = dts;
		mFrmList.add(dataInfo);
		return true;
	}

	public long getDecodedDuration() {
		return mDecodedDuration;
	}

	public String getVideoSize() {
		return mVideoSize;
	}

}
