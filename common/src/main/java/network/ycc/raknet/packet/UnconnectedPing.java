package network.ycc.raknet.packet;

import io.netty.buffer.ByteBuf;

import network.ycc.raknet.RakNet;
import network.ycc.raknet.config.DefaultMagic;

public class UnconnectedPing extends SimplePacket implements Packet {

    private RakNet.Magic magic;
    private long clientTime;
    private long clientId;

    public void decode(ByteBuf buf) {
        clientTime = buf.readLong();
        magic = DefaultMagic.decode(buf);
        clientId = buf.readLong();
    }

    public void encode(ByteBuf buf) {
        buf.writeLong(clientTime);
        magic.write(buf);
        buf.writeLong(clientId);
    }

    public RakNet.Magic getMagic() {
        return magic;
    }

    public void setMagic(RakNet.Magic magic) {
        this.magic = magic;
    }

    public long getClientTime() {
        return clientTime;
    }

    public void setClientTime(long clientTime) {
        this.clientTime = clientTime;
    }

    public long getClientId() {
        return clientId;
    }

    public void setClientId(long clientId) {
        this.clientId = clientId;
    }

}
