package network.ycc.raknet.pipeline;

import network.ycc.raknet.packet.Packet;
import network.ycc.raknet.packet.Ping;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.ConnectTimeoutException;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.concurrent.ScheduledFuture;

import java.util.concurrent.TimeUnit;

public abstract class AbstractConnectionInitializer extends SimpleChannelInboundHandler<Packet> {
    public static final String NAME = "rn-init-connect";

    protected final ChannelPromise connectPromise;
    protected State state = State.CR1;
    protected ScheduledFuture<?> sendTimer = null;
    protected ScheduledFuture<?> connectTimer = null;

    public AbstractConnectionInitializer(ChannelPromise connectPromise) {
        this.connectPromise = connectPromise;
    }

    protected abstract void sendRequest(ChannelHandlerContext ctx);

    protected abstract void removeHandler(ChannelHandlerContext ctx);

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        sendTimer = ctx.channel().eventLoop().scheduleAtFixedRate(() -> sendRequest(ctx),
                0, 200, TimeUnit.MILLISECONDS);
        connectTimer = ctx.channel().eventLoop().schedule(this::doTimeout,
                ctx.channel().config().getConnectTimeoutMillis(), TimeUnit.MILLISECONDS);
        sendRequest(ctx);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        sendTimer.cancel(false);
        connectTimer.cancel(false);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        fail(cause);
    }

    protected void startPing(ChannelHandlerContext ctx) {
        ctx.channel().pipeline().addAfter(NAME, PingProducer.NAME, new PingProducer());
    }

    protected void finish(ChannelHandlerContext ctx) {
        final Channel channel = ctx.channel();
        connectPromise.trySuccess();
        removeHandler(ctx);
        channel.pipeline().fireChannelActive();
    }

    protected void fail(Throwable cause) {
        connectPromise.tryFailure(cause);
    }

    protected void doTimeout() {
        fail(new ConnectTimeoutException());
    }

    protected enum State {
        CR1, //Raw: ConnectionRequest1 -> ConnectionReply1, InvalidVersion
        CR2, //Raw: ConnectionRequest2 -> ConnectionReply2, ConnectionFailed
        CR3, //Framed: ConnectionRequest -> Handshake -> ClientHandshake
    }
}
