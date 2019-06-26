package network.ycc.raknet.packet;

import io.netty.buffer.ByteBuf;

import network.ycc.raknet.RakNet;
import network.ycc.raknet.config.DefaultMagic;

import java.net.InetSocketAddress;

public class ConnectionRequest2 extends SimplePacket implements Packet, Packet.ClientIdConnection {

    private RakNet.Magic magic;
    private int mtu;
    private long clientId;
    private InetSocketAddress address;

    public ConnectionRequest2() {}

    public ConnectionRequest2(RakNet.Magic magic, int mtu, long clientId, InetSocketAddress address) {
        this.magic = magic;
        this.mtu = mtu;
        this.clientId = clientId;
        this.address = address;
    }

    @Override
    public void decode(ByteBuf buf) {
        magic = DefaultMagic.decode(buf);
        address = readAddress(buf);
        mtu = buf.readShort();
        clientId = buf.readLong();
    }

    @Override
    public void encode(ByteBuf buf) {
        magic.write(buf);
        writeAddress(buf, address);
        buf.writeShort(mtu);
        buf.writeLong(clientId);
    }

    public RakNet.Magic getMagic() {
        return magic;
    }

    public void setMagic(RakNet.Magic magic) {
        this.magic = magic;
    }

    public int getMtu() {
        return mtu;
    }

    public void setMtu(int mtu) {
        this.mtu = mtu;
    }

    public long getClientId() {
        return clientId;
    }

    public void setClientId(long clientId) {
        this.clientId = clientId;
    }

    public InetSocketAddress getAddress() {
        return address;
    }

    public void setAddress(InetSocketAddress address) {
        this.address = address;
    }

}
