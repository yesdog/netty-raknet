package network.ycc.raknet.server.pipeline;

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

import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;

import java.net.InetSocketAddress;

public class ConnectionInitializer extends AbstractConnectionInitializer {

    protected boolean clientIdSet = false;
    protected boolean mtuFixed = false;
    protected boolean seenFirst = false;

    public ConnectionInitializer(ChannelPromise connectPromise) {
        super(connectPromise);
    }

    public static void setFixedMTU(Channel channel, int mtu) {
        channel.eventLoop().execute(() -> {
            channel.pipeline().get(ConnectionInitializer.class).mtuFixed = true;
            RakNet.config(channel).setMTU(mtu);
        });
    }

    @SuppressWarnings("unchecked")
    @Override
    public void channelRead0(ChannelHandlerContext ctx, Packet msg) {
        final RakNet.Config config = RakNet.config(ctx);
        if (msg instanceof Packet.ClientIdConnection) {
            processClientId(ctx, ((Packet.ClientIdConnection) msg).getClientId());
        } else if (msg instanceof ConnectionFailed) {
            throw new IllegalStateException("Connection failed");
        }
        switch (state) {
            case CR1:
                if (msg instanceof ConnectionRequest1) {
                    final ConnectionRequest1 cr1 = (ConnectionRequest1) msg;
                    cr1.getMagic().verify(config.getMagic());
                    if (!mtuFixed) {
                        config.setMTU(cr1.getMtu());
                    }
                    seenFirst = true;

                    if (!config.containsProtocolVersion(cr1.getProtocolVersion())) {
                        final InvalidVersion packet = new InvalidVersion(config.getMagic(), config.getServerId());
                        ctx.writeAndFlush(packet).addListener(ChannelFutureListener.CLOSE);
                        return;
                    }

                    config.setProtocolVersion(cr1.getProtocolVersion());
                } else if (msg instanceof ConnectionRequest2) {
                    final ConnectionRequest2 cr2 = (ConnectionRequest2) msg;
                    cr2.getMagic().verify(config.getMagic());
                    if (!mtuFixed) {
                        config.setMTU(cr2.getMtu());
                    }
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
                    state = State.CR3;
                    startPing(ctx);
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
        assert ctx.channel().eventLoop().inEventLoop();

        final RakNet.Config config = RakNet.config(ctx);
        switch (state) {
            case CR1: {
                if (seenFirst) {
                    final Packet packet = new ConnectionReply1(config.getMagic(),
                            config.getMTU(), config.getServerId());
                    ctx.writeAndFlush(packet).addListener(RakNet.INTERNAL_WRITE_LISTENER);
                }
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

    protected void removeHandler(ChannelHandlerContext ctx) {
        ctx.channel().pipeline().replace(NAME, NAME, new RestartConnectionHandler());
    }

    protected void processClientId(ChannelHandlerContext ctx, long clientId) {
        final RakNet.Config config = RakNet.config(ctx);
        if (!clientIdSet) {
            config.setClientId(clientId);
            clientIdSet = true;
        } else if (config.getClientId() != clientId) {
            throw new IllegalStateException("Connection sequence restarted");
        }
    }

    protected class RestartConnectionHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof Packet.ClientIdConnection || msg instanceof ConnectionRequest1) {
                final RakNet.Config config = RakNet.config(ctx);
                ctx.writeAndFlush(new ConnectionFailed(config.getMagic())).addListener(ChannelFutureListener.CLOSE);
                ReferenceCountUtil.safeRelease(msg);
            } else if (msg instanceof ConnectionFailed) {
                ReferenceCountUtil.safeRelease(msg);
                ctx.close();
            } else {
                ctx.fireChannelRead(msg);
            }
        }
    }

}
