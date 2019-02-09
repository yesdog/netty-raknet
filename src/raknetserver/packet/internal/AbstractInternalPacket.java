package raknetserver.packet.internal;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

public abstract class AbstractInternalPacket implements InternalPacket {

    protected Reliability reliability = Reliability.RELIABLE_ORDERED;
    protected int orderId = 0;

    public int getPacketId() {
        //TODO: cache?
        return InternalPacketRegistry.getId(getClass());
    }

    public Reliability getReliability() {
        return reliability;
    }

    public void setReliability(Reliability reliability) {
        this.reliability = reliability;
    }

    public int getOrderId() {
        return orderId;
    }

    public void setOrderId(int orderId) {
        this.orderId = orderId;
    }

    public void encodeFull(ByteBuf buf) {
        buf.writeByte(getPacketId());
        encode(buf);
    }

    public InternalPacketData toInternalPacketData(ByteBufAllocator alloc) {
        final ByteBuf buf = alloc.ioBuffer();
        try {
            encodeFull(buf);
            final InternalPacketData out = InternalPacketData.read(buf);
            out.setReliability(getReliability());
            return out;
        } finally {
            buf.release();
        }
    }

}
