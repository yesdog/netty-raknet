package network.ycc.raknet.server.pipeline;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import network.ycc.raknet.packet.ConnectionRequest;
import network.ycc.raknet.packet.ServerHandshake;

import java.net.InetSocketAddress;

@ChannelHandler.Sharable
public class ConnectionRequestHandler extends SimpleChannelInboundHandler<ConnectionRequest> {

    public static final String NAME = "rn-connection-request";
    public static final ConnectionRequestHandler INSTANCE = new ConnectionRequestHandler();

    protected void channelRead0(ChannelHandlerContext ctx, ConnectionRequest request) {
        ctx.writeAndFlush(new ServerHandshake(
                (InetSocketAddress) ctx.channel().remoteAddress(),
                request.getTimeStamp()
        ));
    }

}
