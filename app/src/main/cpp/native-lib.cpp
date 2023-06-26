#include <jni.h>
#include <string>
#include "libyuv/rotate.h"

extern "C" JNIEXPORT jstring JNICALL
Java_com_zyl_livelibs_MainActivity_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_com_zyl_livelibs_MainActivity_rotate(JNIEnv *env, jclass thiz, jobject y,
                                          jobject u,
                                          jobject v, jint yStride, jint uStride,
                                          jint vStride,
                                          jobject yOut, jobject uOut, jobject vOut,
                                          jint yOutStride, jint uOutStride,
                                          jint vOutStride,
                                          jint width, jint height,
                                          jint rotationMode) {
    uint8_t *yNative = (uint8_t *) env->GetDirectBufferAddress(y);
    uint8_t *uNative = (uint8_t *) env->GetDirectBufferAddress(u);
    uint8_t *vNative = (uint8_t *) env->GetDirectBufferAddress(v);
    uint8_t *yOutNative = (uint8_t *) env->GetDirectBufferAddress(yOut);
    uint8_t *uOutNative = (uint8_t *) env->GetDirectBufferAddress(uOut);
    uint8_t *vOutNative = (uint8_t *) env->GetDirectBufferAddress(vOut);
    libyuv::I420Rotate(yNative, yStride,
                       uNative, uStride,
                       vNative, vStride,
                       yOutNative, yOutStride,
                       uOutNative, uOutStride,
                       vOutNative, vOutStride,
                       width, height,
                       libyuv::RotationMode(rotationMode));
}
