package network.ycc.raknet.packet;

import io.netty.buffer.ByteBuf;

public class ConnectionRequest extends SimpleFramedPacket {

    protected long clientId;
    protected long timestamp;

    public ConnectionRequest() {
        reliability = Reliability.RELIABLE;
    }

    public ConnectionRequest(long clientId) {
        this();
        this.clientId = clientId;
        this.timestamp = System.nanoTime();
    }

    @Override
    public void decode(ByteBuf buf) {
        clientId = buf.readLong(); //client id
        timestamp = buf.readLong();
        buf.readBoolean(); //use security
    }

    @Override
    public void encode(ByteBuf buf) {
        buf.writeLong(clientId);
        buf.writeLong(timestamp);
        buf.writeBoolean(false);
    }

    public long getClientId() {
        return clientId;
    }

    public void setClientId(long clientId) {
        this.clientId = clientId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

}
