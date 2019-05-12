package network.ycc.raknet.packet;

import io.netty.buffer.ByteBuf;

import network.ycc.raknet.config.Magic;

public class InvalidVersion extends SimplePacket implements Packet {

    private Magic magic;
    private int version;
    private long serverId;

    public InvalidVersion() {

    }

    public InvalidVersion(long serverId) {
        this.serverId = serverId;
    }

    public void decode(ByteBuf buf) {
        version = buf.readUnsignedByte();
        magic = Magic.decode(buf);
        serverId = buf.readLong();
    }

    public void encode(ByteBuf buf) {
        buf.writeByte(version);
        magic.write(buf);
        buf.writeLong(serverId);
    }

    public Magic getMagic() {
        return magic;
    }

    public void setMagic(Magic magic) {
        this.magic = magic;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public long getServerId() {
        return serverId;
    }

    public void setServerId(long serverId) {
        this.serverId = serverId;
    }

}
