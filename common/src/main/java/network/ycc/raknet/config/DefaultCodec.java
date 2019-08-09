package network.ycc.raknet.config;

import network.ycc.raknet.RakNet;
import network.ycc.raknet.frame.FrameData;
import network.ycc.raknet.packet.AlreadyConnected;
import network.ycc.raknet.packet.ClientHandshake;
import network.ycc.raknet.packet.ConnectionBanned;
import network.ycc.raknet.packet.ConnectionFailed;
import network.ycc.raknet.packet.ConnectionReply1;
import network.ycc.raknet.packet.ConnectionReply2;
import network.ycc.raknet.packet.ConnectionRequest;
import network.ycc.raknet.packet.ConnectionRequest1;
import network.ycc.raknet.packet.ConnectionRequest2;
import network.ycc.raknet.packet.Disconnect;
import network.ycc.raknet.packet.FrameSet;
import network.ycc.raknet.packet.FramedPacket;
import network.ycc.raknet.packet.InvalidVersion;
import network.ycc.raknet.packet.NoFreeConnections;
import network.ycc.raknet.packet.Packet;
import network.ycc.raknet.packet.Ping;
import network.ycc.raknet.packet.Pong;
import network.ycc.raknet.packet.Reliability;
import network.ycc.raknet.packet.ServerHandshake;
import network.ycc.raknet.packet.SimplePacket;
import network.ycc.raknet.packet.UnconnectedPing;
import network.ycc.raknet.packet.UnconnectedPong;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

public class DefaultCodec implements RakNet.Codec {

    public static final DefaultCodec INSTANCE = new DefaultCodec();

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
    public static final int ALREADY_CONNECTED = 0x12;
    public static final int CLIENT_HANDSHAKE = 0x13;
    public static final int NO_FREE_CONNECTIONS = 0x14;
    public static final int CLIENT_DISCONNECT = 0x15;
    public static final int CONNECTION_BANNED = 0x17;
    public static final int INVALID_VERSION = 0x19;
    public static final int UNCONNECTED_PONG = 0x1C;
    public static final int FRAME_DATA_START = 0x80;
    public static final int FRAME_DATA_END = 0x8F;
    public static final int NACK = 0xA0;
    public static final int ACK = 0xC0;

    protected final Int2ObjectOpenHashMap<Function<ByteBuf, ? extends Packet>> decoders = new Int2ObjectOpenHashMap<>();
    protected final Int2ObjectOpenHashMap<BiConsumer<? extends Packet, ByteBuf>> encoders = new Int2ObjectOpenHashMap<>();
    protected final Object2IntOpenHashMap<Class<?>> idFromClass = new Object2IntOpenHashMap<>();
    protected final IntOpenHashSet framedPacketIds = new IntOpenHashSet();

    public DefaultCodec() {
        //       ID                         Class                       Decoder                     Encoder
        register(PING,                      Ping.class,                 Ping::new);
        register(UNCONNECTED_PING,          UnconnectedPing.class,      UnconnectedPing::new);
        register(PONG,                      Pong.class,                 Pong::new);
        register(OPEN_CONNECTION_REQUEST_1, ConnectionRequest1.class,   ConnectionRequest1::new);
        register(OPEN_CONNECTION_REPLY_1,   ConnectionReply1.class,     ConnectionReply1::new);
        register(OPEN_CONNECTION_REQUEST_2, ConnectionRequest2.class,   ConnectionRequest2::new);
        register(OPEN_CONNECTION_REPLY_2,   ConnectionReply2.class,     ConnectionReply2::new);
        register(CONNECTION_REQUEST,        ConnectionRequest.class,    ConnectionRequest::new);
        register(SERVER_HANDSHAKE,          ServerHandshake.class,      ServerHandshake::new);
        register(CONNECTION_FAILED,         ConnectionFailed.class,     ConnectionFailed::new);
        register(ALREADY_CONNECTED,         AlreadyConnected.class,     AlreadyConnected::new);
        register(CLIENT_HANDSHAKE,          ClientHandshake.class,      ClientHandshake::new);
        register(NO_FREE_CONNECTIONS,       NoFreeConnections.class,    NoFreeConnections::new);
        register(CLIENT_DISCONNECT,         Disconnect.class,           Disconnect::new);
        register(CONNECTION_BANNED,         ConnectionBanned.class,     ConnectionBanned::new);
        register(INVALID_VERSION,           InvalidVersion.class,       InvalidVersion::new);
        register(UNCONNECTED_PONG,          UnconnectedPong.class,      UnconnectedPong::new);
        for (int i = FRAME_DATA_START; i <= FRAME_DATA_END; i++) register(
                 i,                         FrameSet.class,             FrameSet::read,             FrameSet::write);
        register(NACK,                      Reliability.NACK.class,     Reliability.NACK::new);
        register(ACK,                       Reliability.ACK.class,      Reliability.ACK::new);

        idFromClass.defaultReturnValue(-1);
    }

    public FrameData encode(FramedPacket packet, ByteBufAllocator alloc) {
        if (packet instanceof FrameData) {
            return ((FrameData) packet).retain();
        }
        final ByteBuf out = alloc.ioBuffer(packet.sizeHint());
        try {
            encode(packet, out);
            final FrameData frameData = FrameData.read(out, out.readableBytes(), false);
            frameData.setReliability(packet.getReliability());
            frameData.setOrderChannel(packet.getOrderChannel());
            return frameData;
        } finally {
            out.release();
        }
    }

    @SuppressWarnings("unchecked")
    public void encode(Packet packet, ByteBuf out) {
        if (!idFromClass.containsKey(packet.getClass())) {
            throw new IllegalArgumentException("Unknown encoder for " + packet.getClass());
        }
        final int packetId = packetIdFor(packet.getClass());
        final BiConsumer<Packet, ByteBuf> encoder = (BiConsumer<Packet, ByteBuf>) encoders
                .get(packetId);
        encoder.accept(packet, out);
    }

    public ByteBuf produceEncoded(Packet packet, ByteBufAllocator alloc) {
        if (packet instanceof FrameSet && ((FrameSet) packet).getRoughSize() >= 128) {
            return ((FrameSet) packet).produce(alloc);
        }
        final ByteBuf buf = alloc.ioBuffer(packet.sizeHint());
        try {
            encode(packet, buf);
            return buf.retain();
        } finally {
            buf.release();
        }
    }

    public Packet decode(ByteBuf buf) {
        final int packetId = buf.getUnsignedByte(buf.readerIndex());
        final Function<ByteBuf, ? extends Packet> decoder = decoders.get(packetId);
        if (decoder == null) {
            throw new IllegalArgumentException("Unknown decoder for packet ID " + packetId);
        }
        return decoder.apply(buf);
    }

    public FramedPacket decode(FrameData data) {
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

    public int packetIdFor(Class<? extends Packet> type) {
        return idFromClass.getInt(type);
    }

    ///// REGISTRY /////

    protected <T extends SimplePacket> void register(int id, Class<T> clz, Supplier<T> cons) {
        register(id, clz, decodeSimple(cons), encodeSimple(id));
    }

    protected <T extends Packet> void register(int id, Class<? extends Packet> clz,
            Function<ByteBuf, T> decoder, BiConsumer<T, ByteBuf> encoder) {
        idFromClass.put(clz, id);
        decoders.put(id, decoder);
        encoders.put(id, encoder);
        if (FramedPacket.class.isAssignableFrom(clz)) {
            framedPacketIds.add(id);
        }
    }

    protected <T extends SimplePacket> Function<ByteBuf, T> decodeSimple(Supplier<T> cons) {
        return buf -> {
            final T inst = cons.get();
            buf.skipBytes(1);
            inst.decode(buf);
            return inst;
        };
    }

    protected <T extends SimplePacket> BiConsumer<T, ByteBuf> encodeSimple(int id) {
        return (packet, buf) -> {
            buf.writeByte(id);
            packet.encode(buf);
        };
    }

}
