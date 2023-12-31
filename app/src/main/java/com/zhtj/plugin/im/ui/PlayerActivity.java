package com.zhtj.plugin.im.ui;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Toast;

import com.zhtj.plugin.im.R;
import com.zhtj.plugin.im.liveplayer.MediaPlayer;
import com.zhtj.plugin.im.liveplayer.PlayerView;
import com.zhtj.plugin.im.Constans;

public class PlayerActivity extends Activity {
    private static final String PLAYER_SHARED_PREFS = "fanplayer_shared_prefs";
    private static final String KEY_PLAYER_OPEN_URL = "key_player_open_url";
    //    private static final String DEF_PLAYER_OPEN_URL= "http://9890.vod.myqcloud.com/9890_4e292f9a3dd011e6b4078980237cc3d3.f20.mp4";
    private MediaPlayer mPlayer = null;
    private SurfaceView mVideo = null;
    private SeekBar mSeek = null;
    private ProgressBar mBuffering = null;
    private ImageView mPause = null;
    private boolean mIsPlaying = false;
    private boolean mIsLive = false;
    private String mURL = "/data/data/com.rockcarry.fanplayer/files/This.mp4";
    private Surface mVideoSurface;
    private int mVideoViewW;
    private int mVideoViewH;
    SharedPreferences mSharedPrefs;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);
        mSharedPrefs = getSharedPreferences(PLAYER_SHARED_PREFS, Context.MODE_PRIVATE);

        Uri uri = Uri.parse(Constans.STREAM_URL);
        String scheme = uri.getScheme();
        if (scheme.equals("file")) {
            mURL = uri.getPath();
        } else if (scheme.equals("http")
                || scheme.equals("https")
                || scheme.equals("rtsp")
                || scheme.equals("rtmp")) {
            mURL = uri.toString();
        } else if (scheme.equals("content")) {
            String[] proj = {MediaStore.Images.Media.DATA};
            Cursor cursor = managedQuery(uri, proj, null, null, null);
            int colidx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();
            mURL = cursor.getString(colidx);
        }
        mIsLive = mURL.startsWith("http://") && mURL.endsWith(".m3u8") || mURL.startsWith("rtmp://") || mURL.startsWith("rtsp://") || mURL.startsWith("avkcp://") || mURL.startsWith("ffrdp://");
        mPlayer = new MediaPlayer(Constans.STREAM_URL,mHandler,Constans.PLAYER_INIT_PARAMS);
        mPlayer.open(mURL, Constans.PLAYER_INIT_PARAMS);

        PlayerView mRoot = (PlayerView) findViewById(R.id.player_root);
        mRoot.setOnSizeChangedListener((w, h, oldw, oldh) -> {
            mVideo.setVisibility(View.INVISIBLE);
            mVideoViewW = w;
            mVideoViewH = h;
            mHandler.sendEmptyMessage(MSG_UDPATE_VIEW_SIZE);
        });

        mVideo = (SurfaceView) findViewById(R.id.video_view);
        mVideo.getHolder().addCallback(
                new Callback() {
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

        mSeek = (SeekBar) findViewById(R.id.seek_bar);
        mSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    if (mPlayer != null) mPlayer.seek(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        mPause = (ImageView) findViewById(R.id.btn_playpause);
        mPause.setOnClickListener(v -> testPlayerPlay(!mIsPlaying));

        mBuffering = (ProgressBar) findViewById(R.id.buffering);
        mBuffering.setVisibility(mIsLive ? View.VISIBLE : View.INVISIBLE);

        // show buttons with auto hide
        showUIControls(true, true);
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
        if (!mIsLive) testPlayerPlay(true);
    }

    @Override
    public void onPause() {
        if (!mIsLive) testPlayerPlay(false);
        super.onPause();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
            showUIControls(true, true);
        }
        return super.dispatchTouchEvent(ev);
    }

    private void testPlayerPlay(boolean play) {
        if (mPlayer == null) return;
        if (play) {
            mPlayer.play();
            mIsPlaying = true;
            mPause.setImageResource(R.drawable.icn_media_pause);
            mHandler.sendEmptyMessage(MSG_UPDATE_PROGRESS);
        } else {
            mPlayer.pause();
            mIsPlaying = false;
            mPause.setImageResource(R.drawable.icn_media_play);
            mHandler.removeMessages(MSG_UPDATE_PROGRESS);
        }
    }

    private void showUIControls(boolean show, boolean autohide) {
        mHandler.removeMessages(MSG_HIDE_BUTTONS);
        if (mIsLive) show = false;
        if (show) {
            mSeek.setVisibility(View.VISIBLE);
            mPause.setVisibility(View.VISIBLE);
            if (autohide) {
                mHandler.sendEmptyMessageDelayed(MSG_HIDE_BUTTONS, 5000);
            }
        } else {
            mSeek.setVisibility(View.INVISIBLE);
            mPause.setVisibility(View.INVISIBLE);
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
                    Log.e("john Playeractivity", "handleMessage: MSG_UPDATE_PROGRESS");
                    mHandler.sendEmptyMessageDelayed(MSG_UPDATE_PROGRESS, 200);
                    int progress = mPlayer != null ? (int) mPlayer.getParam(MediaPlayer.PARAM_MEDIA_POSITION) : 0;
                    if (!mIsLive) {

                        if (progress >= 0) mSeek.setProgress(progress);
                    } else {
                        mBuffering.setVisibility(progress == -1 ? View.VISIBLE : View.INVISIBLE);
                    }
                }
                break;
                case MSG_HIDE_BUTTONS: {
                    mSeek.setVisibility(View.INVISIBLE);
                    mPause.setVisibility(View.INVISIBLE);
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
                        mSeek.setMax((int) mPlayer.getParam(MediaPlayer.PARAM_MEDIA_DURATION));
                        testPlayerPlay(true);
                    }
                }
                break;
                case MediaPlayer.MSG_OPEN_FAILED: {
                    String str = String.format(getString(R.string.open_video_failed), mURL);
                    Toast.makeText(PlayerActivity.this, str, Toast.LENGTH_LONG).show();
                }
                break;
                case MediaPlayer.MSG_PLAY_COMPLETED: {
                    if (!mIsLive) finish();
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

    private String readPlayerOpenURL() {
        return mSharedPrefs.getString(KEY_PLAYER_OPEN_URL, Constans.STREAM_URL);
    }

    private void savePlayerOpenURL(String url) {
        SharedPreferences.Editor editor = mSharedPrefs.edit();
        editor.putString(KEY_PLAYER_OPEN_URL, url);
        editor.commit();
    }
}

