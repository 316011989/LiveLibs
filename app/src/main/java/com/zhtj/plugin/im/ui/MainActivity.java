package com.zhtj.plugin.im.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.TextView;

import com.zhtj.plugin.im.BaseActivity;
import com.zhtj.plugin.im.databinding.ActivityMainBinding;

public class MainActivity extends BaseActivity {


    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String[] permissions = new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.CAMERA, Manifest.permission.ACCESS_NETWORK_STATE, Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.INTERNET, Manifest.permission.SYSTEM_ALERT_WINDOW, Manifest.permission.MODIFY_AUDIO_SETTINGS
            };
            for (String permission : permissions) {
                if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(permissions, 1001);
                }
            }
        } else {

        }
        // Example of a call to a native method
        TextView btnPush = binding.btnPush;
        btnPush.setOnClickListener(view -> {
            Intent intent = new Intent().setClass(this, LiveActivity.class);
            startActivity(intent);
        });
        TextView btnPlay = binding.btnPlay;
        btnPlay.setOnClickListener(view -> {
            Intent intent = new Intent().setClass(this, PlayerActivity.class);
            startActivity(intent);
        });
    }


}