package com.zyl.livelibs.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import com.zyl.livelibs.BaseActivity;
import com.zyl.livelibs.databinding.ActivityMainBinding;

public class MainActivity extends BaseActivity {


    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Example of a call to a native method
        TextView tv = binding.sampleText;
        tv.setText("native 方法在application中");
        tv.setOnClickListener(view -> {
            Intent intent = new Intent().setClass(this, LiveActivity.class);
            startActivity(intent);
        });
    }


}