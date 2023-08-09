package com.zhtj.plugin.im.srt;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.zhtj.plugin.im.R;
import com.zhtj.plugin.im.databinding.ActivityLiveSrtBinding;
import com.zhtj.plugin.im.live.srt.SrsPublishManager;
import com.zhtj.plugin.im.srtplayer.SrsPlayManager;

import java.util.Timer;
import java.util.TimerTask;

public class LiveSrtActivity extends AppCompatActivity {

    //private String mPublishURL = "udp://224.5.5.5:5001";
    private String mPublishURL = "srt://192.168.8.15:10080?streamid=#!::h=live/livelibs,m=publish";
    private String mPlayURL = "srt://192.168.8.15:10080?streamid=#!::h=live/livelibs,m=request";
    //    private String mPublishURL = "srt://192.168.1.181:10080?streamid=#!::h=live/livelibs,m=publish";
//    private String mPlayURL = "srt://192.168.1.181:10080?streamid=#!::h=live/livelibs,m=request";
    private ActivityLiveSrtBinding binding;
    private SrsPublishManager mPublishManager = null;
    private SrsPlayManager mPlayManager = null;


    private static final int MY_PERMISSIONS_REQUEST = 1;
    private static final String[] MANDATORY_PERMISSIONS = {
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.MODIFY_AUDIO_SETTINGS",
            "android.hardware.camera",
            "android.hardware.camera.autofocus",
            "android.permission.ACCESS_NETWORK_STATE",
            "android.hardware.wifi",
            "android.permission.CAMERA",
            "android.permission.MODIFY_AUDIO_SETTINGS",
            "android.permission.RECORD_AUDIO",
            "android.permission.INTERNET",
            "android.permission.WAKE_LOCK"
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        binding = ActivityLiveSrtBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // response screen rotation event
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);

        binding.navigation.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener);


        //init url
        binding.peerID.setText(mPlayURL);
        binding.localID.setText(mPublishURL);

        //start timer
        startLogTimer();

        //audio aec
        AudioManager audioManager = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        audioManager.setSpeakerphoneOn(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (String permission : MANDATORY_PERMISSIONS) {
                if (checkCallingOrSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                    Log.d("SDK", permission + " request ");
                    ActivityCompat.requestPermissions(this, new String[]{permission}, MY_PERMISSIONS_REQUEST);
                } else {
                    Log.d("SDK", permission + " success ");
                }
            }
        }
    }

    private BottomNavigationView.OnNavigationItemSelectedListener mOnNavigationItemSelectedListener
            = new BottomNavigationView.OnNavigationItemSelectedListener() {

        @Override
        public boolean onNavigationItemSelected(@NonNull MenuItem item) {
            switch (item.getItemId()) {
                case R.id.navigation_home:
                    if (null == mPublishManager) {
                        mPublishManager = new SrsPublishManager();
                    }
                    mPublishManager.setCameraView(binding.cameraPreview);
                    TextView localID = findViewById(R.id.localID);
                    mPublishURL = localID.getText().toString();
                    mPublishManager.start(mPublishURL);
                    return true;
                case R.id.navigation_dashboard:

                    if (null == mPlayManager) {
                        mPlayManager = new SrsPlayManager();
                    }

                    mPlayManager.setSurfaceView(binding.videoSurface);
                    mPlayURL = binding.peerID.getText().toString();
                    mPlayManager.start(mPlayURL);

                    return true;
                case R.id.navigation_notifications:
                    binding.message.setText(R.string.title_notifications);
                    if (mPublishManager != null)
                        mPublishManager.switchCameraFace();
                    return true;
            }
            return false;
        }
    };


    @Override
    protected void onStart() {
        super.onStart();
        resetMedec();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mPublishManager != null) {
            mPublishManager.stop();
        }
        if (mPlayManager != null) {
            mPlayManager.stop();
        }
    }

    void startLogTimer() {
        Timer timer = new Timer();
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                refreshLogInfo();
            }
        };
        timer.schedule(task, 0, 1000);
    }

    void refreshLogInfo() {
        TextView logInfo = (TextView) findViewById(R.id.debugInfoView);
        String log = String.format("publisher:\n");
        String logEncoded = String.format("  encoded duration: %d(ms)\n", mPublishManager == null ? 0 : mPublishManager.getEncodedDuration());
        log += logEncoded;
        log += "player:\n";
        String info = String.format("  video size: %s\n", mPlayManager != null ? mPlayManager.getVideoSize() : "");
        log += info;
        info = String.format("  net delay: %d(ms)\n", mPlayManager != null ? mPlayManager.getNetDelay() : 0);
        log += info;
        info = String.format("  decoded duration: %d(ms)\n", mPlayManager == null ? 0 : mPlayManager.getDecodedDuration());
        log += info;
        logInfo.setText(log);

    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        resetMedec();
    }

    public void resetMedec() {
        if (mPlayManager != null) {
            mPlayManager.reset();
        }
        if (mPublishManager != null) {
            mPublishManager.reset();
        }
    }

}
