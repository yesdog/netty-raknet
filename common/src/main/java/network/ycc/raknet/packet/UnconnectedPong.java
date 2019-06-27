package network.ycc.raknet.packet;

import network.ycc.raknet.RakNet;
import network.ycc.raknet.config.DefaultMagic;

import io.netty.buffer.ByteBuf;

public class UnconnectedPong extends SimplePacket implements Packet {

    private long clientTime = 0L;
    private long serverId = 0L;
    private RakNet.Magic magic;
    private String info = "";

    public UnconnectedPong() {
    }

    public UnconnectedPong(long clientTime, long serverId, RakNet.Magic magic, String info) {
        this.clientTime = clientTime;
        this.serverId = serverId;
        this.magic = magic;
        this.info = info;
    }

    public void encode(ByteBuf buf) {
        buf.writeLong(clientTime);
        buf.writeLong(serverId);
        magic.write(buf);
        writeString(buf, info);
    }

    public void decode(ByteBuf buf) {
        clientTime = buf.readLong();
        serverId = buf.readLong();
        magic = DefaultMagic.decode(buf);
        info = readString(buf);
    }

    public long getClientTime() {
        return clientTime;
    }

    public void setClientTime(long clientTime) {
        this.clientTime = clientTime;
    }

    public long getServerId() {
        return serverId;
    }

    public void setServerId(long serverId) {
        this.serverId = serverId;
    }

    public RakNet.Magic getMagic() {
        return magic;
    }

    public void setMagic(RakNet.Magic magic) {
        this.magic = magic;
    }

    public String getInfo() {
        return info;
    }

    public void setInfo(String info) {
        this.info = info;
    }

}
