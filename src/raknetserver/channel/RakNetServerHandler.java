package raknetserver.channel;

import io.netty.channel.*;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.ReferenceCountUtil;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Map;

public class RakNetServerHandler extends ChannelDuplexHandler {

    protected Map<SocketAddress, RakNetChildChannel> childMap = new HashMap<>();

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        assert ctx.channel() instanceof RakNetServerChannel;
        super.channelRegistered(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof DatagramPacket) {
            try {
                final DatagramPacket datagram = (DatagramPacket) msg;
                final Channel child = childMap.get(datagram.sender());
                if (child != null && child.isActive() && child.config().isAutoRead()) {
                    child.pipeline()
                            .fireChannelRead(datagram.content().retain())
                            .fireChannelReadComplete();
                } else if (child == null) {
                    ctx.fireChannelRead(ReferenceCountUtil.retain(msg));
                }
            } finally {
                ReferenceCountUtil.release(msg);
            }
        } else {
            ctx.fireChannelRead(msg);
        }
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        final RakNetServerChannel channel = (RakNetServerChannel) ctx.channel();
        if (msg instanceof DatagramPacket) {
            channel.listener.write(msg).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
            promise.trySuccess();
        } else {
            ctx.write(msg, promise);
        }
    }

    @Override
    public void flush(ChannelHandlerContext ctx) {
        ((RakNetServerChannel) ctx.channel()).listener.flush();
        ctx.flush();
    }

    @Override
    public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress,
                        SocketAddress localAddress, ChannelPromise promise) {
        //TODO: session limit check
        final RakNetServerChannel channel = (RakNetServerChannel) ctx.channel();
        try {
            if (localAddress != null && !channel.localAddress.equals(localAddress)) {
                throw new IllegalArgumentException("Bound localAddress does not match provided " + localAddress);
            }
            if (!(remoteAddress instanceof InetSocketAddress)) {
                throw new IllegalArgumentException("Provided remote address is not an InetSocketAddress");
            }
            if (!childMap.containsKey(remoteAddress)) {
                final RakNetChildChannel child = new RakNetChildChannel(channel, (InetSocketAddress) remoteAddress);
                channel.pipeline().fireChannelRead(child).fireChannelReadComplete(); //register
                child.closeFuture().addListener(v ->
                        channel.eventLoop().execute(() -> childMap.remove(remoteAddress, child))
                );
                childMap.put(remoteAddress, child);
            }
            promise.trySuccess();
        } catch (Exception e) {
            promise.tryFailure(e);
        }
    }

}
