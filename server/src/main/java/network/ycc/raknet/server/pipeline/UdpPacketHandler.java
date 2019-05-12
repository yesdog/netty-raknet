package network.ycc.raknet.server.pipeline;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.ReferenceCountUtil;

import network.ycc.raknet.RakNet;
import network.ycc.raknet.packet.FramedPacket;
import network.ycc.raknet.packet.Packet;

import java.net.InetSocketAddress;

public abstract class UdpPacketHandler<T extends Packet> extends SimpleChannelInboundHandler<DatagramPacket> {

    public final Class<T> type;
    public int packetId;

    public UdpPacketHandler(Class<T> type) {
        if (FramedPacket.class.isAssignableFrom(type)) {
            throw new IllegalArgumentException("Framed packet types cannot be directly handled by UdpPacketHandler");
        }
        this.type = type;
    }

    abstract protected void handle(ChannelHandlerContext ctx, InetSocketAddress sender, T packet);

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        final RakNet.Config config = (RakNet.Config) ctx.channel().config();
        packetId = config.getCodec().packetIdFor(type);
        if (packetId == -1) {
            throw new IllegalArgumentException("Unknown packet ID for class " + type);
        }
    }

    @Override
    public boolean acceptInboundMessage(Object msg) {
        if (msg instanceof DatagramPacket) {
            final ByteBuf content = ((DatagramPacket) msg).content();
            return content.getUnsignedByte(content.readerIndex()) == packetId;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) {
        final RakNet.Config config = (RakNet.Config) ctx.channel().config();
        final T packet = (T) config.getCodec().decode(msg.content());
        try {
            handle(ctx, msg.sender(), packet);
        } finally {
            ReferenceCountUtil.release(packet);
        }
    }

}
