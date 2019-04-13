package network.ycc.raknet.client.pipeline;

import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;

import io.netty.channel.ChannelPromise;
import io.netty.channel.ConnectTimeoutException;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.UnsupportedMessageTypeException;
import io.netty.util.concurrent.ScheduledFuture;
import network.ycc.raknet.RakNet;
import network.ycc.raknet.packet.ConnectionFailed;
import network.ycc.raknet.packet.ConnectionReply1;
import network.ycc.raknet.packet.ConnectionReply2;
import network.ycc.raknet.packet.ConnectionRequest;
import network.ycc.raknet.packet.ConnectionRequest1;
import network.ycc.raknet.packet.ConnectionRequest2;
import network.ycc.raknet.packet.InvalidVersion;
import network.ycc.raknet.packet.Packet;
import network.ycc.raknet.packet.Ping;
import network.ycc.raknet.packet.ServerHandshake;

import java.util.IllegalFormatException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class ConnectionInitializer extends SimpleChannelInboundHandler<Packet> {

    public static final String NAME = "rn-init-connect";

    protected final ChannelPromise connectPromise;
    protected final long guid = UUID.randomUUID().getLeastSignificantBits();
    protected State state = State.CR1;
    protected ScheduledFuture<?> sendTimer = null;
    protected ScheduledFuture<?> connectTimer = null;

    public ConnectionInitializer(ChannelPromise connectPromise) {
        this.connectPromise = connectPromise;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        sendTimer = ctx.channel().eventLoop().scheduleAtFixedRate(() -> sendRequest(ctx),
                250, 250, TimeUnit.MILLISECONDS);
        connectTimer = ctx.channel().eventLoop().schedule(() -> doTimeout(ctx),
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
        connectPromise.tryFailure(cause);
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, Packet msg) {
        final RakNet.Config config = (RakNet.Config) ctx.channel().config();
        switch (state) {
            case CR1: {
                if (msg instanceof ConnectionReply1) {
                    config.setMTU(((ConnectionReply1) msg).getMtu());
                    config.setServerId(((ConnectionReply1) msg).getServerId());
                    state = State.CR2;
                } else if (msg instanceof InvalidVersion) {
                    connectPromise.tryFailure(new UnsupportedMessageTypeException("Invalid RakNet version"));
                }
                break;
            }
            case CR2: {
                if (msg instanceof ConnectionReply2) {
                    config.setMTU(((ConnectionReply2) msg).getMtu());
                    config.setServerId(((ConnectionReply2) msg).getServerId());
                    state = State.CR3;
                    sendTimer.cancel(false);
                    final ScheduledFuture<?> pingTask = ctx.channel().eventLoop().scheduleAtFixedRate(
                            () -> ctx.channel().writeAndFlush(new Ping()).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE),
                            100, 250, TimeUnit.MILLISECONDS
                    );
                    ctx.channel().closeFuture().addListener(x -> pingTask.cancel(false));
                    final Packet packet = new ConnectionRequest(guid);
                    ctx.writeAndFlush(packet).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
                } else if (msg instanceof ConnectionFailed) {
                    connectPromise.tryFailure(new ChannelException("RakNet connection failed"));
                }
                break;
            }
            case CR3: {
                if (msg instanceof ServerHandshake) {
                    connectPromise.trySuccess();
                    ctx.pipeline().remove(this);
                    return;
                }
                break;
            }
        }

        sendRequest(ctx);
    }

    public void sendRequest(ChannelHandlerContext ctx) {
        final RakNet.Config config = (RakNet.Config) ctx.channel().config();
        switch(state) {
            case CR1: {
                final Packet packet = new ConnectionRequest1(InvalidVersion.VALID_VERSION, config.getMTU());
                ctx.writeAndFlush(packet).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
                break;
            }
            case CR2: {
                final Packet packet = new ConnectionRequest2(config.getMTU(), guid);
                ctx.writeAndFlush(packet).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
                break;
            }
        }
    }

    protected void doTimeout(ChannelHandlerContext ctx) {
        connectPromise.tryFailure(new ConnectTimeoutException());
    }

    protected enum State {
        CR1, //UDP: ConnectionRequest1 -> ConnectionReply1, InvalidVersion
        CR2, //UDP: ConnectionRequest2 -> ConnectionReply2, ConnectionFailed
        CR3, //Framed: ConnectionRequest -> Handshake
    }

}
