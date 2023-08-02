
package com.zhtj.plugin.im.live.srt;

import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.atomic.AtomicInteger;

//multicast


/**
 * Srs implementation of an RTMP publisher
 * 
 * @author francois, leoma
 */
public class SrsPublisher {

    private AtomicInteger videoFrameCacheNumber = new AtomicInteger(10);

    //file
    private String mTSFileName = Environment.getExternalStorageDirectory().getPath() + "/test-av.ts";
    private File mTSFile = null;
    private FileOutputStream fos = null;
    private FileChannel fc = null;
    private boolean save_file = false;

    protected SLSTSDemuxer mTSDemuxer = null;

    private static final String TAG = "SrsPublisher";


    public void setSaveFile() {
        save_file = true;
    }

    public int state()
    {
        return -1;
    }
    public boolean open(String url) {

        if (save_file){
            //open file for test
            mTSFile = new File(mTSFileName);

            mTSFile.delete();
            if (!mTSFile.exists()) {
                try {
                    // 如果文件找不到，就new一个
                    mTSFile.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            try {
                // 定义输出流，写入文件的流
                fos = new FileOutputStream(mTSFile);
                fc = fos.getChannel();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            Log.i(TAG, "connect: open file '" + mTSFileName + "', fc=" + fc);
        }
        return true;
    }


    public boolean connect(String publishType) {
        return true;
    }

    public boolean publish(String publishType) {
        return true;
    }
    public void setDemuxer(SLSTSDemuxer demuxer) {
        mTSDemuxer = demuxer;
    }

    public void close() {

        try {
            if (fos != null) {
                fos.close();
                fos = null;
            }

            if (fc != null) {
                fc.close();
                fc = null;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //必须调用完后flip()才可以调用此方法
    public byte[] byteBuffer2Byte(ByteBuffer byteBuffer){
        int len = byteBuffer.limit() - byteBuffer.position();
        byte[] bytes = new byte[len];

        if(byteBuffer.isReadOnly()){
            return null;
        }else {
            byteBuffer.get(bytes);
        }
        return bytes;
    }

    public int send(ByteBuffer data) {

        int ret = -1;
        if (fc != null){
            try {
                // 写入bs中的数据到file中
//                Log.i(TAG, "publishData: write file, size=" + data.remaining() );
                if (data.hasRemaining()) {
                    fc.write(data);
                    data.flip();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            ret = data.limit();
        }
        return ret;
    }

    public boolean startRecv() {
        new Thread(){
            @Override
            public void run() {
                super.run();
                RecvData();
            }
        }.start();
        return true;
    }

    public boolean RecvData() {
        return true;
    }
    public boolean stop() {
        return false;
    }

    public AtomicInteger getVideoFrameCacheNumber() {
        return videoFrameCacheNumber;
    }


    public final String getServerIpAddr() {
        return "";
    }


    public final int getServerPid() {
        return 1;
    }


    public final int getServerId() {
        return 1;
    }


    public void setVideoResolution(int width, int height) {
        return;
    }

}
