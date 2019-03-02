package raknet.utils;

import io.netty.handler.codec.DecoderException;
import io.netty.util.internal.SystemPropertyUtil;

public class Constants {

	public static final int MAX_PACKET_LOSS = SystemPropertyUtil.getInt("raknetserver.maxPacketLoss", 8192);

	public static void packetLossCheck(int n, String location) {
        if (n > Constants.MAX_PACKET_LOSS) {
            throw new DecoderException("Too big packet loss: " + location);
        }
    }

}
