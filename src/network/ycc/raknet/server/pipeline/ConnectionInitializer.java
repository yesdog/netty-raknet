package network.ycc.raknet.server.pipeline;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;

import io.netty.util.ReferenceCountUtil;

import network.ycc.raknet.RakNet;
import network.ycc.raknet.packet.ConnectionReply1;
import network.ycc.raknet.packet.ConnectionRequest1;
import network.ycc.raknet.packet.InvalidVersion;
import network.ycc.raknet.packet.Packet;

import java.net.InetSocketAddress;

public class ConnectionInitializer extends UdpPacketHandler<ConnectionRequest1> {

    public static final String NAME = "rn-connect-init";

    public ConnectionInitializer() {
        super(ConnectionRequest1.class);
    }

    protected void handle(ChannelHandlerContext ctx, InetSocketAddress sender, ConnectionRequest1 request) {
        final long serverId = ctx.channel().config().getOption(RakNet.SERVER_ID);
        final Packet response;
        //TODO: version config
        if (request.getRakNetProtocolVersion() == InvalidVersion.VALID_VERSION) {
            //use connect to create a new child for this address
            ctx.connect(sender).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
            response = new ConnectionReply1(request.getMtu(), serverId);
        } else {
            response = new InvalidVersion(serverId);
        }
        ctx.writeAndFlush(new DatagramPacket(response.createData(ctx.alloc()), sender))
                .addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
        ReferenceCountUtil.release(response);
    }

}
