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

    protected boolean clientIdSet = false;

    @SuppressWarnings("unchecked")
    @Override
    public void channelRead0(ChannelHandlerContext ctx, Packet msg) {
        final RakNet.Config config = RakNet.config(ctx);
        switch (state) {
            case CR1:
                if (msg instanceof ConnectionRequest1) {
                    final ConnectionRequest1 cr1 = (ConnectionRequest1) msg;
                    cr1.getMagic().verify(config.getMagic());
                    config.setMTU(cr1.getMtu());
                    if (cr1.getProtocolVersion() != config.getProtocolVersion()) {
                        final InvalidVersion packet = new InvalidVersion(config.getMagic(), config.getServerId());
                        ctx.writeAndFlush(packet).addListener(ChannelFutureListener.CLOSE);
                        return;
                    }
                } else if (msg instanceof ConnectionRequest2) {
                    final ConnectionRequest2 cr2 = (ConnectionRequest2) msg;
                    cr2.getMagic().verify(config.getMagic());
                    config.setMTU(cr2.getMtu());
                    processClientId(ctx, cr2.getClientId());
                    state = State.CR2;
                }
                break;
            case CR2: {
                if (msg instanceof ConnectionRequest) {
                    final ConnectionRequest cr = (ConnectionRequest) msg;
                    final Packet packet = new ServerHandshake(
                            (InetSocketAddress) ctx.channel().remoteAddress(),
                            cr.getTimestamp());
                    ctx.writeAndFlush(packet).addListener(RakNet.INTERNAL_WRITE_LISTENER);
                    processClientId(ctx, cr.getClientId());
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
                throw new IllegalStateException("Unknown state " + state);
        }

        sendRequest(ctx);
    }

    @SuppressWarnings("unchecked")
    public void sendRequest(ChannelHandlerContext ctx) {
        final RakNet.Config config = RakNet.config(ctx);
        switch(state) {
            case CR1: {
                final Packet packet = new ConnectionReply1(config.getMagic(), config.getMTU(), config.getServerId());
                ctx.writeAndFlush(packet).addListener(RakNet.INTERNAL_WRITE_LISTENER);
                break;
            }
            case CR2: {
                final Packet packet = new ConnectionReply2(config.getMagic(), config.getMTU(),
                        config.getServerId(), (InetSocketAddress) ctx.channel().remoteAddress());
                ctx.writeAndFlush(packet).addListener(RakNet.INTERNAL_WRITE_LISTENER);
                break;
            }
            case CR3:
                break; // NOOP - ServerHandshake is sent as reliable.
            default:
                throw new IllegalStateException("Unknown state " + state);
        }
    }

    protected void processClientId(ChannelHandlerContext ctx, long clientId) {
        final RakNet.Config config = RakNet.config(ctx);
        if (!clientIdSet) {
            config.setClientId(clientId);
            clientIdSet = true;
            return;
        } else if (config.getClientId() != clientId) {
            ctx.close();
            throw new IllegalStateException("Connection sequence restarted");
        }
    }
}
