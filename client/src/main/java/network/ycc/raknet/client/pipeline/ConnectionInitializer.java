package network.ycc.raknet.client.pipeline;

import io.netty.channel.ChannelException;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

import network.ycc.raknet.RakNet;
import network.ycc.raknet.packet.ClientHandshake;
import network.ycc.raknet.packet.ConnectionFailed;
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

    @Override
    public void channelRead0(ChannelHandlerContext ctx, Packet msg) {
        final RakNet.Config config = RakNet.config(ctx);
        switch (state) {
            case CR1: {
                if (msg instanceof ConnectionReply1) {
                    final ConnectionReply1 cr1 = (ConnectionReply1) msg;
                    cr1.getMagic().verify(config.getMagic());
                    config.setMTU(cr1.getMtu());
                    config.setServerId(cr1.getServerId());
                    state = State.CR2;
                } else if (msg instanceof InvalidVersion) {
                    fail(new InvalidVersion.InvalidVersionException());
                }
                break;
            }
            case CR2: {
                if (msg instanceof ConnectionReply2) {
                    final ConnectionReply2 cr2 = (ConnectionReply2) msg;
                    cr2.getMagic().verify(config.getMagic());
                    config.setMTU(cr2.getMtu());
                    config.setServerId(cr2.getServerId());
                    state = State.CR3;
                    final Packet packet = new ConnectionRequest(config.getClientId());
                    ctx.writeAndFlush(packet).addListener(RakNet.INTERNAL_WRITE_LISTENER);
                } else if (msg instanceof ConnectionFailed) {
                    fail(new ChannelException("RakNet connection failed"));
                }
                break;
            }
            case CR3: {
                if (msg instanceof ServerHandshake) {
                    final Packet packet = new ClientHandshake(((ServerHandshake) msg).getTimestamp(),
                            (InetSocketAddress) ctx.channel().remoteAddress(), ((ServerHandshake) msg).getnExtraAddresses());
                    ctx.writeAndFlush(packet).addListener(RakNet.INTERNAL_WRITE_LISTENER);
                    finish(ctx);
                    return;
                }
                break;
            }
            default:
                throw new IllegalStateException("Unknown state " + state);
        }

        sendRequest(ctx);
    }

    public void sendRequest(ChannelHandlerContext ctx) {
        final RakNet.Config config = RakNet.config(ctx);
        switch(state) {
            case CR1: {
                final Packet packet = new ConnectionRequest1(config.getMagic(),
                        config.getProtocolVersion(), config.getMTU());
                ctx.writeAndFlush(packet).addListener(RakNet.INTERNAL_WRITE_LISTENER);
                break;
            }
            case CR2: {
                final Packet packet = new ConnectionRequest2(config.getMagic(), config.getMTU(),
                        config.getClientId(), (InetSocketAddress) ctx.channel().remoteAddress());
                ctx.writeAndFlush(packet).addListener(RakNet.INTERNAL_WRITE_LISTENER);
                break;
            }
            case CR3:
                break; // NOOP - ClientHandshake is sent as reliable.
            default:
                throw new IllegalStateException("Unknown state " + state);
        }
    }

}
