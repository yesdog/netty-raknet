package network.ycc.raknet.server.pipeline;

import network.ycc.raknet.RakNet;
import network.ycc.raknet.packet.ConnectionReply1;
import network.ycc.raknet.packet.ConnectionRequest1;
import network.ycc.raknet.packet.InvalidVersion;
import network.ycc.raknet.packet.Packet;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.ReferenceCountUtil;

import java.net.InetSocketAddress;

public class ConnectionListener extends UdpPacketHandler<ConnectionRequest1> {

    public static final String NAME = "rn-connect-init";

    public ConnectionListener() {
        super(ConnectionRequest1.class);
    }

    @SuppressWarnings("unchecked")
    protected void handle(ChannelHandlerContext ctx, InetSocketAddress sender,
            ConnectionRequest1 request) {
        final RakNet.Config config = RakNet.config(ctx);
        final Packet response;
        if (request.getProtocolVersion() == config.getProtocolVersion()) {
            response = new ConnectionReply1(config.getMagic(), request.getMtu(), config.getServerId());
            ReferenceCountUtil.retain(request);
            //use connect to create a new child for this remote address
            ctx.connect(sender).addListeners(
                    ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE,
                    future -> {
                        if (future.isSuccess()) {
                            resendRequest(ctx, sender, request);
                        } else {
                            ReferenceCountUtil.safeRelease(request);
                        }
                    }
            );
        } else {
            response = new InvalidVersion(config.getMagic(), config.getServerId());
        }
        sendResponse(ctx, sender, response);
    }

    protected void sendResponse(ChannelHandlerContext ctx, InetSocketAddress sender, Packet packet) {
        final RakNet.Config config = RakNet.config(ctx);
        final ByteBuf buf = ctx.alloc().ioBuffer(packet.sizeHint());
        try {
            config.getCodec().encode(packet, buf);
            ctx.writeAndFlush(new DatagramPacket(buf.retain(), sender))
                    .addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
        } finally {
            ReferenceCountUtil.safeRelease(packet);
            buf.release();
        }
    }

    protected void resendRequest(ChannelHandlerContext ctx, InetSocketAddress sender, ConnectionRequest1 request) {
        final RakNet.Config config = RakNet.config(ctx);
        final ByteBuf buf = ctx.alloc().ioBuffer(request.sizeHint());
        try {
            config.getCodec().encode(request, buf);
            ctx.fireChannelRead(new DatagramPacket(buf.retain(), sender));
        } finally {
            ReferenceCountUtil.safeRelease(request);
            buf.release();
        }
    }

}
