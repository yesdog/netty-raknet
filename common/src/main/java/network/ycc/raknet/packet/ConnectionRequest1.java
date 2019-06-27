package network.ycc.raknet.packet;

import network.ycc.raknet.RakNet;
import network.ycc.raknet.config.DefaultMagic;

import io.netty.buffer.ByteBuf;

public class ConnectionRequest1 extends SimplePacket implements Packet {

    private RakNet.Magic magic;
    private int protocolVersion;
    private int mtu;

    public ConnectionRequest1() {
    }

    public ConnectionRequest1(RakNet.Magic magic, int protocolVersion, int mtu) {
        this.magic = magic;
        this.protocolVersion = protocolVersion;
        this.mtu = mtu;
    }

    public void encode(ByteBuf buf) {
        magic.write(buf);
        buf.writeByte(protocolVersion);
        buf.ensureWritable(mtu);
        buf.writerIndex(buf.writerIndex() + mtu);
    }

    public void decode(ByteBuf buf) {
        magic = DefaultMagic.decode(buf);
        protocolVersion = buf.readByte();
        mtu = buf.readableBytes();
        buf.skipBytes(mtu);
    }

    public RakNet.Magic getMagic() {
        return magic;
    }

    public void setMagic(RakNet.Magic magic) {
        this.magic = magic;
    }

    public int getProtocolVersion() {
        return protocolVersion;
    }

    public void setProtocolVersion(int protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    public int getMtu() {
        return mtu;
    }

    public void setMtu(int mtu) {
        this.mtu = mtu;
    }

}
