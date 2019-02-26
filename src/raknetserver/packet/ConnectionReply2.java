package raknetserver.packet;

import io.netty.buffer.ByteBuf;

import raknetserver.utils.DataSerializer;
import raknetserver.utils.Constants;

public class ConnectionReply2 extends SimplePacket implements Packet {

    private static final boolean needsSecurity = false;

    private final int mtu;
    private final long serverId;
    
    public ConnectionReply2(int mtu, long serverId) {
        this.mtu = mtu;
        this.serverId = serverId;
    }

    @Override
    public void decode(ByteBuf buf) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void encode(ByteBuf buf) {
        buf.writeBytes(Constants.MAGIC);
        buf.writeLong(serverId);
        DataSerializer.writeAddress(buf, Constants.NULL_ADDR);
        buf.writeShort(mtu);
        buf.writeBoolean(needsSecurity);
    }

}
