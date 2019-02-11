package raknetserver.utils;

import io.netty.handler.codec.DecoderException;

import java.util.concurrent.TimeUnit;

public class Constants {

	public static final int BACK_PRESSURE_HIGH_WATERMARK = Integer.parseInt(System.getProperty("raknetserver.backPressureHighWatermark", "256"));
	public static final int BACK_PRESSURE_LOW_WATERMARK = Integer.parseInt(System.getProperty("raknetserver.backPressureLowWatermark", "128"));
	public static final int MAX_PACKET_LOSS = Integer.parseInt(System.getProperty("raknetserver.maxPacketLoss", "8192"));
	public static final int MAX_PACKET_SPLITS = Integer.parseInt(System.getProperty("raknetserver.maxPacketSplits", "4096"));
	public static final int UDP_IO_THREADS = Integer.parseInt(System.getProperty("raknetserver.udpIOThreads", "4"));
	public static final int RETRY_TICK_OFFSET = Integer.parseInt(System.getProperty("raknetserver.retryTickOffset", "2"));
	public static final int RESEND_PER_TICK = Integer.parseInt(System.getProperty("raknetserver.resendPerTick", "5"));
	public static final long MAX_RTT = TimeUnit.NANOSECONDS.convert(
			Integer.parseInt(System.getProperty("raknetserver.maxRTTms", "2000")), TimeUnit.MILLISECONDS);

	public static void packetLossCheck(int n, String location) {
        if (n > Constants.MAX_PACKET_LOSS) {
            throw new DecoderException("Too big packet loss: " + location);
        }
    }

}
