package raknetserver.udp;

import io.netty.channel.*;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.ReferenceCountUtil;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Map;

public class UdpChildHandler extends ChannelDuplexHandler {

    protected Map<SocketAddress, UdpChildChannel> childMap = new HashMap<>();

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        assert ctx.channel() instanceof UdpServerChannel;
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
        final UdpServerChannel channel = (UdpServerChannel) ctx.channel();
        if (msg instanceof DatagramPacket) {
            channel.listener.write(msg).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
        } else {
            ctx.write(msg, promise);
        }
    }

    @Override
    public void flush(ChannelHandlerContext ctx) {
        ((UdpServerChannel) ctx.channel()).listener.flush();
        ctx.flush();
    }

    @Override
    public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress,
                        SocketAddress localAddress, ChannelPromise promise) {
        //TODO: session limit check
        final UdpServerChannel channel = (UdpServerChannel) ctx.channel();
        try {
            if (localAddress != null && !channel.localAddress.equals(localAddress)) {
                throw new IllegalArgumentException("Bound localAddress does not match provided " + localAddress);
            }
            if (!(remoteAddress instanceof InetSocketAddress)) {
                throw new IllegalArgumentException("Provided remote address is not an InetSocketAddress");
            }
            if (!childMap.containsKey(remoteAddress)) {
                final UdpChildChannel child = new UdpChildChannel(channel, (InetSocketAddress) remoteAddress);
                channel.pipeline().fireChannelRead(child).fireChannelReadComplete();
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
