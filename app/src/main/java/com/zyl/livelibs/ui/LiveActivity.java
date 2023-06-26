package com.zyl.livelibs.ui;

import android.os.Bundle;
import android.os.PersistableBundle;

import androidx.annotation.Nullable;

import com.zyl.livelibs.BaseActivity;
import com.zyl.livelibs.databinding.ActivityLiveBinding;

public class LiveActivity extends BaseActivity {

    private ActivityLiveBinding binding;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState, @Nullable PersistableBundle persistentState) {
        super.onCreate(savedInstanceState, persistentState);
        binding = ActivityLiveBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

    }
}
