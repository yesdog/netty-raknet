package raknetserver.pipeline;

import java.util.concurrent.TimeUnit;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.ScheduledFuture;

import raknet.RakNet;
import raknet.packet.Ping;
import raknet.packet.ConnectionFailed;
import raknet.packet.ConnectionReply2;
import raknet.packet.ConnectionRequest1;
import raknet.packet.ConnectionRequest2;
import raknet.packet.Packet;
import raknetserver.channel.RakNetChildChannel;

public class ConnectionHandler extends SimpleChannelInboundHandler<Packet> {

    public static final String NAME = "rn-connect";

    protected long guid;
    protected ScheduledFuture<?> pingTask;
    protected boolean isConnected = false;

    protected void channelRead0(ChannelHandlerContext ctx, Packet packet) {
        if (packet instanceof ConnectionRequest1) {
            // ignore
        } else if (packet instanceof ConnectionRequest2) {
            handleConnectionRequest2(ctx, (ConnectionRequest2) packet);
        } else if (!isConnected) {
            throw new IllegalStateException("Can't handle packet " + packet + ", connection is not established yet");
        } else {
            ctx.fireChannelRead(ReferenceCountUtil.retain(packet));
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (pingTask != null) {
            pingTask.cancel(true);
        }
        super.channelInactive(ctx);
    }

    @SuppressWarnings("unchecked")
    protected void handleConnectionRequest2(ChannelHandlerContext ctx, ConnectionRequest2 connectionRequest2) {
        final long nguid = connectionRequest2.getGUID();
        final RakNetChildChannel channel = (RakNetChildChannel) ctx.channel();
        final long serverId = channel.config().getServerId();
        if (!isConnected) {
            isConnected = true;
            guid = nguid;
            //TODO: verify server-side MTU?
            ctx.channel().config().setOption(RakNet.MTU, connectionRequest2.getMtu());
            ctx.writeAndFlush(new ConnectionReply2(connectionRequest2.getMtu(), serverId))
                    .addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
            pingTask = channel.eventLoop().scheduleAtFixedRate(
                    () -> channel.writeAndFlush(new Ping()).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE),
                    100, 250, TimeUnit.MILLISECONDS
            );
        } else {
            //if guid matches then it means that reply2 packet didn't arrive to the clients
            //otherwise it means that it is actually a new client connecting using already taken ip+port
            if (guid == nguid) {
                final int mtu = ctx.channel().config().getOption(RakNet.MTU);
                ctx.writeAndFlush(new ConnectionReply2(mtu, serverId))
                        .addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
            } else {
                ctx.writeAndFlush(new ConnectionFailed()).addListeners(
                        ChannelFutureListener.CLOSE, ChannelFutureListener.CLOSE_ON_FAILURE);
            }
        }
    }

}
