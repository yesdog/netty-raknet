package raknetserver.packet;

import io.netty.buffer.ByteBuf;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import java.util.function.Function;
import java.util.function.Supplier;

public final class Packets
{

    public static final int PING = 0x00;
    public static final int UNCONNECTED_PING = 0x01;
    public static final int PONG = 0x03;
    public static final int OPEN_CONNECTION_REQUEST_1 = 0x05;
    public static final int OPEN_CONNECTION_REPLY_1 = 0x06;
    public static final int OPEN_CONNECTION_REQUEST_2 = 0x07;
    public static final int OPEN_CONNECTION_REPLY_2 = 0x08;
    public static final int CONNECTION_REQUEST = 0x09;
    public static final int SND_RECEIPT_ACKED = 0x0E;
    public static final int SND_RECEIPT_LOSS = 0x0F;
    public static final int SERVER_HANDSHAKE = 0x10;
    public static final int CONNECTION_FAILED = 0x11;
    public static final int CLIENT_HANDSHAKE = 0x13;
    public static final int CLIENT_DISCONNECT = 0x15;
    public static final int INVALID_VERSION = 0x19;
    public static final int UNCONNECTED_PONG = 0x1C;
    public static final int FRAME_DATA_START = 0x80;
    public static final int FRAME_DATA_END = 0x8F;
    public static final int NACK = 0xA0;
    public static final int ACK = 0xC0;

    private static final Int2ObjectOpenHashMap<Function<ByteBuf, ? extends Packet>> decoders = new Int2ObjectOpenHashMap<>();
    private static final Object2IntOpenHashMap<Class<?>> idFromClass = new Object2IntOpenHashMap<>();
    private static final IntOpenHashSet framedPacketIds = new IntOpenHashSet();

    public static Packet decodeRaw(ByteBuf buf) {
        final Function<ByteBuf, ? extends Packet> decoder = decoders.get(buf.getUnsignedByte(buf.readerIndex()));
        if (decoder == null) {
            return PacketData.read(buf, buf.readableBytes(), false);
        }
        return decoder.apply(buf);
    }

    public static FramedPacket decodeFramed(PacketData data) {
        final int packetId = data.getPacketId();
        final Function<ByteBuf, ? extends Packet> decoder = decoders.get(packetId);
        if (decoder == null || !framedPacketIds.contains(packetId)) {
            return data.retain();
        }
        final ByteBuf buf = data.createData();
        try {
            final FramedPacket out = (FramedPacket) decoder.apply(buf);
            out.setReliability(data.getReliability());
            out.setOrderChannel(data.getOrderChannel());
            return out;
        } finally {
            buf.release();
        }
    }

    public static int packetIdFor(Class<?> clz) {
        return idFromClass.getInt(clz);
    }

    private static <T extends Packet> void register(int id, Class<?> clz, Function<ByteBuf, T> decoder) {
        assert !decoders.containsKey(id);
        decoders.put(id, decoder);
        register(id, clz);
    }

    private static void register(int id, Class<?> clz) {
        idFromClass.put(clz, id);
        if (FramedPacket.class.isAssignableFrom(clz)) {
            framedPacketIds.add(id);
        }
    }

    private static <T extends SimplePacket> void register(int id, Class<?> clz, Supplier<T> cons) {
        register(id, clz, decodeSimple(cons));
    }

    private static <T extends SimplePacket> Function<ByteBuf, T> decodeSimple(Supplier<T> cons) {
        return x -> {
            final T inst = cons.get();
            x.skipBytes(1);
            inst.decode(x);
            return inst;
        };
    }

    static {
        register(PING,                      Ping.class,                 Ping::new);
        register(UNCONNECTED_PING,          UnconnectedPing.class,      UnconnectedPing::new);
        register(PONG,                      Pong.class,                 decodeSimple(Pong::new));
        register(OPEN_CONNECTION_REQUEST_1, ConnectionRequest1.class,   ConnectionRequest1::new);
        register(OPEN_CONNECTION_REPLY_1,   ConnectionReply1.class);
        register(OPEN_CONNECTION_REQUEST_2, ConnectionRequest2.class,   ConnectionRequest2::new);
        register(OPEN_CONNECTION_REPLY_2,   ConnectionReply2.class);
        register(CONNECTION_REQUEST,        ConnectionRequest.class,    ConnectionRequest::new);
        register(SERVER_HANDSHAKE,          ServerHandshake.class,      ServerHandshake::new);
        register(CONNECTION_FAILED,         ConnectionFailed.class,     ConnectionFailed::new);
        register(CLIENT_HANDSHAKE,          ClientHandshake.class,      ClientHandshake::new);
        register(CLIENT_DISCONNECT,         Disconnect.class,           Disconnect::new);
        register(INVALID_VERSION,           InvalidVersion.class,       InvalidVersion::new);
        register(UNCONNECTED_PONG,          UnconnectedPong.class);
        for (int i = FRAME_DATA_START ; i <= FRAME_DATA_END ; i++) {
            register(i,                     FrameSet.class,           FrameSet::read);
        }
        register(NACK,                      Reliability.NACK.class,     decodeSimple(Reliability.NACK::new));
        register(ACK,                       Reliability.ACK.class,      decodeSimple(Reliability.ACK::new));
    }

}
