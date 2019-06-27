package network.ycc.raknet.packet;

import network.ycc.raknet.RakNet;
import network.ycc.raknet.config.DefaultMagic;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;

public class InvalidVersion extends SimplePacket implements Packet {

    private RakNet.Magic magic;
    private int version;
    private long serverId;
    public InvalidVersion() {

    }

    public InvalidVersion(RakNet.Magic magic, long serverId) {
        this.magic = magic;
        this.serverId = serverId;
    }

    public void encode(ByteBuf buf) {
        buf.writeByte(version);
        magic.write(buf);
        buf.writeLong(serverId);
    }

    public void decode(ByteBuf buf) {
        version = buf.readUnsignedByte();
        magic = DefaultMagic.decode(buf);
        serverId = buf.readLong();
    }

    public RakNet.Magic getMagic() {
        return magic;
    }

    public void setMagic(RakNet.Magic magic) {
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

    public static class InvalidVersionException extends DecoderException {
        public static final long serialVersionUID = 590681756L;

        public InvalidVersionException() {
            super("Incorrect RakNet version");
        }
    }

}
