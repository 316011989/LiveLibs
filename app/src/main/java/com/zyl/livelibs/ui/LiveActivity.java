package com.zyl.livelibs.ui;

import android.annotation.SuppressLint;
import android.content.IntentFilter;
import android.media.AudioFormat;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zyl.livelibs.R;
import com.zyl.livelibs.handler.OrientationHandler;
import com.zyl.livelibs.BaseActivity;
import com.zyl.livelibs.camera.Camera2Helper;
import com.zyl.livelibs.camera.CameraType;
import com.zyl.livelibs.databinding.ActivityLiveBinding;
import com.zyl.livelibs.handler.ConnectionReceiver;
import com.zyl.livelibs.listener.LiveStateChangeListener;
import com.zyl.livelibs.listener.OnNetworkChangeListener;
import com.zyl.livelibs.param.AudioParam;
import com.zyl.livelibs.param.VideoParam;
import com.zyl.livelibs.stream.LivePusherNew;

public class LiveActivity extends BaseActivity implements View.OnClickListener, CompoundButton.OnCheckedChangeListener, OnNetworkChangeListener, LiveStateChangeListener {
    private final String TAG = LiveActivity.class.getSimpleName();
    private final int MSG_ERROR = 100;
    private final String LIVE_URL = "rtmp://192.168.10.200:1935/live/android";
    private ActivityLiveBinding binding;
    private OrientationHandler orientationHandler = null;
    private LivePusherNew livePusher = null;
    private ConnectionReceiver connectionReceiver = null;
    private boolean isPushing = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLiveBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        initView();
        initPusher();
        registerBroadcast(this);
        orientationHandler = new OrientationHandler(this);
        orientationHandler.enable();
        orientationHandler.setOnOrientationListener(orientation -> {
                    int previewDegree = (orientation + 90) % 360;
                    livePusher.setPreviewDegree(previewDegree);
                }
        );
    }

    private void initView() {
        binding.btnSwap.setOnClickListener(this);
        binding.btnLive.setOnCheckedChangeListener(this);
        binding.btnMute.setOnCheckedChangeListener(this);
    }

    private void initPusher() {
        int width = 640;
        int height = 480;
        int videoBitRate = 800000;// kb/s
        int videoFrameRate = 10;// fps
        VideoParam videoParam = new VideoParam(width, height, Integer.parseInt(Camera2Helper.CAMERA_ID_FRONT)
                , videoBitRate, videoFrameRate);
        int sampleRate = 44100;
        int channelConfig = AudioFormat.CHANNEL_IN_STEREO;
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
        int numChannels = 2;
        AudioParam audioParam = new AudioParam(sampleRate, channelConfig, audioFormat, numChannels);
        // Camera1: SurfaceView  Camera2: TextureView
        livePusher = new LivePusherNew(this, videoParam, audioParam, binding.surfaceCamera, CameraType.CAMERA2);
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.btn_swap) {//switch camera
            livePusher.switchCamera();
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
        switch (compoundButton.getId()) {
            case R.id.btn_live://start or stop living
                if (isChecked) {
                    livePusher.startPush(LIVE_URL, this);
                    isPushing = true;
                } else {
                    livePusher.stopPush();
                    isPushing = false;
                }
                break;
            case R.id.btn_mute://mute or not
                livePusher.setMute(isChecked);
                break;
            default:
                break;
        }
    }

    private void registerBroadcast(OnNetworkChangeListener networkChangeListener) {
        connectionReceiver = new ConnectionReceiver(networkChangeListener);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(connectionReceiver, intentFilter);
    }

    @Override
    public void onNetworkChange() {
        Toast.makeText(this, "network is not available", Toast.LENGTH_SHORT).show();
        if (livePusher != null && isPushing) {
            livePusher.stopPush();
            isPushing = false;
        }
    }

    @Override
    public void onError(String msg) {
        mHandler.obtainMessage(MSG_ERROR, msg).sendToTarget();
    }

    @Override
    public void  onDestroy() {
        super.onDestroy();
        orientationHandler.disable();
        if (livePusher != null) {
            if (isPushing) {
                isPushing = false;
                livePusher.stopPush();
            }
            livePusher.release();
        }
        if (connectionReceiver != null) {
            unregisterReceiver(connectionReceiver);
        }
    }

    @SuppressLint("HandlerLeak")
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            if (msg.what == MSG_ERROR) {
                String errMsg = msg.obj.toString();
                if (!TextUtils.isEmpty(errMsg)) {
                    Toast.makeText(LiveActivity.this, errMsg, Toast.LENGTH_SHORT).show();
                }
            }
        }

    };
}
