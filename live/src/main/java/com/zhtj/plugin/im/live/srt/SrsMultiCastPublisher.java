
package com.zhtj.plugin.im.live.srt;

import android.util.Log;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.nio.ByteBuffer;

/**
 * Srs implementation of an RTMP publisher
 * 
 * @author francois, leoma
 */
public class SrsMultiCastPublisher   {

    //multicast
    private UpdGroupClient mMultiCast = new UpdGroupClient();

    private static final String TAG = "SrsPublisher";


    public boolean open(String url) {
        return mMultiCast.open(url);
    }


    public boolean publish(String publishType) {
        return true;
    }


    public void close() {

        if (mMultiCast != null){
            mMultiCast.close();
        }
        return;

    }

    public int send(ByteBuffer data) {
        int ret = -1;
        if (mMultiCast != null){
            ret = data.limit();
            mMultiCast.send(data);
            data.flip();
        }
        return ret;
   }


    public class UpdGroupClient {

        private String multi_ip = "224.0.0.1";//组播地址
        private int multi_port = 5001;//指定数据接收端口
        private MulticastSocket multi_sock = null;
        private InetAddress inet_address;


        public boolean open(String url) {
            String temp = "";

            if (url.substring(0, 6).compareToIgnoreCase("udp://") != 0) {
                Log.e(TAG, String.format("UdpGroupClient, wrong url='%s', not start with 'udp://'.", url));
                return false;
            }
            url = url.replaceFirst("udp://", "");
            multi_ip = url.substring(0, url.indexOf(":"));
            temp = url.substring(url.indexOf(":")+1, url.length());
            try {
                multi_port = Integer.parseInt(temp);
            } catch (NumberFormatException e) {
                Log.e(TAG, String.format("UdpGroupClient, port='%s' is not integer.", temp));
                e.printStackTrace();
            }

            try {
                inet_address = InetAddress.getByName(multi_ip); //指定组播地址
                multi_sock = new MulticastSocket(multi_port);//创建组播socket
                multi_sock.joinGroup(inet_address);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return true;
        }

        public void close(){
            if(null != multi_sock){
                try {
                    multi_sock.leaveGroup(inet_address);
                    multi_sock.close();;
                } catch (Exception e) {
                    e.printStackTrace();
                }
                multi_sock = null;
            }
        }

        public void send(ByteBuffer data) {

            if (multi_sock == null) {
                Log.e(TAG, "UdpGroupClient send, multisock is null.");
                return;
            }
            try {
                byte[] message = new byte[data.limit()]; //发送信息
                data.get(message, 0, data.limit());
                DatagramPacket datagramPacket = new DatagramPacket(message, message.length, inet_address, multi_port); //发送数据包
                multi_sock.send(datagramPacket);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }
}
