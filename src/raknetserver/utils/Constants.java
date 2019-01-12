package raknetserver.utils;

public class Constants {

	public static final int MAX_PACKET_LOSS = Integer.parseInt(System.getProperty("raknetserver.maxPacketLoss", "8192"));
	public static final int MAX_PACKET_SPLITS = Integer.parseInt(System.getProperty("raknetserver.maxPacketSplits", "4096"));
	public static final int UDP_IO_THREADS = Integer.parseInt(System.getProperty("raknetserver.udpIOThreads", "4"));

}
