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
        final RakNet.Config config = (RakNet.Config) ctx.channel().config();
        switch (state) {
            case CR1:
                if (msg instanceof ConnectionRequest1) {
                    //version already checked, can skip that part
                    config.setMTU(((ConnectionRequest1) msg).getMtu());
                } else if (msg instanceof ConnectionRequest2) {
                    config.setMTU(((ConnectionRequest2) msg).getMtu());
                    config.setClientId(((ConnectionRequest2) msg).getClientId());
                    state = State.CR2;
                }
            case CR2: {
                if (msg instanceof ConnectionRequest) {
                    final Packet packet = new ServerHandshake(
                            (InetSocketAddress) ctx.channel().remoteAddress(),
                            ((ConnectionRequest) msg).getTimestamp());
                    ctx.writeAndFlush(packet).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
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
        }

        sendRequest(ctx);
    }

    public void sendRequest(ChannelHandlerContext ctx) {
        final RakNet.Config config = (RakNet.Config) ctx.channel().config();
        switch(state) {
            case CR1: {
                final Packet packet = new ConnectionReply1(config.getMTU(), config.getServerId());
                ctx.writeAndFlush(packet).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
                break;
            }
            case CR2: {
                final Packet packet = new ConnectionReply2(config.getMTU(), config.getServerId(),
                        (InetSocketAddress) ctx.channel().remoteAddress());
                ctx.writeAndFlush(packet).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
                break;
            }
        }
    }
}
