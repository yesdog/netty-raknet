package network.ycc.raknet.packet;

import io.netty.buffer.ByteBuf;

import network.ycc.raknet.RakNet;
import network.ycc.raknet.config.DefaultMagic;

public class ConnectionReply1 extends AbstractConnectionReply implements Packet {

    public ConnectionReply1() {}

    public ConnectionReply1(RakNet.Magic magic, int mtu, long serverId) {
        super(magic, mtu, serverId);
    }

    public void decode(ByteBuf buf) {
        magic = DefaultMagic.decode(buf);
        serverId = buf.readLong();
        if (buf.readBoolean()) {
            throw new IllegalArgumentException("No security support yet"); //TODO: security i guess?
        }
        mtu = buf.readShort();
    }

    public void encode(ByteBuf buf) {
        magic.write(buf);
        buf.writeLong(serverId);
        buf.writeBoolean(needsSecurity);
        buf.writeShort(mtu);
    }

}
