package com.zyl.livelibs.handler;

import android.content.Context;
import android.util.Log;
import android.view.OrientationEventListener;

public class OrientationHandler {
    private String TAG = "OrientationHandler";
    private int OFFSET_ANGLE = 5;
    private int lastOrientationDegree = 0;
    private OnOrientationListener onOrientationListener = null;
    private OrientationEventListener orientationEventListener = null;

    public OrientationHandler(Context context) {
        initOrientation(context);
    }

    public interface OnOrientationListener {
        void onOrientation(int orientation);
    }

    private void initOrientation(Context context) {
        orientationEventListener = new OrientationEventListener(context.getApplicationContext()) {
            @Override
            public void onOrientationChanged(int orientation) {

                if (orientation == ORIENTATION_UNKNOWN)
                    return;

                if (orientation >= 0 - OFFSET_ANGLE && orientation <= OFFSET_ANGLE) {
                    if (lastOrientationDegree != 0) {
                        Log.i(TAG, "0, portrait down");
                        lastOrientationDegree = 0;
                        onOrientationListener.onOrientation(lastOrientationDegree);
                    }
                } else if (orientation >= 90 - OFFSET_ANGLE && orientation <= 90 + OFFSET_ANGLE) {
                    if (lastOrientationDegree != 90) {
                        Log.i(TAG, "90, landscape right");
                        lastOrientationDegree = 90;
                        onOrientationListener.onOrientation(lastOrientationDegree);
                    }
                } else if (orientation >= 180 - OFFSET_ANGLE && orientation <= 180 + OFFSET_ANGLE) {
                    if (lastOrientationDegree != 180) {
                        Log.i(TAG, "180, portrait up");
                        lastOrientationDegree = 180;
                        onOrientationListener.onOrientation(lastOrientationDegree);
                    }
                } else if (orientation >= 270 - OFFSET_ANGLE && orientation <= 270 + OFFSET_ANGLE) {
                    if (lastOrientationDegree != 270) {
                        Log.i(TAG, "270, landscape left");
                        lastOrientationDegree = 270;
                        onOrientationListener.onOrientation(lastOrientationDegree);
                    }
                }
            }
        };
    }

    public void setOnOrientationListener(OnOrientationListener onOrientationListener) {
        this.onOrientationListener = onOrientationListener;
    }

    public void enable() {
        if (orientationEventListener.canDetectOrientation()) {
            orientationEventListener.enable();
        }
    }

    void disable() {
        if (orientationEventListener.canDetectOrientation()) {
            orientationEventListener.disable();
        }
    }
}
