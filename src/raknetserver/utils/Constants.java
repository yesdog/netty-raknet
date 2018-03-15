package raknetserver.utils;

public class Constants {

	public static final int MAX_PACKET_LOSS = Integer.parseInt(System.getProperty("raknetserver.maxPacketLoss", "10240"));
	public static final int MAX_PACKET_SPLITS = Integer.parseInt(System.getProperty("raknetserver.maxPacketSplits", "4096"));
	public static final int PACKET_RESEND_INTERVAL = Integer.parseInt(System.getProperty("raknetserver.packetResendInterval", "200"));

}
