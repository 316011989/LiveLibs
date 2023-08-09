package com.zhtj.plugin.im.srtplayer;

import android.media.MediaCodec;
import android.util.Log;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;

//list
import java.util.ArrayList;
import java.util.Arrays;
import java.nio.ByteOrder;
import java.util.List;

/**
 * Created by Edward.Wu on 2019/03/28.
 * to POST the h.264/avc annexb frame over multi cast.
 * @see android.media.MediaMuxer https://developer.android.com/reference/android/media/MediaMuxer.html
 */
public class SLSTSDemuxer {

    private volatile boolean connected = false;
    //private SrsTSReceiver receiver = new SrsTSReceiver();

    private Thread worker;
    private final Object txFrameLock = new Object();

    private static final int PES_MAX_LEN = 2 * 1024 * 1024;
    private static final int SPSPPS_MAX_LEN = 256;
    private static final int TS_PACK_LEN = 188;
    private static final int TS_UDP_PACK_LEN = 7 * TS_PACK_LEN;
    private static final byte TS_SYNC_BYTE = 0x47;

    private static final int TS_90K_CLOCK = 90;
    private static final int TS_27M_CLOCK = 90 * 300;
    private static final int TS_PCR_DTS_DELAY = 500 * TS_90K_CLOCK;//ms
    private static final int TS_PATPMT_INTERVAL = 400 * TS_90K_CLOCK;//ms

    private static final int AAC_ADTS_HEADER_LEN = 7;//fixed


    private static final short TS_VIDEO_PID = 0x100;
    private static final short TS_AUDIO_PID = 0x101;
    private static final short NULL_PACK_PID = 0x1FFF;
    private static final byte TS_VIDEO_SID = (byte) 0xe0;
    private static final byte TS_AUDIO_SID = (byte) 0xc0;
    private static final int  MAX_PES_PAYLOAD = 200 * 1024;


    private static final int VIDEO_TRACK = 100;
    private static final int AUDIO_TRACK = 101;

    private SrsPESFrame mVideoPESFrame = new SrsPESFrame();
    private SrsPESFrame mAudioPESFrame = new SrsPESFrame();

    private long mLastSendPATDTS = 0;
    private byte mPATPMTCC = 0;

    private long mLastSendPCR = 0;


    private SrsRawH264Stream avc = new SrsRawH264Stream();
    private SrsAVCMedia mVideoMedia = new SrsAVCMedia();
    private SrsAACMedia mAudioMedia = new SrsAACMedia();

    private SrsUDPPackList mTSUDPPackList = new SrsUDPPackList();

    private static final String TAG = "SrsTSDemuxer";
    private long mnetDelay = 0;


    /**
     * constructor.
     */
    public SLSTSDemuxer() {

    }

    public long getNetDelay() {
        return mnetDelay;
    }
    public boolean isConneced() {
        return connected;
    }

    /**
     * get cached video frame number in publisher
     */
    //public AtomicInteger getVideoFrameCacheNumber() {
    //    return publisher == null ? null : publisher.getVideoFrameCacheNumber();
    //}


    private void disconnect() {
        try {
            //publisher.close();
        } catch (IllegalStateException e) {
            // Ignore illegal state.
        }
        connected = false;
        Log.i(TAG, "worker: disconnect ok.");
    }

    private boolean connect(String url) {
        if (!connected) {
            Log.i(TAG, String.format("worker: connecting to RTMP server by url=%s\n", url));
            //if (publisher.connect(url)) {
            //    connected = publisher.publish("live");
            //}
        }
        return connected;
    }

    /*
    private void sendTSPack(ByteBuffer pack) {
        if (!connected || pack == null || publisher == null) {
            return;
        }
        publisher.publishData(pack, pack.limit());

    }
*/
    private void reset(){
        mLastSendPATDTS = 0;
        mPATPMTCC = 0;
        mLastSendPCR = 0;

        mAudioMedia.reset();
        mVideoMedia.reset();
        mTSUDPPackList.reset();
    }

    public void setVideoDecoder(SLSMediaCodec decoder)
    {
        mVideoPESFrame.decoder = decoder;
    }
    public void setAudioDecoder(SLSMediaCodec decoder)
    {
        mAudioPESFrame.decoder = decoder;
    }

    public boolean isTSListFull()
    {
        return mTSUDPPackList.isFull();
    }

    public boolean addTSPack(byte[] data)
    {
        ByteBuffer ts_udp_pack = ByteBuffer.allocateDirect(data.length);
        ts_udp_pack.put(data);
        ts_udp_pack.flip();
        return mTSUDPPackList.SrsPutTSData(ts_udp_pack);
    }

    public boolean addTSPack(ByteBuffer data)
    {
        data.flip();
        return mTSUDPPackList.SrsPutTSData(data);
    }
    /**
     * start to the remote server for remux.
     */
    public void start() {
        reset();

        worker = new Thread(new Runnable() {
            @Override
            public void run() {

                while (!Thread.interrupted()) {
                    while (!mTSUDPPackList.isEmpty()) {
                        ByteBuffer tsUDPPack = mTSUDPPackList.poll();
                        SrsParseTSPack(tsUDPPack);
                    }
                    // Waiting for next frame
                    synchronized (txFrameLock) {
                        try {
                            // isEmpty() may take some time, so we set timeout to detect next frame
                            txFrameLock.wait(10);
                        } catch (InterruptedException ie) {
                            worker.interrupt();
                        }
                    }
                }
            }
        });
        worker.start();
    }

    /**
     * stop the muxer, disconnect RTMP connection.
     */
    public void stop() {
        mTSUDPPackList.clear();
        if (worker != null) {
            worker.interrupt();
            try {
                worker.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
                worker.interrupt();
            }
            worker = null;
        }
        Log.i(TAG, "SrsTSDemuxer closed");

        new Thread(new Runnable() {
            @Override
            public void run() {
                disconnect();
            }
        }).start();
    }


    private void SrsParseTSPack(ByteBuffer tsUDPPack)
    {
        byte[] ts_pack = new byte[TS_PACK_LEN];
        if (tsUDPPack.remaining() != TS_UDP_PACK_LEN) {
            Log.i(TAG, "SrsParseTSPack ts udp len is not 1316.");
            return ;
        }
        while (tsUDPPack.remaining() > 0) {
            tsUDPPack.get(ts_pack);
            SrsTSToES(ts_pack);
        }
    }
    private long ff_parse_pes_pts(byte[] buf) {

        //FIXME???
        long pts = 0;
        long tmp = (long)((buf[0] & 0x0e) << 29) ;
        pts = pts | tmp;
        tmp = (long)((((long)(buf[1]&0xFF)<<8)|(buf[2]) >> 1) << 15);
        pts = pts | tmp;
        tmp = (long)((((long)(buf[3]&0xFF)<<8)|(buf[4])) >> 1);
        pts = pts | tmp;
        return pts;
        /*
        return (int64_t)((*buf & 0x0e) << 29 |
            (AV_RB16(buf+1) >> 1) << 15 |
             AV_RB16(buf+3) >> 1);
             */
    }

    private int SrsPEStoES(SrsPESFrame pesFrame)
    {
        if (pesFrame.data.position() == 0) {
            Log.i(TAG, "SrsPEStoES pesFrame.data.position() = 0.");
            return 0;
        }

        if (!pesFrame.b_start) {
            return 0;
        }

        pesFrame.data.flip();
        int pos = 0;
        byte[] pesHeader = new byte[3];
        pesFrame.data.get(pesHeader);

        if (pesHeader[0] != 0x00 ||
                pesHeader[1] != 0x00 ||
                pesHeader[2] != 0x01) {
            Log.i(TAG, "SrsPEStoES wrong pes start code.");
            return 0;
        }

        /* it must be an MPEG-2 PES stream */
        int stream_id = (pesFrame.data.get() & 0xFF);
        if (stream_id != 0xE0 && stream_id != 0xC0) {
            Log.i(TAG, "SrsPEStoES wrong pes stream_id=" + stream_id);
            return 0;
        }
        byte[] total_size = new byte[2];
        pesFrame.data.get(total_size);

        pesFrame.total_size = ((int)(total_size[0]<<8)) | total_size[1];
        /* NOTE: a zero total size means the PES size is
         * unbounded */
        if (0 == pesFrame.total_size)
            pesFrame.total_size = MAX_PES_PAYLOAD;
        int flags = 0;
        /*
        '10'                        :2,
        PES_scrambling_control      :2,
        PES_priority                :1,
        data_alignment_indicator    :1,
        copyright                   :1,
        original_or_copy            :1
         */
        flags = (pesFrame.data.get() & 0x7F);
        /*
        PTS_DTS_flags               :2,
        ESCR_flag                   :1,
        ES_rate_flag:1,
        DSM_trick_mode_flag:1,
        additional_copy_info_flag:1,
        PES_CRC_flag:1,
        PES_extension_flag:1,
         */
        flags = (pesFrame.data.get() & 0xFF);
        pesFrame.header_len = (pesFrame.data.get() & 0xFF);
        pesFrame.dts = -1;
        pesFrame.pts = -1;
        byte[] pts = new byte[5];
        if ((flags & 0xc0) == 0x80) {
            pesFrame.data.get(pts);
            pesFrame.dts = pesFrame.pts = ff_parse_pes_pts(pts);
        } else if ((flags & 0xc0) == 0xc0) {
            pesFrame.data.get(pts);
            pesFrame.pts = ff_parse_pes_pts(pts);
            pesFrame.dts = ff_parse_pes_pts(pts);
        }

        //put data to decoder
        byte[] esData = new byte[pesFrame.data.remaining()];
        pesFrame.data.get(esData);
        pesFrame.sys_tm = System.currentTimeMillis();
        if (null != pesFrame.decoder) {
            //Log.i(TAG, "SrsPEStoES, add video es.length==" + esData.length);
            pesFrame.decoder.addESData(esData, esData.length, pesFrame.dts, pesFrame.dts);
        }

        pesFrame.reset();
        return 0;
    }

    /**
     * 利用 {@link java.nio.ByteBuffer}实现byte[]转long
     * @param input
     * @param offset
     * @param littleEndian 输入数组是否小端模式
     * @return
     */
    public static long bytesToLong(byte[] input, int offset, boolean littleEndian) {
        // 将byte[] 封装为 ByteBuffer
        ByteBuffer buffer = ByteBuffer.wrap(input,offset,8);
        if(littleEndian){
            // ByteBuffer.order(ByteOrder) 方法指定字节序,即大小端模式(BIG_ENDIAN/LITTLE_ENDIAN)
            // ByteBuffer 默认为大端(BIG_ENDIAN)模式
            buffer.order(ByteOrder.LITTLE_ENDIAN);
        }
        return buffer.getLong();
    }

    private int SrsTSToES(byte[] packet){

        SrsPESFrame pesFrame = null;

        if (packet[0] != TS_SYNC_BYTE) {
            Log.i(TAG, "SrsTSToES tsPack[0] is " + packet[0] + ", expect 0x47.");
            return 0;
        }
        int is_start = packet[1] & 0x40;

        int pid = ((int)(packet[1] & 0x1F) << 8) | packet[2]&0xFF;
        if (pid == TS_VIDEO_PID) {
            pesFrame = mVideoPESFrame;
        } else if (pid == TS_AUDIO_PID) {
            pesFrame = mAudioPESFrame;
        } else if (pid == NULL_PACK_PID) {
            if (0 != is_start) {
                long sysTm = System.currentTimeMillis();
                long tm = bytesToLong(packet, 4, false);
                mnetDelay = sysTm - tm;
                Log.i(TAG, String.format("SrsTSToES, net send tm=%d, net delay d=%d,", tm, mnetDelay));
            }
            return 0;

        } else {
            Log.i(TAG, "SrsTSToES other pid=" + pid );
            return 0;
        }

        if (0 != is_start) {
            if (pesFrame.data.position() > 0) {
                SrsPEStoES(pesFrame);
            }
            pesFrame.b_start = true;
        }
        if (!pesFrame.b_start) {
            return 0;
        }

        int afc = (packet[3] >> 4) & 3;
        if (afc == 0) /* reserved value */
            return 0;
        int has_adaptation   = afc & 2;
        int has_payload      = afc & 1;
        boolean is_discontinuity = (has_adaptation == 1) &&
                (packet[4] != 0) && /* with length > 0 */
                ((packet[5] & 0x80) != 0); /* and discontinuity indicated */

        /* continuity check (currently not used) */
        int cc = (packet[3] & 0xf);
        int expected_cc = (has_payload == 1) ? (pesFrame.last_cc + 1) & 0x0f : pesFrame.last_cc;
        boolean cc_ok = (pid == 0x1FFF) || // null packet PID
                is_discontinuity ||
                (pesFrame.last_cc < 0) ||
                (expected_cc == cc);

        pesFrame.last_cc = cc;
        if (!cc_ok) {
            Log.i(TAG, "SrsTSToES, Continuity check failed for pid " + pid +
                    " expected " + expected_cc + "but " + cc );
        }

        if ((packet[1] & 0x80) != 0) {
            Log.i(TAG, "SrsTSToES, Packet had TEI flag set; marking as corrupt ");
        }

        int pos = 4;
        int p = (packet[pos] & 0xFF);
        if (has_adaptation != 0) {
            long pcr_h;
            int pcr_l;
            //if (parse_pcr(&pcr_h, &pcr_l, packet) == 0)
            //ts->last_pcr = pcr_h * 300 + pcr_l;
            /* skip adaptation field */
            pos += p + 1;
        }
        /* if past the end of packet, ignore */
        if (pos >= TS_PACK_LEN || 1 != has_payload)
            return 0;

        //copy data directly
        pesFrame.putData(packet, pos, TS_PACK_LEN);

        return 0;
    }

    private void SrsGetPCR(ByteBuffer data, long pcr) {
        byte temp ;
        temp = (byte) (pcr >> 25);
        data.put(temp);
        temp = (byte) (pcr >> 17);
        data.put(temp);
        temp = (byte) (pcr >> 9);
        data.put(temp);
        temp = (byte) (pcr >> 1);
        data.put(temp);
        temp = (byte) (pcr << 7 | 0x7e);
        data.put(temp);
        temp = 0;
        data.put(temp);

    }

    private void SrsGetPTS(ByteBuffer data, int fb, long pts) {

        long val = 0;
        byte b;
        short s;

        val = fb << 4 | (((pts >> 30) & 0x07) << 1) | 1;
        data.put((byte)val);

        val = (((pts >> 15) & 0x7fff) << 1) | 1;

        data.put((byte)(val>>8));
        data.put((byte)(val));

        val = (((pts) & 0x7fff) << 1) | 1;
        data.put((byte)(val>>8));
        data.put((byte)(val));

    }

    /**
     * the aac object type, for RTMP sequence header
     * for AudioSpecificConfig, @see aac-mp4a-format-ISO_IEC_14496-3+2001.pdf, page 33
     * for audioObjectType, @see aac-mp4a-format-ISO_IEC_14496-3+2001.pdf, page 23
     */
    private class SrsAacObjectType
    {
        public final static int Reserved = 0;

        // Table 1.1 – Audio Object Type definition
        // @see @see aac-mp4a-format-ISO_IEC_14496-3+2001.pdf, page 23
        public final static int AacMain = 1;
        public final static int AacLC = 2;
        public final static int AacSSR = 3;

        // AAC HE = LC+SBR
        public final static int AacHE = 5;
        // AAC HEv2 = LC+SBR+PS
        public final static int AacHEV2 = 29;
    }

    /**
     * the aac profile, for ADTS(HLS/TS)
     * @see https://github.com/simple-rtmp-server/srs/issues/310
     */
    private class SrsAacProfile
    {
        public final static int Reserved = 3;

        // @see 7.1 Profiles, aac-iso-13818-7.pdf, page 40
        public final static int Main = 0;
        public final static int LC = 1;
        public final static int SSR = 2;
    }

    /**
     * the FLV/RTMP supported audio sample rate.
     * Sampling rate. The following values are defined:
     * 0 = 5.5 kHz = 5512 Hz
     * 1 = 11 kHz = 11025 Hz
     * 2 = 22 kHz = 22050 Hz
     * 3 = 44 kHz = 44100 Hz
     */
    private class SrsCodecAudioSampleRate
    {
        // set to the max value to reserved, for array map.
        public final static int Reserved                 = 4;

        public final static int R5512                     = 0;
        public final static int R11025                    = 1;
        public final static int R22050                    = 2;
        public final static int R44100                    = 3;
    }


    /**
     * Table 7-1 – NAL unit type codes, syntax element categories, and NAL unit type classes
     * H.264-AVC-ISO_IEC_14496-10-2012.pdf, page 83.
     */
    private class SrsAvcNaluType
    {
        // Unspecified
        public final static int Reserved = 0;

        // Coded slice of a non-IDR picture slice_layer_without_partitioning_rbsp( )
        public final static int NonIDR = 1;
        // Coded slice data partition A slice_data_partition_a_layer_rbsp( )
        public final static int DataPartitionA = 2;
        // Coded slice data partition B slice_data_partition_b_layer_rbsp( )
        public final static int DataPartitionB = 3;
        // Coded slice data partition C slice_data_partition_c_layer_rbsp( )
        public final static int DataPartitionC = 4;
        // Coded slice of an IDR picture slice_layer_without_partitioning_rbsp( )
        public final static int IDR = 5;
        // Supplemental enhancement information (SEI) sei_rbsp( )
        public final static int SEI = 6;
        // Sequence parameter set seq_parameter_set_rbsp( )
        public final static int SPS = 7;
        // Picture parameter set pic_parameter_set_rbsp( )
        public final static int PPS = 8;
        // Access unit delimiter access_unit_delimiter_rbsp( )
        public final static int AccessUnitDelimiter = 9;
        // End of sequence end_of_seq_rbsp( )
        public final static int EOSequence = 10;
        // End of stream end_of_stream_rbsp( )
        public final static int EOStream = 11;
        // Filler data filler_data_rbsp( )
        public final static int FilterData = 12;
        // Sequence parameter set extension seq_parameter_set_extension_rbsp( )
        public final static int SPSExt = 13;
        // Prefix NAL unit prefix_nal_unit_rbsp( )
        public final static int PrefixNALU = 14;
        // Subset sequence parameter set subset_seq_parameter_set_rbsp( )
        public final static int SubsetSPS = 15;
        // Coded slice of an auxiliary coded picture without partitioning slice_layer_without_partitioning_rbsp( )
        public final static int LayerWithoutPartition = 19;
        // Coded slice extension slice_layer_extension_rbsp( )
        public final static int CodedSliceExt = 20;
    }

    /**
     * the search result for annexb.
     */
    private class SrsAnnexbSearch {
        public int nb_start_code = 0;
        public int nal_unit_type = 0;
        public boolean match = false;
    }

    private class SrsTSUdpPack
    {
        private ByteBuffer ts_udp_pack = ByteBuffer.allocateDirect(TS_UDP_PACK_LEN);

        boolean PutData(ByteBuffer src)
        {
            int len = src.remaining();
            if (len % TS_PACK_LEN != 0) {
                Log.i(TAG, String.format("PutData error, ByteBuffer src len = %d, not nx188.", src.remaining()));
                return false;
            }
            if (ts_udp_pack.remaining() >= len){
                ts_udp_pack.put(src);
                return true;
            }

            Log.i(TAG, String.format("PutData error, ts_udp_pack.remainder len = %d.", ts_udp_pack.remaining()));
            return false;
        }
        ByteBuffer GetData()
        {
            return ts_udp_pack;
        }

        boolean IsFull(){
            return ts_udp_pack.position() == ts_udp_pack.limit();
        }

    }

    private class SrsUDPPackList {

        private ConcurrentLinkedQueue<ByteBuffer> udp_packs = new ConcurrentLinkedQueue<>();
        public SrsTSUdpPack ts_udp_pack ;
        private int ts_udp_pack_max_count = 1024;

        private final Object publishLock = new Object();

        public boolean add(ByteBuffer data){
            return udp_packs.add(data);
        }

        public ByteBuffer poll() {
            synchronized (publishLock) {//multiple thread for audio and video encoder
                return udp_packs.poll();
            }
        }

        public void clear() {
            synchronized (publishLock) {//multiple thread for audio and video encoder
                udp_packs.clear();
            }
        }

        public boolean isFull(){
            synchronized (publishLock) {//multiple thread for audio and video encoder
                if (udp_packs.size() >= ts_udp_pack_max_count)
                    return true;
                return false;
            }
        }

        public boolean isEmpty(){
            synchronized (publishLock) {//multiple thread for audio and video encoder
                return udp_packs.isEmpty();
            }
        }

        boolean SrsPutTSData( ByteBuffer ts_pack){
            synchronized (publishLock) {//multiple thread for audio and video encoder
                if (udp_packs.size() >= ts_udp_pack_max_count) {
                    Log.i(TAG, String.format("SrsPutTSData, udp_packs is full, len=", udp_packs.size()));
                    return false;
                }

                if (null == ts_udp_pack) {
                    ts_udp_pack = new SrsTSUdpPack();
                }

                if (ts_udp_pack.IsFull()) {
                    ts_udp_pack.GetData().flip();
                    udp_packs.add(ts_udp_pack.GetData());
                    ts_udp_pack = new SrsTSUdpPack();
                }
                return ts_udp_pack.PutData(ts_pack);
            }
        }

        public void reset(){
            synchronized (publishLock) {//multiple thread for audio and video encoder
                udp_packs.clear();
                if (null != ts_udp_pack) {
                    ts_udp_pack.GetData().clear();
                }
            }
        }

    }


    private class SrsAACMedia {
        public boolean aac_specific_config_got;
        private SrsPESFrame mAudioFrame = new SrsPESFrame();
        public ByteBuffer adts_header = ByteBuffer.allocateDirect(AAC_ADTS_HEADER_LEN );

        private int achannel;
        private int asample_rate;

        public SrsCalcPTS mCalcPTS = new SrsCalcPTS();

        public SrsAACMedia() {
            mAudioFrame.pid = TS_AUDIO_PID;
            mAudioFrame.sid = TS_AUDIO_SID;

        }

        public void reset(){
            mAudioFrame.reset();
            mCalcPTS.reset();
        }


    }


    private class SrsAVCMedia {
        private SrsAVCSpsPps h264_sps = new SrsAVCSpsPps();
        private SrsAVCSpsPps h264_pps = new SrsAVCSpsPps();
        private SrsPESFrame mVideoFrame = new SrsPESFrame();

        public SrsCalcPTS mCalcPTS = new SrsCalcPTS();

        public SrsAVCMedia(){
            mVideoFrame.pid = TS_VIDEO_PID;
            mVideoFrame.sid = TS_VIDEO_SID;
        }

        public void reset()
        {
            mVideoFrame.reset();
            mCalcPTS.reset();
        }

    }

    /**
     * the demuxed tag frame.
     */
    private class SrsPESFrame {
        public ByteBuffer data = ByteBuffer.allocateDirect(PES_MAX_LEN );
        public int index = 0;
        public int ts_index = 0;
        public int len = 0;
        public int nal_unit_type;
        public boolean key_frame = false;
        public boolean b_start = false;
        public int last_cc = 0;
        public int total_size = 0;
        public int header_len = 0;

        public SLSMediaCodec decoder = null;


        long dts = 0;
        long pts = 0;
        long real_pts = 0;
        long pcr = 0;
        long duration = 0;
        long sys_tm  = 0;

        byte cc = 0;
        short pid = 0;
        byte sid = 0;


        public void reset(){
            index = 0;
            ts_index = 0;
            len = 0;
            key_frame = false;


            dts = 0;
            pts = 0;
            real_pts = 0;
            pcr = 0;
            duration =0;

            cc = 0;

            data.clear();

        }

        public int putData(byte[] packet, int pos, int len)
        {
            byte[] esData = Arrays.copyOfRange(packet, pos, len);
            data.put(esData);
            return len;
        }


    }

    private class SrsCalcPTS
    {
        private List<Long> pts_list = new ArrayList<Long>();
        private long pts_d_sum = 0;
        private final int max_count = 5000;
        private long last_real_pts = 0;
        private long last_pts = 0;

        private boolean b_start = false;

        public void reset(){
            pts_d_sum = 0;
            last_real_pts = 0;
            last_pts = 0;
            b_start = false;

            pts_list.clear();
        }

        public long PutPTS(long pts){
            long temp = 0;

            if (!b_start){
                if (pts_list.size() == 0){
                    last_real_pts = pts;
                    last_pts = pts;
                    b_start = true;
                    return pts;
                }
            }
            if (pts_list.size() >= max_count){
                temp = pts_list.remove(0);
                pts_d_sum -= temp;
            }

            temp  = pts - last_real_pts;
            last_real_pts = pts;

            pts_list.add(temp);
            pts_d_sum += temp;

            temp = pts_d_sum / pts_list.size();
            last_pts += temp;
            return  last_pts ;

        }
    }


    private class SrsAVCSpsPps
    {
        public ByteBuffer data = ByteBuffer.allocateDirect(SPSPPS_MAX_LEN);
        public boolean b_changed = true;
    }


    private class SrsRawH264Stream {
        private final static String TAG = "SrsFlvMuxer";

        private SrsAnnexbSearch annexb = new SrsAnnexbSearch();

        public boolean isSps(SrsPESFrame frame) {
            return frame.len >= 1 && frame.nal_unit_type == SrsAvcNaluType.SPS;
        }

        public boolean isPps(SrsPESFrame frame) {
            return frame.len >= 1 && frame.nal_unit_type == SrsAvcNaluType.PPS;
        }


        private SrsAnnexbSearch searchAnnexb(ByteBuffer bb, MediaCodec.BufferInfo bi) {
            annexb.match = false;
            annexb.nb_start_code = 0;

            for (int i = bb.position(); i < bi.size - 3; i++) {
                // not match.
                if (bb.get(i) != 0x00 || bb.get(i + 1) != 0x00) {
                    break;
                }

                // match N[00] 00 00 01, where N>=0
                if (bb.get(i + 2) == 0x01) {
                    annexb.match = true;
                    annexb.nb_start_code = i + 3 - bb.position();
                    annexb.nal_unit_type = bb.get(i + 3)  & 0x1f;
                    break;
                }
            }

            return annexb;
        }

        public SrsPESFrame demuxAnnexb(ByteBuffer bb, MediaCodec.BufferInfo bi) {
            SrsPESFrame tbb = new SrsPESFrame();

            while (bb.position() < bi.size) {
                // each frame must prefixed by annexb format.
                // about annexb, @see H.264-AVC-ISO_IEC_14496-10.pdf, page 211.
                SrsAnnexbSearch tbbsc = searchAnnexb(bb, bi);
                if (!tbbsc.match || tbbsc.nb_start_code < 3) {
                    Log.e(TAG, "annexb not match.");
                    //mHandler.notifyRtmpIllegalArgumentException(new IllegalArgumentException(
                    //    String.format("annexb not match for %dB, pos=%d", bi.size, bb.position())));
                }
                tbb.nal_unit_type = tbbsc.nal_unit_type;

               // find out the frame size.
                tbb.data = bb.slice();
                int pos = bb.position();

                // the start codes.
                for (int i = 0; i < tbbsc.nb_start_code; i++) {
                    bb.get();
                }

                while (bb.position() < bi.size) {
                    SrsAnnexbSearch bsc = searchAnnexb(bb, bi);
                    if (bsc.match) {
                        break;
                    }
                    bb.get();
                }

                tbb.len = bb.position() - pos;
                break;
            }

            return tbb;
        }
    }

 }
