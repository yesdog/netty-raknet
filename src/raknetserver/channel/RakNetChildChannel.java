package raknetserver.channel;

import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.ReferenceCountUtil;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

public class RakNetChildChannel extends AbstractChannel {
    protected final Config config;
    protected final InetSocketAddress remoteAddress;
    protected final ChannelMetadata metadata = new ChannelMetadata(false);

    protected volatile boolean open = true;

    protected RakNetChildChannel(RakNetServerChannel parent, InetSocketAddress remoteAddress) {
        super(parent);
        this.remoteAddress = remoteAddress;
        config = new Config();
    }

    @Override
    public RakNetServerChannel parent() {
        return (RakNetServerChannel) super.parent();
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

    public class Config extends RakNetChannelConfig {
        //TODO: ChannelOption's... fml
        public static final int DEFAULT_BACK_PRESSURE_HIGH_WATERMARK = 2048;
        public static final int DEFAULT_BACK_PRESSURE_LOW_WATERMARK = 1024;
        public static final int DEFAULT_MAX_PENDING_FRAME_SETS = 1024;
        public static final int DEFAULT_DEFAULT_PENDING_FRAME_SETS = 64;

        protected volatile int backPressureHighWatermark = DEFAULT_BACK_PRESSURE_HIGH_WATERMARK;
        protected volatile int backPressureLowWatermark = DEFAULT_BACK_PRESSURE_LOW_WATERMARK;
        protected volatile int maxPendingFrameSets = DEFAULT_MAX_PENDING_FRAME_SETS;
        protected volatile int defaultPendingFrameSets = DEFAULT_DEFAULT_PENDING_FRAME_SETS;

        protected Config() {
            super(RakNetChildChannel.this);
            metrics = parent().config.metrics;
            serverId = parent().config.serverId;
            userDataId = parent().config.userDataId;
        }

        public int getBackPressureHighWatermark() {
            return backPressureHighWatermark;
        }

        public void setBackPressureHighWatermark(int backPressureHighWatermark) {
            this.backPressureHighWatermark = backPressureHighWatermark;
        }

        public int getBackPressureLowWatermark() {
            return backPressureLowWatermark;
        }

        public void setBackPressureLowWatermark(int backPressureLowWatermark) {
            this.backPressureLowWatermark = backPressureLowWatermark;
        }

        public int getMaxPendingFrameSets() {
            return maxPendingFrameSets;
        }

        public void setMaxPendingFrameSets(int maxPendingFrameSets) {
            this.maxPendingFrameSets = maxPendingFrameSets;
        }

        public int getDefaultPendingFrameSets() {
            return defaultPendingFrameSets;
        }

        public void setDefaultPendingFrameSets(int defaultPendingFrameSets) {
            this.defaultPendingFrameSets = defaultPendingFrameSets;
        }
    }
}
