#include <jni.h>
#include <string>
#include "libyuv/convert.h"

extern "C" JNIEXPORT jstring JNICALL
Java_com_zyl_livelibs_MainActivity_stringFromJNI(
        JNIEnv *env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}



/**
 * NV21 -> I420
 */
extern "C"
JNIEXPORT void JNICALL
Java_com_zyl_livelibs_MainActivity_NV21toI420(JNIEnv *env, jobject instance, jbyteArray input_,
                                              jbyteArray output_, jint in_width, jint in_height) {
    jbyte *srcData = env->GetByteArrayElements(input_, NULL);
    jbyte *dstData = env->GetByteArrayElements(output_, NULL);

    libyuv::NV21ToI420((const uint8_t *)srcData, in_width,
               (uint8_t *)srcData + (in_width * in_height), in_width,
               (uint8_t *)dstData, in_width,
               (uint8_t *)dstData + (in_width * in_height), in_width / 2,
               (uint8_t *)dstData + (in_width * in_height * 5 / 4), in_width / 2,
               in_width, in_height);
    env->ReleaseByteArrayElements(input_, srcData, 0);
    env->ReleaseByteArrayElements(output_, dstData, 0);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_zyl_livelibs_MainActivity_rotate(JNIEnv *env, jobject type, jbyteArray input_,
                                          jbyteArray output_, jint in_width, jint in_height,
                                          jint rotation) {
    jbyte *srcData = env->GetByteArrayElements(input_, NULL);
    jbyte *dstData = env->GetByteArrayElements(output_, NULL);


    libyuv::RotationMode rotationMode = libyuv::kRotate0;
    switch (rotation) {
        case 90:
            rotationMode = libyuv::kRotate90;
            break;
        case 180:
            rotationMode = libyuv::kRotate180;
            break;
        case 270:
            rotationMode = libyuv::kRotate270;
            break;
    }
    I420Rotate((const uint8_t *)srcData, in_width,
               (uint8_t *)srcData + (in_width * in_height), in_width / 2,
               (uint8_t *)srcData + (in_width * in_height * 5 / 4), in_width / 2,
               (uint8_t *)dstData, in_height,
               (uint8_t *)dstData + (in_width * in_height), in_height / 2,
               (uint8_t *)dstData + (in_width * in_height * 5 / 4), in_height / 2,
               in_width, in_height,
               rotationMode);

    env->ReleaseByteArrayElements(input_, srcData, 0);
    env->ReleaseByteArrayElements(output_, dstData, 0);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_zyl_livelibs_MainActivity_NV21ToI420andRotate90(JNIEnv *env, jobject type,
                                                             jbyteArray input_,
                                                             jbyteArray output_,
                                                             jint in_width, jint in_height) {
    jbyte *srcData = env->GetByteArrayElements(input_, NULL);
    jbyte *dstData = env->GetByteArrayElements(output_, NULL);
    jsize size = env->GetArrayLength(input_);

    libyuv::NV21ToI420((const uint8_t *) srcData, in_width,
               (uint8_t *)srcData + (in_width * in_height), in_width,
               (uint8_t *)dstData, in_width,
               (uint8_t *)dstData + (in_width * in_height), in_width / 2,
               (uint8_t *)dstData + (in_width * in_height * 5 / 4), in_width / 2,
               in_width, in_height);

    I420Rotate((const uint8_t *)dstData, in_width,
               (uint8_t *)dstData + (in_width * in_height), in_width / 2,
               (uint8_t *)dstData + (in_width * in_height * 5 / 4), in_width / 2,
               (uint8_t *)srcData, in_height,
               (uint8_t *)srcData + (in_width * in_height), in_height / 2,
               (uint8_t *)srcData + (in_width * in_height * 5 / 4), in_height / 2,
               in_width, in_height,
               libyuv::kRotate90);
    memcpy(dstData, srcData, size);

//    fixme can't work
//    ConvertToI420((const uint8_t *) srcData, size,
//                  (uint8_t *)dstData, in_width,
//                  (uint8_t *)dstData + (in_width * in_height), in_width / 2,
//                  (uint8_t *)dstData + (in_width * in_height * 5 / 4), in_width / 2,
//                  0, 0,
//                  in_width, in_height,
//                  in_width, in_height,
//                  kRotate90,
//                  FOURCC_NV21);
//
//   fixme can't work
//    NV12ToI420Rotate((const uint8_t *) srcData, in_width,
//                     (uint8_t *) srcData + (in_width * in_height), in_width,
//                     (uint8_t *)dstData, in_width,
//                     (uint8_t *)dstData + (in_width * in_height * 5 / 4), in_width / 2,
//                     (uint8_t *)dstData + (in_width * in_height), in_width / 2,
//                     in_width, in_height,
//                     kRotate90);

    env->ReleaseByteArrayElements(input_, srcData, 0);
    env->ReleaseByteArrayElements(output_, dstData, 0);
}
