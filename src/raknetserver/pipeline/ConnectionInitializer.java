package raknetserver.pipeline;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;

import io.netty.util.ReferenceCountUtil;

import raknetserver.packet.ConnectionReply1;
import raknetserver.packet.ConnectionRequest1;
import raknetserver.packet.InvalidVersion;
import raknetserver.packet.Packet;
import raknetserver.packet.Packets;

public class ConnectionInitializer extends SimpleChannelInboundHandler<DatagramPacket> {

    public static final String NAME = "rn-connect-init";

    @Override
    public boolean acceptInboundMessage(Object msg) throws Exception {
        if (!super.acceptInboundMessage(msg)) {
            return false;
        }
        final ByteBuf content = ((DatagramPacket) msg).content();
        return content.getUnsignedByte(content.readerIndex()) == Packets.OPEN_CONNECTION_REQUEST_1;
    }

    public void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) {
        final ConnectionRequest1 request = (ConnectionRequest1) Packets.decodeRaw(msg.content());
        try {
            final Packet response;
            if (request.getRakNetProtocolVersion() == InvalidVersion.VALID_VERSION) {
                //use connect to create a new child for this address
                ctx.connect(msg.sender()).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
                response = new ConnectionReply1(request.getMtu());
            } else {
                response = new InvalidVersion();
            }
            ctx.writeAndFlush(new DatagramPacket(response.createData(ctx.alloc()), msg.sender()))
                    .addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
            ReferenceCountUtil.release(response);
        } finally {
            ReferenceCountUtil.release(request);
        }
    }

}
