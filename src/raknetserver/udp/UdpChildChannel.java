package raknetserver.udp;

import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.ReferenceCountUtil;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

public class UdpChildChannel extends AbstractChannel {
    protected final Config config;
    protected final InetSocketAddress remoteAddress;
    protected final ChannelMetadata metadata = new ChannelMetadata(false);

    protected volatile boolean open = true;

    protected UdpChildChannel(UdpServerChannel parent, InetSocketAddress remoteAddress) {
        super(parent);
        this.remoteAddress = remoteAddress;
        config = new Config();
    }

    @Override
    public UdpServerChannel parent() {
        return (UdpServerChannel) super.parent();
    }

    protected boolean isCompatible(EventLoop eventloop) {
        return true;
    }

    protected AbstractUnsafe newUnsafe() {
        return new AbstractUnsafe() {
            public void connect(SocketAddress addr1, SocketAddress addr2, ChannelPromise pr) {
                throw new UnsupportedOperationException();
            }
        };
    }

    protected SocketAddress localAddress0() {
        return parent().localAddress();
    }

    protected SocketAddress remoteAddress0() {
        return remoteAddress;
    }

    protected void doBind(SocketAddress addr) {
        throw new UnsupportedOperationException();
    }

    protected void doDisconnect() {
        doClose();
    }

    protected void doClose() {
        open = false;
    }

    protected void doBeginRead() {}

    protected void doWrite(ChannelOutboundBuffer buffer) {
        Object obj;
        boolean wroteAny = false;
        while ((obj = buffer.current()) != null) {
            if (obj instanceof ByteBuf) {
                final ByteBuf data = ReferenceCountUtil.retain((ByteBuf) obj);
                parent().write(new DatagramPacket(data, remoteAddress))
                        .addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
                wroteAny = true;
            }
            buffer.remove();
        }
        if (wroteAny) {
            parent().flush();
        }
    }

    public Config config() {
        return config;
    }

    public boolean isOpen() {
        return open;
    }

    public boolean isActive() {
        return isOpen() && isRegistered();
    }

    public ChannelMetadata metadata() {
        return metadata;
    }

    public class Config extends RakNetConfig {
        protected Config() {
            super(UdpChildChannel.this);
            metrics = parent().config.metrics;
            serverId = parent().config.serverId;
            userDataId = parent().config.userDataId;
        }
    }
}
