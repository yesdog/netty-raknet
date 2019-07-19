package network.ycc.raknet.packet;

import network.ycc.raknet.RakNet;
import network.ycc.raknet.config.DefaultMagic;

import io.netty.buffer.ByteBuf;

public class ConnectionReply1 extends AbstractConnectionReply implements Packet {

    public ConnectionReply1() {
    }

    public ConnectionReply1(RakNet.Magic magic, int mtu, long serverId) {
        super(magic, mtu, serverId);
    }

    public void encode(ByteBuf buf) {
        magic.write(buf);
        buf.writeLong(serverId);
        buf.writeBoolean(NEEDS_SECURITY);
        buf.writeShort(mtu);
    }

    public void decode(ByteBuf buf) {
        magic = DefaultMagic.decode(buf);
        serverId = buf.readLong();
        if (buf.readBoolean()) {
            throw new IllegalArgumentException("No security support yet");
        }
        mtu = buf.readShort();
    }

}
