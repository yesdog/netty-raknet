package raknetserver.pipeline;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;

import io.netty.util.ReferenceCountUtil;
import raknetserver.RakNetServer;
import raknetserver.packet.ConnectionReply1;
import raknetserver.packet.ConnectionRequest1;
import raknetserver.packet.InvalidVersion;
import raknetserver.packet.Packet;
import raknetserver.packet.Packets;
import raknetserver.packet.UnconnectedPing;
import raknetserver.packet.UnconnectedPong;

import java.util.function.Supplier;

public class PreConnectionHandler extends SimpleChannelInboundHandler<DatagramPacket> {

    public static final String NAME = "rn-pre-connect";

    @Override
    public boolean acceptInboundMessage(Object msg) throws Exception {
        if (!super.acceptInboundMessage(msg)) {
            return false;
        }
        final ByteBuf content = ((DatagramPacket) msg).content();
        switch (content.getUnsignedByte(content.readerIndex())) {
            case Packets.OPEN_CONNECTION_REQUEST_1:
            case Packets.UNCONNECTED_PING:
                return true;
            default:
                return false;
        }
    }

    public void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) {
        final Packet packet = Packets.decodeRaw(msg.content());
        try {
            final Packet response;
            if (packet instanceof ConnectionRequest1) {
                final ConnectionRequest1 connectionRequest1 = (ConnectionRequest1) packet;
                if (connectionRequest1.getRakNetProtocolVersion() == InvalidVersion.VALID_VERSION) {
                    //use connect to create a new child for this address
                    ctx.connect(msg.sender()).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
                    response = new ConnectionReply1(connectionRequest1.getMtu());
                } else {
                    response = new InvalidVersion();
                }
            } else if (packet instanceof UnconnectedPing) {
                final UnconnectedPing ping = (UnconnectedPing) packet;
                final Supplier<String> pingSupplier = ctx.channel().attr(RakNetServer.PING_SUPPLIER).get();
                response = new UnconnectedPong(ping.getClientTime(), pingSupplier == null ? "" : pingSupplier.get());
            } else {
                throw new IllegalArgumentException("PreConnectionHandler handled an unknown packet: " + packet);
            }
            ctx.writeAndFlush(new DatagramPacket(response.createData(ctx.alloc()), msg.sender()))
                    .addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
            ReferenceCountUtil.release(response);
        } finally {
            ReferenceCountUtil.release(packet);
        }
    }

}
