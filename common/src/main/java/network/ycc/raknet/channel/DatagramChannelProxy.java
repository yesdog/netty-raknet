package network.ycc.raknet.channel;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.socket.DatagramChannel;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;

import network.ycc.raknet.RakNet;
import network.ycc.raknet.config.DefaultConfig;

import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.function.Supplier;

public class DatagramChannelProxy implements Channel {

    public static final String LISTENER_HANDLER_NAME = "rn-udp-listener-handler";

    protected final DefaultChannelPipeline pipeline;
    protected final DatagramChannel listener;
    protected final Config config;

    public DatagramChannelProxy(Supplier<? extends DatagramChannel> ioChannelSupplier) {
        listener = ioChannelSupplier.get();
        pipeline = newChannelPipeline();
        listener.pipeline().addLast(new ListenerInboundProxy());
        pipeline().addLast(LISTENER_HANDLER_NAME, new ListnerOutboundProxy());
        config = new Config();
    }

    public DatagramChannelProxy(Class<? extends DatagramChannel> ioChannelType) {
        this(() -> {
            try {
                return ioChannelType.newInstance();
            } catch (InstantiationException | IllegalAccessException e) {
                throw new IllegalArgumentException("Failed to create instance", e);
            }
        });
    }

    public ChannelId id() {
        return listener.id();
    }

    public EventLoop eventLoop() {
        return listener.eventLoop();
    }

    public Channel parent() {
        return listener;
    }

    public RakNet.Config config() {
        return config;
    }

    public boolean isOpen() {
        return listener.isOpen();
    }

    public boolean isRegistered() {
        return listener.isRegistered();
    }

    public boolean isActive() {
        return listener.isActive();
    }

    public ChannelMetadata metadata() {
        return listener.metadata();
    }

    public SocketAddress localAddress() {
        return listener.localAddress();
    }

    public SocketAddress remoteAddress() {
        return listener.remoteAddress();
    }

    public ChannelFuture closeFuture() {
        return listener.closeFuture();
    }

    public boolean isWritable() {
        return listener.isWritable();
    }

    public long bytesBeforeUnwritable() {
        return listener.bytesBeforeUnwritable();
    }

    public long bytesBeforeWritable() {
        return listener.bytesBeforeWritable();
    }

    public Unsafe unsafe() {
        return listener.unsafe();
    }

    public ChannelPipeline pipeline() {
        return pipeline;
    }

    public ByteBufAllocator alloc() {
        return config().getAllocator();
    }

    public ChannelFuture bind(SocketAddress localAddress) {
        return pipeline.bind(localAddress);
    }

    public ChannelFuture connect(SocketAddress remoteAddress) {
        return pipeline.connect(remoteAddress);
    }

    public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress) {
        return pipeline.connect(remoteAddress, localAddress);
    }

    public ChannelFuture disconnect() {
        return pipeline.disconnect();
    }

    public ChannelFuture close() {
        return pipeline.close();
    }

    public ChannelFuture deregister() {
        return pipeline.deregister();
    }

    public ChannelFuture bind(SocketAddress localAddress, ChannelPromise promise) {
        return pipeline.bind(localAddress, promise);
    }

    public ChannelFuture connect(SocketAddress remoteAddress, ChannelPromise promise) {
        return pipeline.connect(remoteAddress, promise);
    }

    public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
        return pipeline.connect(remoteAddress, localAddress, promise);
    }

    public ChannelFuture disconnect(ChannelPromise promise) {
        return pipeline.disconnect(promise);
    }

    public ChannelFuture close(ChannelPromise promise) {
        return pipeline.close(promise);
    }

    public ChannelFuture deregister(ChannelPromise promise) {
        return pipeline.deregister(promise);
    }

    public Channel read() {
        pipeline.read();
        return this;
    }

    public ChannelFuture write(Object msg) {
        return pipeline.write(msg);
    }

    public ChannelFuture write(Object msg, ChannelPromise promise) {
        return pipeline.write(msg, promise);
    }

    public Channel flush() {
        pipeline.flush();
        return this;
    }

    public ChannelFuture writeAndFlush(Object msg, ChannelPromise promise) {
        return pipeline.writeAndFlush(msg, promise);
    }

    public ChannelFuture writeAndFlush(Object msg) {
        return pipeline.writeAndFlush(msg);
    }

    public ChannelPromise newPromise() {
        return pipeline.newPromise();
    }

    public ChannelProgressivePromise newProgressivePromise() {
        return pipeline.newProgressivePromise();
    }

    public ChannelFuture newSucceededFuture() {
        return pipeline.newSucceededFuture();
    }

    public ChannelFuture newFailedFuture(Throwable cause) {
        return pipeline.newFailedFuture(cause);
    }

    public ChannelPromise voidPromise() {
        return pipeline.voidPromise();
    }

    public <T> Attribute<T> attr(AttributeKey<T> key) {
        return listener.attr(key);
    }

    public <T> boolean hasAttr(AttributeKey<T> key) {
        return listener.hasAttr(key);
    }

    public int compareTo(Channel o) {
        return listener.compareTo(o);
    }

    protected DefaultChannelPipeline newChannelPipeline() {
        return new DefaultChannelPipeline(this) {
            @Override
            protected void onUnhandledInboundException(Throwable cause) {
                if (cause instanceof ClosedChannelException) {
                    return;
                }
                super.onUnhandledInboundException(cause);
            }
        };
    }

    protected ChannelPromise wrapPromise(ChannelPromise in) {
        final ChannelPromise out = listener.newPromise();
        out.addListener(res -> {
            if (res.isSuccess()) {
                in.trySuccess();
            } else {
                in.tryFailure(res.cause());
            }
        });
        return out;
    }

    protected class Config extends DefaultConfig {

        protected Config() {
            super(DatagramChannelProxy.this);
        }

        @Override
        @SuppressWarnings("deprecation")
        public <T> boolean setOption(ChannelOption<T> option, T value) {
            final boolean thisOption = super.setOption(option, value);
            final boolean listenOption = listener.config().setOption(option, value);
            return thisOption || listenOption;
        }

        @Override
        @SuppressWarnings({ "unchecked", "deprecation" })
        public <T> T getOption(ChannelOption<T> option) {
            final T thisOption = super.getOption(option);
            if (thisOption == null) {
                return listener.config().getOption(option);
            }
            return thisOption;
        }

    }

    protected class ListnerOutboundProxy implements ChannelOutboundHandler {

        public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
            // NOOP
        }

        public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
            // NOOP
        }

        public void bind(ChannelHandlerContext ctx, SocketAddress localAddress, ChannelPromise promise) throws Exception {
            listener.bind(localAddress, wrapPromise(promise));
        }

        public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) throws Exception {
            listener.connect(remoteAddress, localAddress, wrapPromise(promise));
        }

        public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
            listener.disconnect(wrapPromise(promise));
        }

        public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
            listener.close(wrapPromise(promise));
        }

        public void deregister(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
            listener.deregister(wrapPromise(promise));
        }

        public void read(ChannelHandlerContext ctx) throws Exception {
            listener.read();
        }

        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            listener.write(msg, wrapPromise(promise));
        }

        public void flush(ChannelHandlerContext ctx) throws Exception {
            listener.flush();
        }

        @SuppressWarnings("deprecation")
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            ctx.fireExceptionCaught(cause);
        }

    }

    protected class ListenerInboundProxy implements ChannelInboundHandler {

        public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
            // NOOP
        }

        public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
            // NOOP
        }

        public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
            pipeline.fireChannelRegistered();
        }

        public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
            pipeline.fireChannelUnregistered();
        }

        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            // NOOP - active status managed by connection sequence
        }

        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            pipeline.fireChannelInactive();
        }

        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            pipeline.fireChannelRead(msg);
        }

        public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
            pipeline.fireChannelReadComplete();
        }

        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            pipeline.fireUserEventTriggered(evt);
        }

        public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
            pipeline.fireChannelWritabilityChanged();
        }

        @SuppressWarnings("deprecation")
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            if (cause instanceof ClosedChannelException) {
                return;
            }
            pipeline.fireExceptionCaught(cause);
        }

    }

}
