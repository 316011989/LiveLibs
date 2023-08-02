package com.zhtj.plugin.im.live.srt;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;


/**
 * Created by Leo Ma on 2016/2/25.
 */
public class SrsCameraView extends SurfaceView {
    private final String TAG = SrsCameraView.class.getSimpleName();

    private final int VWIDTH = 640;//1920;
    private final int VHEIGHT = 360;//1080;

    private int mPreviewWidth;
    private int mPreviewHeight;
    private boolean mIsTorchOn = false;
    private Camera mCamera;
    private int mCamId = -1;
    private int mfrontCamId = -1;
    private int mbackCamId = -1;

    private int mPreviewRotation = 90;
    private int mPreviewOrientation = Configuration.ORIENTATION_PORTRAIT;

    private byte[] vbuffer;

    // video camera settings.
    private Camera.Size vsize;

    private SrsEncoder mMedieaEncoder = null;


    public SrsCameraView(Context context) {

        this(context, null);
    }

    public SrsCameraView(Context context, AttributeSet attrs) {
        super(context, attrs);

        SurfaceHolder holder = getHolder();
        holder.addCallback(mCallback);
    }

    private SurfaceHolder.Callback mCallback = new SurfaceHolder.Callback() {
        @Override
        public void surfaceCreated(SurfaceHolder holder) {

        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            Log.i(TAG, "surfaceChanged width = " + width + " height = " + height);
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            Log.i(TAG, "surfaceDestroyed");
        }
    };


    public void setPreviewCallback(PreviewCallback cb) {
    }

    public int getPreviewWidht() {
        return mPreviewRotation == 90 ? mPreviewHeight : mPreviewWidth;
    }

    public int getPreviewHeight() {
        return mPreviewRotation == 90 ? mPreviewWidth : mPreviewHeight;
    }

    public int[] setPreviewResolution(int width, int height) {
        getHolder().setFixedSize(width, height);

        mCamera = openCamera();
        mPreviewWidth = width;
        mPreviewHeight = height;
        Camera.Size rs = adaptPreviewResolution(mCamera.new Size(width, height));
        if (rs != null) {
            mPreviewWidth = rs.width;
            mPreviewHeight = rs.height;
        }
        mCamera.getParameters().setPreviewSize(mPreviewWidth, mPreviewHeight);

        ByteBuffer.allocateDirect(mPreviewWidth * mPreviewHeight * 4);

        return new int[]{mPreviewWidth, mPreviewHeight};
    }


    public void setCameraId(int id) {
        mCamId = id;
        setPreviewOrientation(mPreviewOrientation);
    }

    public void setPreviewOrientation(int orientation) {
        mPreviewOrientation = orientation;
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(mCamId, info);
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                mPreviewRotation = info.orientation % 360;
                mPreviewRotation = (360 - mPreviewRotation) % 360;  // compensate the mirror
            } else {
                mPreviewRotation = (info.orientation + 360) % 360;
            }
        } else if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                mPreviewRotation = (info.orientation + 90) % 360;
                mPreviewRotation = (360 - mPreviewRotation) % 360;  // compensate the mirror
            } else {
                mPreviewRotation = (info.orientation + 270) % 360;
            }
        }
    }

    public void setEncoder(SrsEncoder encoder) {
        mMedieaEncoder = encoder;
    }

    public int getCameraId() {
        return mCamId;
    }

    // for the vbuffer for YV12(android YUV), @see below:
    // https://developer.android.com/reference/android/hardware/Camera.Parameters.html#setPreviewFormat(int)
    // https://developer.android.com/reference/android/graphics/ImageFormat.html#YV12
    private int getYuvBuffer(int width, int height) {
        // stride = ALIGN(width, 16)
        int stride = (int) Math.ceil(width / 16.0) * 16;
        // y_size = stride * height
        int y_size = stride * height;
        // c_stride = ALIGN(stride/2, 16)
        int c_stride = (int) Math.ceil(width / 32.0) * 16;
        // c_size = c_stride * height/2
        int c_size = c_stride * height / 2;
        // size = y_size + c_size * 2
        return y_size + c_size * 2;
    }

    public boolean startCamera() {
        //setCameraId(1);
        if (mCamera == null) {
            mCamera = openCamera();
            if (mCamera == null) {
                return false;
            }
        }

        setPreviewResolution(VWIDTH, VHEIGHT);

        int VFPS = 24;
        Camera.Parameters params = mCamera.getParameters();
        params.setPictureSize(mPreviewWidth, mPreviewHeight);
        params.setPreviewSize(mPreviewWidth, mPreviewHeight);
        int[] range = adaptFpsRange(VFPS, params.getSupportedPreviewFpsRange());
        params.setPreviewFpsRange(range[0], range[1]);
        //params.setPreviewFormat(ImageFormat.NV21);
        params.setPreviewFormat(ImageFormat.YV12);
        params.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
        params.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_AUTO);
        params.setSceneMode(Camera.Parameters.SCENE_MODE_AUTO);

        List<String> supportedFocusModes = params.getSupportedFocusModes();
        if (supportedFocusModes != null && !supportedFocusModes.isEmpty()) {
            if (supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            } else if (supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                mCamera.autoFocus(null);
            } else {
                params.setFocusMode(supportedFocusModes.get(0));
            }
        }

        List<String> supportedFlashModes = params.getSupportedFlashModes();
        if (supportedFlashModes != null && !supportedFlashModes.isEmpty()) {
            if (supportedFlashModes.contains(Camera.Parameters.FLASH_MODE_TORCH)) {
                if (mIsTorchOn) {
                    params.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                }
            } else {
                params.setFlashMode(supportedFlashModes.get(0));
            }
        }
        Camera.Size size = null;
        List<Camera.Size> sizes = params.getSupportedPictureSizes();
        for (int i = 0; i < sizes.size(); i++) {
            Camera.Size s = sizes.get(i);
            //Log.i(TAG, String.format("camera supported picture size %dx%d", s.width, s.height));
            if (size == null) {
                if (s.height == mPreviewHeight) {
                    size = s;
                }
            } else {
                if (s.width == mPreviewWidth) {
                    size = s;
                }
            }
        }
        params.setPictureSize(size.width, size.height);
        Log.i(TAG, String.format("set the picture size in %dx%d", size.width, size.height));

//        size = null;
//        sizes = params.getSupportedPreviewSizes();
//        for (int i = 0; i < sizes.size(); i++) {
//            Camera.Size s = sizes.get(i);
//            //Log.i(TAG, String.format("camera supported preview size %dx%d", s.width, s.height));
//            if (size == null) {
//                if (s.height == mPreviewHeight) {
//                    size = s;
//                }
//            } else {
//                if (s.width == mPreviewWidth) {
//                    size = s;
//                }
//            }
//        }
        vsize = size;
        params.setPreviewSize(size.width, size.height);
        Log.i(TAG, String.format("set the preview size in %dx%d", size.width, size.height));

        mCamera.setParameters(params);

        mCamera.setDisplayOrientation(mPreviewRotation);
        // set the callback and start the preview.
        vbuffer = new byte[getYuvBuffer(size.width, size.height)];
        mCamera.addCallbackBuffer(vbuffer);
        mCamera.setPreviewCallbackWithBuffer((Camera.PreviewCallback) fetchVideoFromDevice());

        try {
            //mCamera.setPreviewTexture(surfaceTexture);
            mCamera.setPreviewDisplay(getHolder());
        } catch (IOException e) {
            e.printStackTrace();
        }
        mCamera.startPreview();

        return true;
    }

    // when got YUV frame from camera.
    private Object fetchVideoFromDevice() {
        return (Camera.PreviewCallback) (data, camera) -> {
            // color space transform.
            byte[] frame = data;
            int w = vsize.width;
            int h = vsize.height;

            //*
            if (mPreviewRotation == 90) {
                frame = new byte[data.length];
                int rotation = mCamId == mfrontCamId ? 90 : 270;
                JNISrt.yv12RotationAnti(data, frame, vsize.width, vsize.height, rotation);
                w = vsize.height;
                h = vsize.width;
            }
            //*/

            // feed the frame to vencoder and muxer.
            try {
                if (mMedieaEncoder != null)
                    mMedieaEncoder.onGetRgbaFrame(frame, w, h);
            } catch (Exception e) {
                Log.e(TAG, String.format("consume yuv frame failed. e=%s", e.toString()));
                e.printStackTrace();
                throw e;
            }
            //*/

            // to fetch next frame.
            camera.addCallbackBuffer(vbuffer);
        };
    }

    public void switchCameraFace() {
        int id = getCameraId();
        id = id == mfrontCamId ? mbackCamId : mfrontCamId;
        setCameraId(id);
    }


    public void stopCamera() {

        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
    }

    private Camera openCamera() {
        Camera camera;
        if (mCamId < 0) {
            Camera.CameraInfo info = new Camera.CameraInfo();
            int numCameras = Camera.getNumberOfCameras();
            mfrontCamId = -1;
            mbackCamId = -1;
            for (int i = 0; i < numCameras; i++) {
                Camera.getCameraInfo(i, info);
                if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                    mbackCamId = i;
                } else if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                    mfrontCamId = i;
                    break;
                }
            }
            if (mfrontCamId != -1) {
                mCamId = mfrontCamId;
            } else if (mbackCamId != -1) {
                mCamId = mbackCamId;
            } else {
                mCamId = 0;
            }
        }
        camera = Camera.open(mCamId);
        return camera;
    }

    private Camera.Size adaptPreviewResolution(Camera.Size resolution) {
        float xdy = (float) resolution.width / (float) resolution.height;
        Camera.Size best = resolution;
        Log.e("johnelon", "width=" + resolution.width);
        for (Camera.Size size : mCamera.getParameters().getSupportedPreviewSizes()) {
            if (size.equals(resolution)) {
                return size;
            }
            Log.e("johnelon", "for width=" + size.width);
            float tmp = Math.abs(((float) size.width / (float) size.height) - xdy);
            if (tmp == 0) {
                best = size;
            }
        }
        return best;
    }

    private int[] adaptFpsRange(int expectedFps, List<int[]> fpsRanges) {
        expectedFps *= 1000;
        int[] closestRange = fpsRanges.get(0);
        int measure = Math.abs(closestRange[0] - expectedFps) + Math.abs(closestRange[1] - expectedFps);
        for (int[] range : fpsRanges) {
            if (range[0] <= expectedFps && range[1] >= expectedFps) {
                int curMeasure = Math.abs(range[0] - expectedFps) + Math.abs(range[1] - expectedFps);
                if (curMeasure < measure) {
                    closestRange = range;
                    measure = curMeasure;
                }
            }
        }
        return closestRange;
    }

    public interface PreviewCallback {

        void onGetRgbaFrame(byte[] data, int width, int height);
    }

    /**
     * yuv format **
     * <p>
     * YUV420:
     * YYYY
     * YYYY
     * YYYY
     * YYYY
     * UU
     * UU
     * VV
     * VV
     * <p>
     * NV21:
     * YYYY
     * YYYY
     * YYYY
     * YYYY
     * VUVU
     * VUVU
     * <p>
     * YV12:
     * YYYY
     * YYYY
     * YYYY
     * YYYY
     * VV
     * VV
     * UU
     * UU
     */

    // the color transform, @see http://stackoverflow.com/questions/15739684/mediacodec-and-camera-color-space-incorrect
    private static byte[] YV12toYUV420PackedSemiPlanar(final byte[] input, final byte[] output, final int width, final int height) {
        /*
         * COLOR_TI_FormatYUV420PackedSemiPlanar is NV12
         * We convert by putting the corresponding U and V bytes together (interleaved).
         */
        final int frameSize = width * height;
        final int qFrameSize = frameSize / 4;

        System.arraycopy(input, 0, output, 0, frameSize); // Y

        for (int i = 0; i < qFrameSize; i++) {
            output[frameSize + i * 2] = input[frameSize + i + qFrameSize]; // Cb (U)
            output[frameSize + i * 2 + 1] = input[frameSize + i]; // Cr (V)
        }
        return output;
    }

    private static byte[] YV12toYUV420Planar(byte[] input, byte[] output, int width, int height) {
        /*
         * COLOR_FormatYUV420Planar is I420 which is like YV12, but with U and V reversed.
         * So we just have to reverse U and V.
         */
        final int frameSize = width * height;
        final int qFrameSize = frameSize / 4;

        System.arraycopy(input, 0, output, 0, frameSize); // Y
        System.arraycopy(input, frameSize, output, frameSize + qFrameSize, qFrameSize); // Cr (V)
        System.arraycopy(input, frameSize + qFrameSize, output, frameSize, qFrameSize); // Cb (U)

        return output;
    }

}
