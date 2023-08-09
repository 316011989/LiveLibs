package com.zhtj.plugin.im.liveplayer.srt;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
 * @author zed
 * @date 2017/11/22 上午10:38
 * @desc
 */

public class SLSSurfaceView extends SurfaceView {

	private final String TAG = "SLSSurfaceView";
	private Surface mSurface;



	public SLSSurfaceView(Context context, AttributeSet attrs) {
		super(context, attrs);
		SurfaceHolder holder = getHolder();
		holder.addCallback(mCallback);
	}

	private SurfaceHolder.Callback mCallback = new SurfaceHolder.Callback() {
		@Override
		public void surfaceCreated(SurfaceHolder holder) {

			mSurface = holder.getSurface();
			Log.i(TAG, "surfaceCreated");
		}

		@Override
		public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
			Log.i(TAG, "surfaceChanged width = " + width + " height = " + height);
		}

		@Override
		public void surfaceDestroyed(SurfaceHolder holder) {
			Log.i(TAG, "surfaceDestroyed");
		}
	};

	public Surface getSurface() {
		return mSurface;
	}

}
