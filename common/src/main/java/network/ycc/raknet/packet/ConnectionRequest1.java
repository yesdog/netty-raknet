package network.ycc.raknet.packet;

import io.netty.buffer.ByteBuf;

import network.ycc.raknet.utils.DataSerializer;

public class ConnectionRequest1 extends SimplePacket implements Packet {

    private int rakNetProtocolVersion;
    private int mtu;

    public ConnectionRequest1() {}

    public ConnectionRequest1(int rakNetProtocolVersion, int mtu) {
        this.rakNetProtocolVersion = rakNetProtocolVersion;
        this.mtu = mtu;
    }

    public void decode(ByteBuf buf) {
        DataSerializer.readMagic(buf);
        rakNetProtocolVersion = buf.readByte();
        mtu = buf.readableBytes();
        buf.skipBytes(mtu);
    }

    public void encode(ByteBuf buf) {
        DataSerializer.writeMagic(buf);
        buf.writeByte(rakNetProtocolVersion);
        buf.ensureWritable(mtu);
        buf.writerIndex(buf.writerIndex() + mtu);
    }

    public void setRakNetProtocolVersion(int rakNetProtocolVersion) {
        this.rakNetProtocolVersion = rakNetProtocolVersion;
    }

    public void setMtu(int mtu) {
        this.mtu = mtu;
    }

    public int getRakNetProtocolVersion() {
        return rakNetProtocolVersion;
    }

    public int getMtu() {
        return mtu;
    }

}
