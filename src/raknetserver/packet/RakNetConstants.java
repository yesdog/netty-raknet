package raknetserver.packet;

import java.net.InetSocketAddress;

import io.netty.util.AttributeKey;

public final class RakNetConstants {

	public static final byte[] MAGIC = new byte[] { (byte) 0x00, (byte) 0xff, (byte) 0xff, (byte) 0x00, (byte) 0xfe, (byte) 0xfe, (byte) 0xfe, (byte) 0xfe, (byte) 0xfd, (byte) 0xfd, (byte) 0xfd, (byte) 0xfd, (byte) 0x12, (byte) 0x34, (byte) 0x56, (byte) 0x78 };
	public static final long SERVER_ID = 0x0000000012345678L;
	public static final InetSocketAddress NULL_ADDR = new InetSocketAddress(0);

	public static final int ID_RN_UNCONNECTED_PING = 0x01;
	public static final int ID_RN_UNCONNECTED_PONG = 0x1C;

	public static final int ID_RN_OPEN_CONNECTION_REQUEST_1 = 0x05;
	public static final int ID_RN_OPEN_CONNECTION_REPLY_1 = 0x06;
	public static final int ID_RN_INVALID_VERSION = 25;
	public static final int ID_RN_ALREADY_CONNECTED = 18;
	public static final int ID_RN_OPEN_CONNECTION_REQUEST_2 = 0x07;
	public static final int ID_RN_OPEN_CONNECTION_REPLY_2 = 0x08;
	public static final int ID_RN_ACK = 0xC0;
	public static final int ID_RN_NACK = 0xA0;

	public static final int ID_I_PING = 0x00;
	public static final int ID_I_PONG = 0x03;

	public static final int ID_I_CONNECTION_REQUEST = 0x09;
	public static final int ID_I_SERVER_HANDSHAKE = 0x10;
	public static final int ID_I_CLIENT_HANDSHAKE = 0x13;
	public static final int ID_I_CLIENT_DISCONNECT = 0x15;

	public static final AttributeKey<Integer> MTU = AttributeKey.valueOf("MTU");

}
