package com.zyl.livelibs;

import android.os.Bundle;
import android.widget.TextView;

import com.zyl.livelibs.databinding.ActivityMainBinding;

public class MainActivity extends BaseActivity {

    static {
        System.loadLibrary("live");
//        System.loadLibrary("yuv");
    }

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Example of a call to a native method
        TextView tv = binding.sampleText;
        tv.setText(stringFromJNI());
    }

    public native String stringFromJNI();

    public native void NV21toI420(byte[] src, byte[] dest, int height, int width);
}