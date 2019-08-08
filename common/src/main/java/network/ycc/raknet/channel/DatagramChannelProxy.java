package network.ycc.raknet.channel;

import network.ycc.raknet.RakNet;
import network.ycc.raknet.config.DefaultConfig;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelProgressivePromise;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelPipeline;
import io.netty.channel.EventLoop;
import io.netty.channel.socket.DatagramChannel;
import io.netty.handler.flush.FlushConsolidationHandler;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;

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
        listener.pipeline()
                .addLast(new FlushConsolidationHandler(
                        FlushConsolidationHandler.DEFAULT_EXPLICIT_FLUSH_AFTER_FLUSHES, true))
                .addLast(LISTENER_HANDLER_NAME, new ListenerInboundProxy());
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

    public Channel read() {
        pipeline.read();
        return this;
    }

    public Channel flush() {
        pipeline.flush();
        return this;
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

    public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress,
            ChannelPromise promise) {
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

    public ChannelFuture write(Object msg) {
        return pipeline.write(msg);
    }

    public ChannelFuture write(Object msg, ChannelPromise promise) {
        return pipeline.write(msg, promise);
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

    protected void gracefulClose(ChannelPromise promise) {
        listener.close(wrapPromise(promise));
    }

    protected DefaultChannelPipeline newChannelPipeline() {
        return new DefaultChannelPipeline(this) {
            @Override
            protected void onUnhandledInboundException(Throwable cause) {
                if (cause instanceof ClosedChannelException) {
                    ReferenceCountUtil.safeRelease(cause);
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
        @SuppressWarnings({"unchecked", "deprecation"})
        public <T> T getOption(ChannelOption<T> option) {
            final T thisOption = super.getOption(option);
            if (thisOption == null) {
                return listener.config().getOption(option);
            }
            return thisOption;
        }

        @Override
        @SuppressWarnings("deprecation")
        public <T> boolean setOption(ChannelOption<T> option, T value) {
            final boolean thisOption = super.setOption(option, value);
            final boolean listenOption = listener.config().setOption(option, value);
            return thisOption || listenOption;
        }

    }

    protected class ListnerOutboundProxy implements ChannelOutboundHandler {

        public void handlerAdded(ChannelHandlerContext ctx) {
            assert listener.eventLoop().inEventLoop();
            // NOOP
        }

        public void bind(ChannelHandlerContext ctx, SocketAddress localAddress,
                ChannelPromise promise) {
            listener.bind(localAddress, wrapPromise(promise));
        }

        public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress,
                SocketAddress localAddress, ChannelPromise promise) {
            listener.connect(remoteAddress, localAddress, wrapPromise(promise));
        }

        public void handlerRemoved(ChannelHandlerContext ctx) {
            // NOOP
        }

        public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) {
            listener.disconnect(wrapPromise(promise));
        }

        public void close(ChannelHandlerContext ctx, ChannelPromise promise) {
            gracefulClose(promise);
        }

        public void deregister(ChannelHandlerContext ctx, ChannelPromise promise) {
            listener.deregister(wrapPromise(promise));
        }

        public void read(ChannelHandlerContext ctx) {
            listener.read();
        }

        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            listener.write(msg, wrapPromise(promise));
        }

        public void flush(ChannelHandlerContext ctx) {
            listener.flush();
        }

        @SuppressWarnings("deprecation")
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            ctx.fireExceptionCaught(cause);
        }

    }

    protected class ListenerInboundProxy implements ChannelInboundHandler {

        public void channelRegistered(ChannelHandlerContext ctx) {
            pipeline.fireChannelRegistered();
        }

        public void channelUnregistered(ChannelHandlerContext ctx) {
            pipeline.fireChannelUnregistered();
        }

        public void handlerAdded(ChannelHandlerContext ctx) {
            assert listener.eventLoop().inEventLoop();
            // NOOP
        }

        public void channelActive(ChannelHandlerContext ctx) {
            // NOOP - active status managed by connection sequence
        }

        public void channelInactive(ChannelHandlerContext ctx) {
            pipeline.fireChannelInactive();
        }

        public void handlerRemoved(ChannelHandlerContext ctx) {
            // NOOP
        }

        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            pipeline.fireChannelRead(msg);
        }

        public void channelReadComplete(ChannelHandlerContext ctx) {
            pipeline.fireChannelReadComplete();
        }

        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            pipeline.fireUserEventTriggered(evt);
        }

        public void channelWritabilityChanged(ChannelHandlerContext ctx) {
            pipeline.fireChannelWritabilityChanged();
        }

        @SuppressWarnings("deprecation")
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            if (cause instanceof ClosedChannelException) {
                return;
            }
            pipeline.fireExceptionCaught(cause);
        }

    }

}
