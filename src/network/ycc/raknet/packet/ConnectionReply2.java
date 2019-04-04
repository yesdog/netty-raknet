package network.ycc.raknet.packet;

import io.netty.buffer.ByteBuf;

import network.ycc.raknet.utils.DataSerializer;

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
        DataSerializer.writeMagic(buf);
        buf.writeLong(serverId);
        DataSerializer.writeAddress(buf);
        buf.writeShort(mtu);
        buf.writeBoolean(needsSecurity);
    }

}
