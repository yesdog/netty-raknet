package raknetserver.pipeline;

import java.util.concurrent.TimeUnit;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.ScheduledFuture;
import raknetserver.RakNetServer;
import raknetserver.packet.Ping;
import raknetserver.packet.ConnectionFailed;
import raknetserver.packet.ConnectionReply2;
import raknetserver.packet.ConnectionRequest2;
import raknetserver.packet.Packet;

public class ConnectionHandler extends SimpleChannelInboundHandler<Packet> {

    public static final String NAME = "rn-connect";

    protected long guid;
    protected ScheduledFuture<?> pingTask;
    protected boolean isConnected = false;

    protected void channelRead0(ChannelHandlerContext ctx, Packet packet) {
        if (packet instanceof ConnectionRequest2) {
            handleConnectionRequest2(ctx, (ConnectionRequest2) packet);
        } else if (!isConnected) {
            throw new IllegalStateException("Can't handle packet " + packet + ", connection is not established yet");
        } else {
            ctx.fireChannelRead(ReferenceCountUtil.retain(packet));
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ctx.writeAndFlush(new ConnectionFailed()).addListeners(
                ChannelFutureListener.CLOSE, ChannelFutureListener.CLOSE_ON_FAILURE);
        ctx.fireExceptionCaught(cause);
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
        if (!isConnected) {
            isConnected = true;
            guid = nguid;
            Channel channel = ctx.channel();
            //TODO: verify server-side MTU?
            channel.attr(RakNetServer.MTU).set(connectionRequest2.getMtu());
            ctx.writeAndFlush(new ConnectionReply2(connectionRequest2.getMtu()))
                    .addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
            pingTask = channel.eventLoop().scheduleAtFixedRate(
                    () -> channel.writeAndFlush(new Ping()).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE),
                    100, 250, TimeUnit.MILLISECONDS);
        } else {
            //if guid matches then it means that reply2 packet didn't arrive to the clients
            //otherwise it means that it is actually a new client connecting using already taken ip+port
            if (guid == nguid) {
                ctx.writeAndFlush(new ConnectionReply2(ctx.channel().attr(RakNetServer.MTU).get()))
                        .addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
            } else {
                ctx.writeAndFlush(new ConnectionFailed()).addListeners(
                        ChannelFutureListener.CLOSE, ChannelFutureListener.CLOSE_ON_FAILURE);
            }
        }
    }

}
