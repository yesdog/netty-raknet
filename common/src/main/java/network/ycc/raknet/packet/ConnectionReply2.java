package network.ycc.raknet.packet;

import network.ycc.raknet.RakNet;
import network.ycc.raknet.config.DefaultMagic;

import io.netty.buffer.ByteBuf;

import java.net.InetSocketAddress;

public class ConnectionReply2 extends AbstractConnectionReply implements Packet {

    private InetSocketAddress address = null;

    public ConnectionReply2() {
    }

    public ConnectionReply2(RakNet.Magic magic, int mtu, long serverId, InetSocketAddress address) {
        super(magic, mtu, serverId);
        this.address = address;
    }

    public void encode(ByteBuf buf) {
        magic.write(buf);
        buf.writeLong(serverId);
        if (address == null) {
            writeAddress(buf);
        } else {
            writeAddress(buf, address);
        }
        buf.writeShort(mtu);
        buf.writeBoolean(NEEDS_SECURITY);
    }

    public void decode(ByteBuf buf) {
        magic = DefaultMagic.decode(buf);
        serverId = buf.readLong();
        address = readAddress(buf);
        mtu = buf.readShort();
        if (buf.readBoolean()) {
            throw new IllegalArgumentException("No security support yet"); //TODO: security i guess?
        }
    }

    public InetSocketAddress getAddress() {
        return address;
    }

    public void setAddress(InetSocketAddress address) {
        this.address = address;
    }

}
