package com.zhtj.plugin.im.ui;

import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.zhtj.plugin.im.BaseActivity;
import com.zhtj.plugin.im.Constans;
import com.zhtj.plugin.im.R;
import com.zhtj.plugin.im.databinding.ActivityNewplayerBinding;
import com.zhtj.plugin.im.liveplayer.MediaPlayer;
import com.zhtj.plugin.im.liveplayer.PlayerView;

/**
 * 相对playeractivity做了简化,没有UI,没有本地视频播放和控制
 */
public class NewPlayerActivity extends BaseActivity {
    private MediaPlayer mPlayer = null;
    private SurfaceView mVideo = null;
    private Surface mVideoSurface;
    private int mVideoViewW;
    private int mVideoViewH;

    private ActivityNewplayerBinding binding;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityNewplayerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("open url:");
        final EditText edt = new EditText(this);
        edt.setHint("url:");
        edt.setSingleLine(true);
        edt.setText(Constans.PLAY_URL);
        builder.setView(edt);
        builder.setNegativeButton("cancel", (dialog, which) -> NewPlayerActivity.this.finish());
        builder.setPositiveButton("confirm", (dialog, which) -> {
            mPlayer = new MediaPlayer(mHandler);
            mPlayer.open(Constans.PLAY_URL, Constans.PLAYER_INIT_PARAMS);
            mPlayer.setDisplaySurface(mVideoSurface);
        });
        AlertDialog dlg = builder.create();
        dlg.show();

        PlayerView mRoot = (PlayerView) findViewById(R.id.player_root);
        mRoot.setOnSizeChangedListener((w, h, oldw, oldh) -> {
            mVideo.setVisibility(View.INVISIBLE);
            mVideoViewW = w;
            mVideoViewH = h;
            mHandler.sendEmptyMessage(MSG_UDPATE_VIEW_SIZE);
        });

        mVideo = (SurfaceView) findViewById(R.id.video_view);
        mVideo.getHolder().addCallback(
                new SurfaceHolder.Callback() {
                    @Override
                    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
//                  if (mPlayer != null) mPlayer.setDisplaySurface(holder.getSurface());
                    }

                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        mVideoSurface = holder.getSurface();
                        if (mPlayer != null) mPlayer.setDisplaySurface(mVideoSurface);
                    }

                    @Override
                    public void surfaceDestroyed(SurfaceHolder holder) {
                        mVideoSurface = null;
                        if (mPlayer != null) mPlayer.setDisplaySurface(mVideoSurface);
                    }
                }
        );

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mHandler.removeMessages(MSG_UPDATE_PROGRESS);
        if (mPlayer != null) mPlayer.close();
    }

    @Override
    public void onResume() {
        super.onResume();
        testPlayerPlay(true);
    }

    @Override
    public void onPause() {
        testPlayerPlay(false);
        super.onPause();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
        }
        return super.dispatchTouchEvent(ev);
    }

    private void testPlayerPlay(boolean play) {
        if (mPlayer == null) return;
        if (play) {
            mPlayer.play();
            mHandler.sendEmptyMessage(MSG_UPDATE_PROGRESS);
        } else {
            mPlayer.pause();
            mHandler.removeMessages(MSG_UPDATE_PROGRESS);
        }
    }


    private static final int MSG_UPDATE_PROGRESS = 1;
    private static final int MSG_UDPATE_VIEW_SIZE = 2;
    private static final int MSG_HIDE_BUTTONS = 3;
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_UPDATE_PROGRESS: {
                    mHandler.sendEmptyMessageDelayed(MSG_UPDATE_PROGRESS, 200);
                    int progress = mPlayer != null ? (int) mPlayer.getParam(MediaPlayer.PARAM_MEDIA_POSITION) : 0;
                }
                break;
                case MSG_HIDE_BUTTONS: {
                }
                break;
                case MSG_UDPATE_VIEW_SIZE: {
                    if (mPlayer != null && mPlayer.initVideoSize(mVideoViewW, mVideoViewH, mVideo)) {
                        mVideo.setVisibility(View.VISIBLE);
                    }
                }
                break;
                case MediaPlayer.MSG_OPEN_DONE: {
                    if (mPlayer != null) {
                        mPlayer.setDisplaySurface(mVideoSurface);
                        mVideo.setVisibility(View.INVISIBLE);
                        mHandler.sendEmptyMessage(MSG_UDPATE_VIEW_SIZE);
                        testPlayerPlay(true);
                    }
                }
                break;
                case MediaPlayer.MSG_OPEN_FAILED: {
                    String str = String.format(getString(R.string.open_video_failed), Constans.PLAY_URL);
                    Toast.makeText(NewPlayerActivity.this, str, Toast.LENGTH_LONG).show();
                }
                break;
                case MediaPlayer.MSG_PLAY_COMPLETED: {
                    finish();
                }
                break;
                case MediaPlayer.MSG_VIDEO_RESIZED: {
                    mVideo.setVisibility(View.INVISIBLE);
                    mHandler.sendEmptyMessage(MSG_UDPATE_VIEW_SIZE);
                }
                break;
            }
        }
    };
}

