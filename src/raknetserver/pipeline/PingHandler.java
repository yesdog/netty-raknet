package raknetserver.pipeline;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.ReferenceCountUtil;

import raknetserver.packet.Packet;
import raknetserver.packet.UnconnectedPing;
import raknetserver.packet.UnconnectedPong;
import raknetserver.udp.UdpServerChannel;

import java.net.InetSocketAddress;
import java.util.function.Consumer;

public abstract class PingHandler extends UdpPacketHandler<UnconnectedPing> {

    public PingHandler() {
        super(UnconnectedPing.class);
    }

    abstract protected void handlePing(InetSocketAddress remote, long serverId, Consumer<String> respond);

    protected void handle(ChannelHandlerContext ctx, InetSocketAddress sender, UnconnectedPing ping) {
        final long clientTime = ping.getClientTime(); //must ditch references to ping
        final UdpServerChannel channel = (UdpServerChannel) ctx.channel();
        final long serverId = channel.config().getServerId();
        handlePing(sender, serverId, response -> {
            final Packet pong = new UnconnectedPong(clientTime, serverId, response);
            try {
                ctx.writeAndFlush(new DatagramPacket(pong.createData(ctx.alloc()), sender))
                        .addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
            } finally {
                ReferenceCountUtil.release(pong);
            }
        });
    }

}
