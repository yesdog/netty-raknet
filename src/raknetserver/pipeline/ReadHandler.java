package raknetserver.pipeline;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.ReferenceCountUtil;

import raknet.RakNet;
import raknet.packet.ClientHandshake;
import raknet.packet.ConnectionRequest;
import raknet.packet.Disconnect;
import raknet.packet.Packet;
import raknet.packet.Ping;
import raknet.packet.Pong;
import raknet.packet.ServerHandshake;
import raknet.packet.PacketData;
import raknetserver.channel.RakNetChildChannel;

public class ReadHandler extends SimpleChannelInboundHandler<Packet> {

    public static final String NAME = "rn-read";

    protected void channelRead0(ChannelHandlerContext ctx, Packet packet) {
        final RakNetChildChannel channel = (RakNetChildChannel) ctx.channel();
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
                channel.config().updateRTT(pongRTT);
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
