package raknetserver.packet;

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

public class RakNetDataSerializer {

	public static void writeString(ByteBuf buf, String str) {
		byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
		buf.writeShort(bytes.length);
		buf.writeBytes(bytes);
	}

	public static InetSocketAddress readAddress(ByteBuf buf) {
		byte[] addr = null;
		int port = -1;
		int type = buf.readByte();
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

	public static void writeAddress(ByteBuf buf, InetSocketAddress address) {
		InetAddress addr = address.getAddress();
		if (addr instanceof Inet4Address) {
			buf.writeByte((byte) 4);
			int addri = ByteBuffer.wrap(addr.getAddress()).getInt();
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

}
