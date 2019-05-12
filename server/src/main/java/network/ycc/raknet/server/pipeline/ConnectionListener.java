package network.ycc.raknet.server.pipeline;

import io.netty.buffer.ByteBuf;
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

public class ConnectionListener extends UdpPacketHandler<ConnectionRequest1> {

    public static final String NAME = "rn-connect-init";

    public ConnectionListener() {
        super(ConnectionRequest1.class);
    }

    protected void handle(ChannelHandlerContext ctx, InetSocketAddress sender, ConnectionRequest1 request) {
        final RakNet.Config config = (RakNet.Config) ctx.channel().config();
        final Packet response;
        if (request.getProtocolVersion() == config.getProtocolVersion()) {
            //use connect to create a new child for this address
            ctx.connect(sender).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
            response = new ConnectionReply1(config.getMagic(), request.getMtu(), config.getServerId());
        } else {
            response = new InvalidVersion(config.getServerId());
        }
        final ByteBuf buf = ctx.alloc().ioBuffer(response.sizeHint());
        try {
            config.getCodec().encode(response, buf);
            ctx.writeAndFlush(new DatagramPacket(buf.retain(), sender))
                    .addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
        } finally {
            ReferenceCountUtil.safeRelease(response);
            buf.release();
        }
    }

}
