package raknetserver.utils;

import io.netty.handler.codec.DecoderException;
import io.netty.util.internal.SystemPropertyUtil;

import java.net.InetSocketAddress;

public class Constants {

	public static final int MAX_PACKET_LOSS = SystemPropertyUtil.getInt("raknetserver.maxPacketLoss", 8192);

    public static final byte[] MAGIC = new byte[] { (byte) 0x00, (byte) 0xff, (byte) 0xff, (byte) 0x00,
            (byte) 0xfe, (byte) 0xfe, (byte) 0xfe, (byte) 0xfe, (byte) 0xfd, (byte) 0xfd, (byte) 0xfd,
            (byte) 0xfd, (byte) 0x12, (byte) 0x34, (byte) 0x56, (byte) 0x78 };

	public static void packetLossCheck(int n, String location) {
        if (n > Constants.MAX_PACKET_LOSS) {
            throw new DecoderException("Too big packet loss: " + location);
        }
    }

}
