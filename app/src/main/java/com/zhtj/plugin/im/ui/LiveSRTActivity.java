package com.zhtj.plugin.im.ui;

import android.os.Bundle;
import android.view.WindowManager;

import androidx.annotation.Nullable;

import com.zhtj.plugin.im.BaseActivity;
import com.zhtj.plugin.im.databinding.ActivitySrtLiveBinding;

public class LiveSRTActivity extends BaseActivity {
    private ActivitySrtLiveBinding binding;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        binding = ActivitySrtLiveBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
//        SRTStreamerManager( this,new SRTConfiguration(this));
    }
}
