package network.ycc.raknet.server.pipeline;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

import network.ycc.raknet.RakNet;
import network.ycc.raknet.packet.ClientHandshake;
import network.ycc.raknet.packet.ConnectionReply1;
import network.ycc.raknet.packet.ConnectionReply2;
import network.ycc.raknet.packet.ConnectionRequest;
import network.ycc.raknet.packet.ConnectionRequest1;
import network.ycc.raknet.packet.ConnectionRequest2;
import network.ycc.raknet.packet.InvalidVersion;
import network.ycc.raknet.packet.Packet;
import network.ycc.raknet.packet.ServerHandshake;
import network.ycc.raknet.pipeline.AbstractConnectionInitializer;

import java.net.InetSocketAddress;

public class ConnectionInitializer extends AbstractConnectionInitializer {
    public ConnectionInitializer(ChannelPromise connectPromise) {
        super(connectPromise);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void channelRead0(ChannelHandlerContext ctx, Packet msg) {
        final RakNet.Config config = (RakNet.Config) ctx.channel().config();
        switch (state) {
            case CR1:
                if (msg instanceof ConnectionRequest1) {
                    final ConnectionRequest1 cr1 = (ConnectionRequest1) msg;
                    cr1.getMagic().verify(config.getMagic());
                    if (cr1.getProtocolVersion() != config.getProtocolVersion()) {
                        final InvalidVersion packet = new InvalidVersion(config.getMagic(), config.getServerId());
                        ctx.writeAndFlush(packet).addListener(ChannelFutureListener.CLOSE);
                        return;
                    }
                    config.setMTU(cr1.getMtu());
                } else if (msg instanceof ConnectionRequest2) {
                    final ConnectionRequest2 cr2 = (ConnectionRequest2) msg;
                    cr2.getMagic().verify(config.getMagic());
                    config.setMTU(cr2.getMtu());
                    config.setClientId(cr2.getClientId());
                    state = State.CR2;
                }
            case CR2: {
                if (msg instanceof ConnectionRequest) {
                    final Packet packet = new ServerHandshake(
                            (InetSocketAddress) ctx.channel().remoteAddress(),
                            ((ConnectionRequest) msg).getTimestamp());
                    ctx.writeAndFlush(packet).addListeners(
                            ChannelFutureListener.CLOSE_ON_FAILURE, ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
                    state = State.CR3;
                }
                break;
            }
            case CR3: {
                if (msg instanceof ClientHandshake) {
                    finish(ctx);
                    return;
                }
                break;
            }
            default:
        }

        sendRequest(ctx);
    }

    public void sendRequest(ChannelHandlerContext ctx) {
        final RakNet.Config config = (RakNet.Config) ctx.channel().config();
        switch(state) {
            case CR1: {
                final Packet packet = new ConnectionReply1(config.getMagic(), config.getMTU(), config.getServerId());
                ctx.writeAndFlush(packet).addListeners(
                        ChannelFutureListener.CLOSE_ON_FAILURE, ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
                break;
            }
            case CR2: {
                final Packet packet = new ConnectionReply2(config.getMagic(), config.getMTU(),
                        config.getServerId(), (InetSocketAddress) ctx.channel().remoteAddress());
                ctx.writeAndFlush(packet).addListeners(
                        ChannelFutureListener.CLOSE_ON_FAILURE, ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
                break;
            }
            default:
        }
    }
}
