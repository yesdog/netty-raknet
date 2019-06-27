package network.ycc.raknet.packet;

import network.ycc.raknet.RakNet;
import network.ycc.raknet.config.DefaultMagic;

import io.netty.buffer.ByteBuf;

public class NoFreeConnections extends SimplePacket implements Packet {

    private RakNet.Magic magic;
    private long serverId;

    public NoFreeConnections() {

    }

    public NoFreeConnections(RakNet.Magic magic, long serverId) {
        this.magic = magic;
        this.serverId = serverId;
    }

    public void encode(ByteBuf buf) {
        magic.write(buf);
        buf.writeLong(serverId);
    }

    public void decode(ByteBuf buf) {
        magic = DefaultMagic.decode(buf);
        serverId = buf.readLong();
    }

    public RakNet.Magic getMagic() {
        return magic;
    }

    public void setMagic(RakNet.Magic magic) {
        this.magic = magic;
    }

    public long getServerId() {
        return serverId;
    }

    public void setServerId(long serverId) {
        this.serverId = serverId;
    }

}
