package network.ycc.raknet.utils;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;

public class DataSerializer {

    private static final InetSocketAddress NULL_ADDR = new InetSocketAddress(0);

    private static final byte[] MAGIC = new byte[] { (byte) 0x00, (byte) 0xff, (byte) 0xff, (byte) 0x00,
			(byte) 0xfe, (byte) 0xfe, (byte) 0xfe, (byte) 0xfe, (byte) 0xfd, (byte) 0xfd, (byte) 0xfd,
			(byte) 0xfd, (byte) 0x12, (byte) 0x34, (byte) 0x56, (byte) 0x78 };

	public static void writeString(ByteBuf buf, String str) {
		byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
		buf.writeShort(bytes.length);
		buf.writeBytes(bytes);
	}

	public static String readString(ByteBuf buf) {
		byte[] bytes = new byte[buf.readShort()];
		buf.readBytes(bytes);
		return new String(bytes, StandardCharsets.UTF_8);
	}

	public static InetSocketAddress readAddress(ByteBuf buf) {
		final int type = buf.readByte();
		byte[] addr;
		int port;
		if (type == 4) {
			int addri = ~buf.readInt();
			addr = ByteBuffer.allocate(4).putInt(addri).array();
			port = buf.readUnsignedShort();
		} else if (type == 6) {
			//sockaddr_in6 structure
			buf.skipBytes(2); //family
			port = buf.readUnsignedShort();
			buf.skipBytes(4); //flow info
			addr = new byte[16];
			buf.readBytes(addr);
			buf.skipBytes(4); //scope id;
		} else {
			throw new DecoderException("Unknown inet addr version: " + type);
		}
		try {
			return new InetSocketAddress(InetAddress.getByAddress(addr), port);
		} catch (UnknownHostException e) {
			throw new DecoderException("Unexpected error", e);
		}
	}

	public static void writeAddress(ByteBuf buf) {
        writeAddress(buf, NULL_ADDR);
    }

	public static void writeAddress(ByteBuf buf, InetSocketAddress address) {
		final InetAddress addr = address.getAddress();
		if (addr instanceof Inet4Address) {
			buf.writeByte((byte) 4);
			final int addri = ByteBuffer.wrap(addr.getAddress()).getInt();
			buf.writeInt(~addri);
			buf.writeShort(address.getPort());
		} else if (addr instanceof Inet6Address) {
			//socaddr_in6 structure
			buf.writeShort(10); //family AF_INET6
			buf.writeShort(address.getPort());
			buf.writeInt(0); //flow info
			buf.writeBytes(addr.getAddress());
			buf.writeInt(0); //scope id
		} else {
			throw new EncoderException("Unknown inet addr version: " + addr.getClass().getName());
		}
	}

	public static void writeMagic(ByteBuf out) {
		out.writeBytes(MAGIC);
	}

	public static void readMagic(ByteBuf out) {
		for (byte b : MAGIC) {
			if (out.readByte() != b) {
				throw new MagicDecodeException();
			}
		}
	}

	public static class MagicDecodeException extends DecoderException {
		protected MagicDecodeException() {
			super("Incorrect RakNet magic value");
		}
	}

}
