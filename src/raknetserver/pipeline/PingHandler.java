package raknetserver.pipeline;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;

import io.netty.util.ReferenceCountUtil;
import raknetserver.packet.Packet;
import raknetserver.packet.Packets;
import raknetserver.packet.UnconnectedPing;
import raknetserver.packet.UnconnectedPong;

import java.net.InetSocketAddress;
import java.util.function.Consumer;

public abstract class PingHandler extends SimpleChannelInboundHandler<DatagramPacket> {

    abstract protected void handlePing(InetSocketAddress remote, Consumer<String> respond);

    @Override
    public boolean acceptInboundMessage(Object msg) throws Exception {
        if (!super.acceptInboundMessage(msg)) {
            return false;
        }
        final ByteBuf content = ((DatagramPacket) msg).content();
        return content.getUnsignedByte(content.readerIndex()) == Packets.UNCONNECTED_PING;
    }

    public void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) {
        final UnconnectedPing ping = (UnconnectedPing) Packets.decodeRaw(msg.content());
        final InetSocketAddress sender = msg.sender();
        final long clientTime = ping.getClientTime();
        try {
            handlePing(sender, response -> {
                final Packet pong = new UnconnectedPong(clientTime, response);
                try {
                    ctx.writeAndFlush(new DatagramPacket(pong.createData(ctx.alloc()), sender))
                            .addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
                } finally {
                    ReferenceCountUtil.release(pong);
                }
            });
        } finally {
            ReferenceCountUtil.release(ping);
        }
    }

}
