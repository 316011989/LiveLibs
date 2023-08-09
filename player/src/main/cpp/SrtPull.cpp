#include <jni.h>
#include <string>
#include <string.h>
#include <stdlib.h>

#include <android/log.h>
#include "srt/srt.h"

#define  LOG_TAG    "SRTClient"

#define  LOGD(...)  __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

struct srtContext {
    SRTSOCKET client_sock;
    int client_pollid;
};

extern "C" JNIEXPORT jint JNICALL
Java_com_zhtj_plugin_im_liveplayer_srt_JniPull_srtStartup(JNIEnv *env, jclass clazz) {
    int status = srt_startup();
    if (status != 0) {
        LOGD("%s(%d):Failed \n", __FUNCTION__, __LINE__);
        return -1;
    }
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_zhtj_plugin_im_liveplayer_srt_JniPull_srtCleanup(JNIEnv *env, jclass clazz) {
    int status = srt_cleanup();
    if (status != 0) {
        LOGD("%s(%d):Failed \n", __FUNCTION__, __LINE__);
        return -1;
    }
    return 0;
}



extern "C" JNIEXPORT jlong JNICALL
Java_com_zhtj_plugin_im_liveplayer_srt_JniPull_srtOpen(
        JNIEnv *env,
        jobject /* this */, jstring str_url) {

    int yes = 1;
    int no = 0;
    char server_ip[128];
    int server_port;
    char streamid[1024];
    int latency = 500;

    char *url = (char *) env->GetStringUTFChars(str_url, 0);
    //parse url
    if (strlen(url) == 0) {
        LOGD("wrong srt uri format, srt uri must be like vhost/app/stream_name or ip/app/stream_name?vhost=domain.\n");
        return false;
    }
    char *p = url;
    //protocal
    p = strchr(url, ':');
    if (!p) {
        return false;
    }
    p[0] = 0x00;
    if (strcmp(url, "srt") != 0)
        return false;

    p += 3;//skip 'srt://'

    //hostname:port
    char *p_tmp = strchr(p, ':');
    if (p_tmp) {
        p_tmp[0] = 0x00;
        strcpy(server_ip, p);
        p = p_tmp + 1;
    } else
        return false;

    //hostname
    p_tmp = strchr(p, '?');
    if (!p_tmp) //ignore '?'
        return false;

    p_tmp[0] = 0;
    server_port = atoi(p);
    p = p_tmp + 1;

    //streamid
    p_tmp = strchr(p, '=');
    if (!p) {
        return false;
    }
    p_tmp[0] = 0;
    if (strcmp(p, "streamid") != 0)
        return false;
    p = p_tmp + 1;
    strcpy(streamid, p);

    if (strlen(streamid) == 0) {
        LOGD("%s(%d): no streamid. \n", __FUNCTION__, __LINE__);
        return -1;
    }

    int client_pollid = srt_epoll_create();
    if (client_pollid == SRT_ERROR) {
        LOGD("%s(%d):Failed \n", __FUNCTION__, __LINE__);
        return -1;
    }

    SRTSOCKET m_client_sock = srt_socket(AF_INET, SOCK_DGRAM, 0);

    int status = srt_setsockopt(m_client_sock, 0, SRTO_SNDSYN, &no, sizeof no); // for async connect
    if (status == SRT_ERROR) {
        LOGD("%s(%d):Failed \n", __FUNCTION__, __LINE__);
    }

    srt_setsockflag(m_client_sock, SRTO_SENDER, &yes, sizeof yes);
    if (status == SRT_ERROR) {
        LOGD("%s(%d):Failed \n", __FUNCTION__, __LINE__);
    }

    status = srt_setsockopt(m_client_sock, 0, SRTO_TSBPDMODE, &yes, sizeof yes);
    if (status == SRT_ERROR) {
        LOGD("%s(%d):Failed \n", __FUNCTION__, __LINE__);
    }

    if (srt_setsockopt(m_client_sock, 0, SRTO_STREAMID, streamid, strlen(streamid)) < 0) {
        LOGD("%s(%d):srt_setsockopt SRTO_STREAMID Failed, streamid=%s\n", __FUNCTION__, __LINE__,
             streamid);
        return -1;
    }

    if (srt_setsockopt(m_client_sock, 0, SRTO_LATENCY, &latency, sizeof latency) < 0) {
        LOGD("%s(%d):srt_setsockopt SRTO_STREAMID Failed, streamid=%s\n", __FUNCTION__, __LINE__,
             streamid);
        return -1;
    }

    int epoll_out = SRT_EPOLL_OUT;
    srt_epoll_add_usock(client_pollid, m_client_sock, &epoll_out);

    struct sockaddr_in sa;
    memset(&sa, 0, sizeof sa);
    sa.sin_family = AF_INET;
    sa.sin_port = htons(server_port);

    //if (inet_pton(AF_INET, "192.168.1.45", &sa.sin_addr) != 1)
    if (inet_pton(AF_INET, server_ip, &sa.sin_addr) != 1) {
        LOGD("%s(%d):Failed \n", __FUNCTION__, __LINE__);
        //std::string hello = "inet_pton failed";
        //srt_cleanup();
        return -1;//env->NewStringUTF(hello.c_str());
    }

    struct sockaddr *psa = (struct sockaddr *) &sa;

    LOGD("%s(%d):srt_connect\n", __FUNCTION__, __LINE__);

    status = srt_connect(m_client_sock, psa, sizeof sa);
    if (status == SRT_ERROR) {
        LOGD("%s(%d):Failed \n", __FUNCTION__, __LINE__);
        LOGD("srt_connect: %s\n", srt_getlasterror_str());
        //std::string hello = "srt_connect failed";
        //srt_cleanup();
        return -1;//env->NewStringUTF(hello.c_str());
    }

    srtContext *sc = new srtContext;
    sc->client_pollid = client_pollid;
    sc->client_sock = m_client_sock;
    jlong ret = (jlong) sc;
    return ret;

}

extern "C" JNIEXPORT jint JNICALL
Java_com_zhtj_plugin_im_liveplayer_srt_JniPull_srtClose(
        JNIEnv *env,
        jobject /* this */, jlong srt) {

    srtContext *sc = (srtContext *) srt;

    if (!srt)
        return 0;
    if (sc->client_sock > 0) {
        srt_close(sc->client_sock);
    }
    if (sc->client_pollid > 0) {
        srt_epoll_release(sc->client_pollid);
    }
    delete sc;

    return 0;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_zhtj_plugin_im_liveplayer_srt_JniPull_srtRecv(
        JNIEnv *env,
        jobject /* this */, jlong srt) {

    srtContext *sc = (srtContext *) srt;
    if (!srt)
        return 0;
    // Socket readiness for connection is checked by polling on WRITE allowed sockets.
/*
    int rlen = 1;
    SRTSOCKET read[1];

    int wlen = 1;
    SRTSOCKET write[1];

    int status = srt_epoll_wait(sc->client_pollid, read, &rlen,
                                write, &wlen,
                                (int64_t)-1, // -1 is set for debuging purpose.
            // in case of production we need to set appropriate value
                                0, 0, 0, 0);
    if (status == SRT_ERROR) {
        LOGD("%s(%d):Failed \n", __FUNCTION__, __LINE__);
        return 0;
    }

    if (rlen != 0) {
        LOGD("%s(%d):Failed \n", __FUNCTION__, __LINE__);
        return 0;
    }

    if (wlen != 1) {
        LOGD("%s(%d):Failed \n", __FUNCTION__, __LINE__);
        return 0;
    }

    if (read[0] != sc->client_sock) {
        LOGD("%s(%d):Failed \n", __FUNCTION__, __LINE__);
        return 0;
    }
*/
    int ts_len = 1316;
    char recv_data[1316];
    int status = srt_recvmsg(sc->client_sock, recv_data, ts_len); // in order must be set to true
    if (status == SRT_ERROR) {
        LOGD("%s(%d):Failed \n", __FUNCTION__, __LINE__);
        LOGD("srt_recvmsg: %s\n", srt_getlasterror_str());
        return 0;
    }
    //nOutSize是BYTE数组的长度 BYTE pData[]
    jbyte *by = (jbyte *) recv_data;
    jbyteArray jarray = env->NewByteArray(status);
    env->SetByteArrayRegion(jarray, 0, status, by);
    return jarray;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_zhtj_plugin_im_liveplayer_srt_JniPull_srtGetSockState(
        JNIEnv *env,
        jobject /* this */, jlong srt) {

    srtContext *sc = (srtContext *) srt;
    if (!srt)
        return -1;

    return srt_getsockstate(sc->client_sock);
}



