package network.ycc.raknet.server.pipeline;

import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.ReferenceCountUtil;

@Sharable
public class DatagramConsumer extends ChannelInboundHandlerAdapter {

    public static final String NAME = "rn-datagram-consumer";
    public static final DatagramConsumer INSTANCE = new DatagramConsumer();

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof DatagramPacket) {
            ReferenceCountUtil.safeRelease(msg);
        } else {
            ctx.fireChannelRead(msg);
        }
    }

}
