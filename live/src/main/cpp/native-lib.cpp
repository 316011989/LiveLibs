#include <jni.h>
#include <string>
#include "libyuv/convert.h"
#include "libyuv/rotate.h"
#include "libyuv/scale.h"




/**
 * NV21 -> I420
 */
extern "C" JNIEXPORT void JNICALL
Java_com_zyl_live_util_YuvUtil_NV21toI420andRotate(JNIEnv *env, jclass instance, jbyteArray input_,
                                                   jbyteArray output_, jint in_width,
                                                   jint in_height,
                                                   jint rotation) {
    jbyte *srcData = env->GetByteArrayElements(input_, NULL);
    jbyte *dstData = env->GetByteArrayElements(output_, NULL);
    ////NV21转i420
    libyuv::NV21ToI420((const uint8_t *) srcData, in_width,
                       (uint8_t *) srcData + (in_width * in_height), in_width,
                       (uint8_t *) dstData, in_width,
                       (uint8_t *) dstData + (in_width * in_height), in_width / 2,
                       (uint8_t *) dstData + (in_width * in_height * 5 / 4), in_width / 2,
                       in_width, in_height);


    ////旋转i420
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
    I420Rotate((const uint8_t *) srcData, in_width,
               (uint8_t *) srcData + (in_width * in_height), in_width / 2,
               (uint8_t *) srcData + (in_width * in_height * 5 / 4), in_width / 2,
               (uint8_t *) dstData, in_height,
               (uint8_t *) dstData + (in_width * in_height), in_height / 2,
               (uint8_t *) dstData + (in_width * in_height * 5 / 4), in_height / 2,
               in_width, in_height,
               rotationMode);

    env->ReleaseByteArrayElements(input_, srcData, 0);
    env->ReleaseByteArrayElements(output_, dstData, 0);

}


/**
 * NV21 -> I420
 */
extern "C" JNIEXPORT void JNICALL
Java_com_zyl_live_util_YuvUtil_NV21toI420(JNIEnv *env, jclass instance, jbyteArray input_,
                                          jbyteArray output_, jint in_width, jint in_height) {
    jbyte *srcData = env->GetByteArrayElements(input_, NULL);
    jbyte *dstData = env->GetByteArrayElements(output_, NULL);
    ////NV21转i420
    libyuv::NV21ToI420((const uint8_t *) srcData, in_width,
                       (uint8_t *) srcData + (in_width * in_height), in_width,
                       (uint8_t *) dstData, in_width,
                       (uint8_t *) dstData + (in_width * in_height), in_width / 2,
                       (uint8_t *) dstData + (in_width * in_height * 5 / 4), in_width / 2,
                       in_width, in_height);


    env->ReleaseByteArrayElements(input_, srcData, 0);
    env->ReleaseByteArrayElements(output_, dstData, 0);
}

/**
 * 镜像翻转
 */
extern "C" JNIEXPORT void JNICALL
Java_com_zyl_live_util_YuvUtil_I420Mirror(JNIEnv *env, jclass instance, jbyteArray input_,
                                          jbyteArray output_, jint in_width, jint in_height) {
    jbyte *srcData = env->GetByteArrayElements(input_, NULL);
    jbyte *dstData = env->GetByteArrayElements(output_, NULL);

    ////镜像翻转
    jint src_i420_y_size = in_width * in_height;
    jint src_i420_u_size = src_i420_y_size >> 2;
    jbyte *src_i420_y_data = srcData;
    jbyte *src_i420_u_data = srcData + src_i420_y_size;
    jbyte *src_i420_v_data = srcData + src_i420_y_size + src_i420_u_size;

    jbyte *dst_i420_y_data = dstData;
    jbyte *dst_i420_u_data = dstData + src_i420_y_size;
    jbyte *dst_i420_v_data = dstData + src_i420_y_size + src_i420_u_size;
    libyuv::I420Mirror((const uint8 *) src_i420_y_data, in_width,
                       (const uint8 *) src_i420_u_data, in_width >> 1,
                       (const uint8 *) src_i420_v_data, in_width >> 1,
                       (uint8 *) dst_i420_y_data, in_width,
                       (uint8 *) dst_i420_u_data, in_width >> 1,
                       (uint8 *) dst_i420_v_data, in_width >> 1,
                       in_width, in_height);


    env->ReleaseByteArrayElements(input_, srcData, 0);
    env->ReleaseByteArrayElements(output_, dstData, 0);
}


/**
 * NV21 -> I420
 */
extern "C" JNIEXPORT void JNICALL
Java_com_zyl_live_util_YuvUtil_Rotate(JNIEnv *env, jclass instance, jbyteArray input_,
                                                   jbyteArray output_, jint in_width,
                                                   jint in_height,
                                                   jint rotation) {
    jbyte *srcData = env->GetByteArrayElements(input_, NULL);
    jbyte *dstData = env->GetByteArrayElements(output_, NULL);


//    ////旋转i420
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
//    I420Rotate((const uint8_t *) srcData, in_width,
//               (uint8_t *) srcData + (in_width * in_height), in_width / 2,
//               (uint8_t *) srcData + (in_width * in_height * 5 / 4), in_width / 2,
//               (uint8_t *) dstData, in_height,
//               (uint8_t *) dstData + (in_width * in_height), in_height / 2,
//               (uint8_t *) dstData + (in_width * in_height * 5 / 4), in_height / 2,
//               in_width, in_height,
//               rotationMode);
//
//    env->ReleaseByteArrayElements(input_, srcData, 0);
//    env->ReleaseByteArrayElements(output_, dstData, 0);
////镜像翻转
    jint src_i420_y_size = in_width * in_height;
    jint src_i420_u_size = src_i420_y_size >> 2;
    jbyte *src_i420_y_data = srcData;
    jbyte *src_i420_u_data = srcData + src_i420_y_size;
    jbyte *src_i420_v_data = srcData + src_i420_y_size + src_i420_u_size;

    jbyte *dst_i420_y_data = dstData;
    jbyte *dst_i420_u_data = dstData + src_i420_y_size;
    jbyte *dst_i420_v_data = dstData + src_i420_y_size + src_i420_u_size;
    libyuv::I420Rotate((const uint8 *) src_i420_y_data, in_width,
                       (const uint8 *) src_i420_u_data, in_width >> 1,
                       (const uint8 *) src_i420_v_data, in_width >> 1,
                       (uint8 *) dst_i420_y_data, in_width,
                       (uint8 *) dst_i420_u_data, in_width >> 1,
                       (uint8 *) dst_i420_v_data, in_width >> 1,
                       in_width, in_height,rotationMode);


    env->ReleaseByteArrayElements(input_, srcData, 0);
    env->ReleaseByteArrayElements(output_, dstData, 0);
}