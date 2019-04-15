package network.ycc.raknet.server.pipeline;

import java.util.concurrent.TimeUnit;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.ScheduledFuture;

import network.ycc.raknet.RakNet;
import network.ycc.raknet.packet.Ping;
import network.ycc.raknet.packet.ConnectionFailed;
import network.ycc.raknet.packet.ConnectionReply2;
import network.ycc.raknet.packet.ConnectionRequest1;
import network.ycc.raknet.packet.ConnectionRequest2;
import network.ycc.raknet.packet.Packet;

//TODO: redo this all as a single handler that self-removes and uses a connection promise for isActive
public class ConnectionHandler extends SimpleChannelInboundHandler<Packet> {

    public static final String NAME = "rn-connect";

    protected boolean isConnected = false; //TODO: attribute + channelActive

    protected void channelRead0(ChannelHandlerContext ctx, Packet packet) {
        if (packet instanceof ConnectionRequest1) {
            // ignore
        } else if (packet instanceof ConnectionRequest2) {
            handleConnectionRequest2(ctx, (ConnectionRequest2) packet);
        } else if (!isConnected) {
            throw new IllegalStateException("Can't handle packet " + packet + ", connection is not established yet");
        } else {
            ctx.fireChannelRead(ReferenceCountUtil.retain(packet));
            ctx.pipeline().remove(this); //done with the handler now
        }
    }

    @SuppressWarnings("unchecked")
    protected void handleConnectionRequest2(ChannelHandlerContext ctx, ConnectionRequest2 connectionRequest2) {
        final long nguid = connectionRequest2.getGUID();
        final Channel channel = ctx.channel();
        final RakNet.Config config = (RakNet.Config) channel.config();
        if (!isConnected) {
            isConnected = true;
            config.setClientId(nguid);
            //TODO: verify server-side MTU?
            config.setMTU(connectionRequest2.getMtu());
            ctx.writeAndFlush(new ConnectionReply2(connectionRequest2.getMtu(), config.getServerId()))
                    .addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
            final ScheduledFuture<?> pingTask = channel.eventLoop().scheduleAtFixedRate(
                    () -> channel.writeAndFlush(new Ping()).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE),
                    100, 250, TimeUnit.MILLISECONDS
            );
            channel.closeFuture().addListener(x -> pingTask.cancel(false));
        } else {
            //if guid matches then it means that reply2 packet didn't arrive to the clients
            //otherwise it means that it is actually a new client connecting using already taken ip+port
            if (config.getClientId() == nguid) {
                ctx.writeAndFlush(new ConnectionReply2(config.getMTU(), config.getServerId()))
                        .addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
            } else {
                ctx.writeAndFlush(new ConnectionFailed()).addListeners(
                        ChannelFutureListener.CLOSE, ChannelFutureListener.CLOSE_ON_FAILURE);
            }
        }
    }

}
