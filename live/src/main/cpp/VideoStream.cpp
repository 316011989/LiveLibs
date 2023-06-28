
#include <cstring>
#include <__mutex_base>
#include "VideoStream.h"
#include "PushInterface.h"
#include "x264.h"

VideoStream::VideoStream():m_frameLen(0),
                           videoCodec(nullptr),
                           pic_in(nullptr),
                           videoCallback(nullptr) {

}

int VideoStream::setVideoEncInfo(int width, int height, int fps, int bitrate) {
    std::lock_guard<std::mutex> l(m_mutex);
    m_frameLen = width * height;
    if (videoCodec) {
        x264_encoder_close(videoCodec);
        videoCodec = nullptr;
    }
    if (pic_in) {
        x264_picture_clean(pic_in);
        delete pic_in;
        pic_in = nullptr;
    }

    //setting x264 params
    x264_param_t param;
    int ret = x264_param_default_preset(&param, "ultrafast", "zerolatency");
    if (ret < 0) {
        return ret;
    }
    param.i_level_idc = 32;
    //input format
    param.i_csp = X264_CSP_I420;
    param.i_width = width;
    param.i_height = height;
    //no B frame
    param.i_bframe = 0;
    //i_rc_method:bitrate control, CQP(constant quality), CRF(constant bitrate), ABR(average bitrate)
    param.rc.i_rc_method = X264_RC_ABR;
    //bitrate(Kbps)
    param.rc.i_bitrate = bitrate / 1024;
    //max bitrate
    param.rc.i_vbv_max_bitrate = bitrate / 1024 * 1.2;
    //unit:kbps
    param.rc.i_vbv_buffer_size = bitrate / 1024;

    //frame rate
    param.i_fps_num = fps;
    param.i_fps_den = 1;
    param.i_timebase_den = param.i_fps_num;
    param.i_timebase_num = param.i_fps_den;
    //using fps
    param.b_vfr_input = 0;
    //key frame interval(GOP)
    param.i_keyint_max = fps * 2;
    //each key frame attaches sps/pps
    param.b_repeat_headers = 1;
    //thread number
    param.i_threads = 1;

    ret = x264_param_apply_profile(&param, "baseline");
    if (ret < 0) {
        return ret;
    }
    //open encoder
    videoCodec = x264_encoder_open(&param);
    if (!videoCodec) {
        return -1;
    }
    pic_in = new x264_picture_t();
    x264_picture_alloc(pic_in, X264_CSP_I420, width, height);
    return ret;
}

void VideoStream::setVideoCallback(VideoCallback callback) {
    this->videoCallback = callback;
}

void VideoStream::sendSpsPps(uint8_t *sps, uint8_t *pps, int sps_len, int pps_len) {
    int bodySize = 13 + sps_len + 3 + pps_len;
    auto *packet = new RTMPPacket();
    RTMPPacket_Alloc(packet, bodySize);
    int i = 0;
    // type
    packet->m_body[i++] = 0x17;
    packet->m_body[i++] = 0x00;
    // timestamp
    packet->m_body[i++] = 0x00;
    packet->m_body[i++] = 0x00;
    packet->m_body[i++] = 0x00;

    //version
    packet->m_body[i++] = 0x01;
    // profile
    packet->m_body[i++] = sps[1];
    packet->m_body[i++] = sps[2];
    packet->m_body[i++] = sps[3];
    packet->m_body[i++] = 0xFF;

    //sps
    packet->m_body[i++] = 0xE1;
    //sps len
    packet->m_body[i++] = (sps_len >> 8) & 0xFF;
    packet->m_body[i++] = sps_len & 0xFF;
    memcpy(&packet->m_body[i], sps, sps_len);
    i += sps_len;

    //pps
    packet->m_body[i++] = 0x01;
    packet->m_body[i++] = (pps_len >> 8) & 0xFF;
    packet->m_body[i++] = (pps_len) & 0xFF;
    memcpy(&packet->m_body[i], pps, pps_len);

    //video
    packet->m_packetType = RTMP_PACKET_TYPE_VIDEO;
    packet->m_nBodySize  = bodySize;
    packet->m_nChannel   = 0x10;
    //sps and pps no timestamp
    packet->m_nTimeStamp = 0;
    packet->m_hasAbsTimestamp = 0;
    packet->m_headerType = RTMP_PACKET_SIZE_MEDIUM;

    videoCallback(packet);
}

void VideoStream::sendFrame(int type, uint8_t *payload, int i_payload) {
    if (payload[2] == 0x00) {
        i_payload -= 4;
        payload += 4;
    } else {
        i_payload -= 3;
        payload += 3;
    }
    int i = 0;
    int bodySize = 9 + i_payload;
    auto *packet = new RTMPPacket();
    RTMPPacket_Alloc(packet, bodySize);

    if (type == NAL_SLICE_IDR) {
        packet->m_body[i++] = 0x17; // 1:Key frame  7:AVC
    } else {
        packet->m_body[i++] = 0x27; // 2:None key frame 7:AVC
    }
    //AVC NALU
    packet->m_body[i++] = 0x01;
    //timestamp
    packet->m_body[i++] = 0x00;
    packet->m_body[i++] = 0x00;
    packet->m_body[i++] = 0x00;
    //packet len
    packet->m_body[i++] = (i_payload >> 24) & 0xFF;
    packet->m_body[i++] = (i_payload >> 16) & 0xFF;
    packet->m_body[i++] = (i_payload >> 8) & 0xFF;
    packet->m_body[i++] = (i_payload) & 0xFF;

    memcpy(&packet->m_body[i], payload, static_cast<size_t>(i_payload));

    packet->m_hasAbsTimestamp = 0;
    packet->m_nBodySize       = bodySize;
    packet->m_packetType      = RTMP_PACKET_TYPE_VIDEO;
    packet->m_nChannel        = 0x10;
    packet->m_headerType      = RTMP_PACKET_SIZE_LARGE;
    videoCallback(packet);
}

void VideoStream::encodeVideo(int8_t *data, int camera_type) {
    std::lock_guard<std::mutex> l(m_mutex);
    if (!pic_in)
        return;

    if (camera_type == 1) {
        memcpy(pic_in->img.plane[0], data, m_frameLen); // y
        for (int i = 0; i < m_frameLen/4; ++i) {
            *(pic_in->img.plane[1] + i) = *(data + m_frameLen + i * 2 + 1);  // u
            *(pic_in->img.plane[2] + i) = *(data + m_frameLen + i * 2); // v
        }
    } else if (camera_type == 2) {
        int offset = 0;
        memcpy(pic_in->img.plane[0], data, (size_t) m_frameLen); // y
        offset += m_frameLen;
        memcpy(pic_in->img.plane[1], data + offset, (size_t) m_frameLen / 4); // u
        offset += m_frameLen / 4;
        memcpy(pic_in->img.plane[2], data + offset, (size_t) m_frameLen / 4); // v
    } else {
        return;
    }

    x264_nal_t *pp_nal;
    int pi_nal;
    x264_picture_t pic_out;
    x264_encoder_encode(videoCodec, &pp_nal, &pi_nal, pic_in, &pic_out);
    int pps_len, sps_len = 0;
    uint8_t sps[100];
    uint8_t pps[100];
    for (int i = 0; i < pi_nal; ++i) {
        x264_nal_t nal = pp_nal[i];
        if (nal.i_type == NAL_SPS) {
            sps_len = nal.i_payload - 4;
            memcpy(sps, nal.p_payload + 4, static_cast<size_t>(sps_len));
        } else if (nal.i_type == NAL_PPS) {
            pps_len = nal.i_payload - 4;
            memcpy(pps, nal.p_payload + 4, static_cast<size_t>(pps_len));
            sendSpsPps(sps, pps, sps_len, pps_len);
        } else {
            sendFrame(nal.i_type, nal.p_payload, nal.i_payload);
        }
    }
}

VideoStream::~VideoStream() {
    if (videoCodec) {
        x264_encoder_close(videoCodec);
        videoCodec = nullptr;
    }
    if (pic_in) {
        x264_picture_clean(pic_in);
        delete pic_in;
        pic_in = nullptr;
    }
}
