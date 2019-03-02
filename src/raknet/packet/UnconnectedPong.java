package raknet.packet;

import io.netty.buffer.ByteBuf;

import raknet.utils.DataSerializer;

public class UnconnectedPong extends SimplePacket implements Packet {

    private long clientTime = 0L;
    private long serverId = 0L;
    private String info = "";

    public UnconnectedPong(long clientTime, long serverId, String info) {
        this.clientTime = clientTime;
        this.serverId = serverId;
        this.info = info;
    }

    public UnconnectedPong() {
        
    }

    public void decode(ByteBuf buf) {
        clientTime = buf.readLong();
        serverId = buf.readLong();
        DataSerializer.readMagic(buf);
        info = DataSerializer.readString(buf);
    }

    public void encode(ByteBuf buf) {
        buf.writeLong(clientTime);
        buf.writeLong(serverId);
        DataSerializer.writeMagic(buf);
        DataSerializer.writeString(buf, info);
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

    public String getInfo() {
        return info;
    }

    public void setInfo(String info) {
        this.info = info;
    }

}
