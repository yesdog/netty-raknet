package raknetserver.pipeline;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.ReferenceCountUtil;

import raknetserver.RakNetServer;
import raknetserver.packet.ClientHandshake;
import raknetserver.packet.ConnectionRequest;
import raknetserver.packet.Disconnect;
import raknetserver.packet.Packet;
import raknetserver.packet.Ping;
import raknetserver.packet.Pong;
import raknetserver.packet.ServerHandshake;
import raknetserver.packet.PacketData;
import raknetserver.udp.UdpChildChannel;

public class ReadHandler extends SimpleChannelInboundHandler<Packet> {

    public static final String NAME = "rn-read";

    protected static final int RTT_WEIGHT = 8;
    protected static final long DEFAULT_RTT_MS = 400;

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        super.handlerAdded(ctx);
        ctx.channel().attr(RakNetServer.RTT).set(TimeUnit.NANOSECONDS.convert(DEFAULT_RTT_MS, TimeUnit.MILLISECONDS));
    }

    protected void channelRead0(ChannelHandlerContext ctx, Packet packet) {
        final UdpChildChannel channel = (UdpChildChannel) ctx.channel();
        if (packet instanceof PacketData) {
            final PacketData data = (PacketData) packet;
            final int userDataId = channel.config().getUserDataId();
            assert !data.isFragment();
            if (userDataId != -1 && userDataId == data.getPacketId()) {
                ctx.fireChannelRead(data.createData().skipBytes(1));
            } else {
                ctx.fireChannelRead(data.retain());
            }
        } else if (packet instanceof Ping) {
            final Ping ping = (Ping) packet;
            ctx.writeAndFlush(new Pong(ping.getTimestamp(), ping.getReliability()));
        } else if (packet instanceof Pong) {
            final Pong pong = (Pong) packet;
            if (!pong.getReliability().isReliable) {
                final long pongRTT = TimeUnit.NANOSECONDS.convert(pong.getRTT(), TimeUnit.MILLISECONDS);
                final long oldRTT = ctx.channel().attr(RakNetServer.RTT).get();
                final long newRTT = (oldRTT * (RTT_WEIGHT - 1) + pongRTT) / RTT_WEIGHT;
                ctx.channel().attr(RakNetServer.RTT).set(newRTT);
                channel.config().getMetrics().measureRTTns(newRTT);
            }
        } else if (packet instanceof ConnectionRequest) {
            ctx.writeAndFlush(new ServerHandshake(
                    (InetSocketAddress) ctx.channel().remoteAddress(),
                    ((ConnectionRequest) packet).getTimeStamp()
            ));
        } else if (packet instanceof ClientHandshake) {

        } else if (packet instanceof Disconnect) {
            ctx.channel().close();
        } else {
            ctx.fireChannelRead(ReferenceCountUtil.retain(packet));
        }
    }

}
