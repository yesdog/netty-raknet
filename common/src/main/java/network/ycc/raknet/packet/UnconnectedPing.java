package network.ycc.raknet.packet;

import io.netty.buffer.ByteBuf;

import network.ycc.raknet.config.Magic;

public class UnconnectedPing extends SimplePacket implements Packet {

    private Magic magic;
    private long clientTime;
    private long clientId;

    public void decode(ByteBuf buf) {
        clientTime = buf.readLong();
        magic = Magic.decode(buf);
        clientId = buf.readLong();
    }

    public void encode(ByteBuf buf) {
        buf.writeLong(clientTime);
        magic.write(buf);
        buf.writeLong(clientId);
    }

    public Magic getMagic() {
        return magic;
    }

    public void setMagic(Magic magic) {
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
