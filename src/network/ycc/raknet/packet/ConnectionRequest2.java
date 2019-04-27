package network.ycc.raknet.packet;

import io.netty.buffer.ByteBuf;
import network.ycc.raknet.utils.DataSerializer;

import java.net.InetSocketAddress;

public class ConnectionRequest2 extends SimplePacket implements Packet {

    private int mtu;
    private long clientId;
    private InetSocketAddress address;

    public ConnectionRequest2() {}

    public ConnectionRequest2(int mtu, long clientId, InetSocketAddress address) {
        this.mtu = mtu;
        this.clientId = clientId;
        this.address = address;
    }

    @Override
    public void decode(ByteBuf buf) {
        DataSerializer.readMagic(buf);
        address = DataSerializer.readAddress(buf);
        mtu = buf.readShort();
        clientId = buf.readLong();
    }

    @Override
    public void encode(ByteBuf buf) {
        DataSerializer.writeMagic(buf);
        DataSerializer.writeAddress(buf, address);
        buf.writeShort(mtu);
        buf.writeLong(clientId);
    }

    public int getMtu() {
        return mtu;
    }

    public long getClientId() {
        return clientId;
    }

}
