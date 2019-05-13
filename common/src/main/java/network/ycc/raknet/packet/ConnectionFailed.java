package network.ycc.raknet.packet;

import io.netty.buffer.ByteBuf;

import network.ycc.raknet.RakNet;
import network.ycc.raknet.config.DefaultMagic;

public class ConnectionFailed extends SimplePacket implements Packet {

    protected RakNet.Magic magic;
    protected long code = 0;

    public ConnectionFailed() {}

    public ConnectionFailed(RakNet.Magic magic) {
        this.magic = magic;
    }

    @Override
    public void decode(ByteBuf buf) {
        magic = DefaultMagic.decode(buf);
        code = buf.readLong();
    }

    @Override
    public void encode(ByteBuf buf) {
        magic.write(buf);
        buf.writeLong(code);
    }

    public RakNet.Magic getMagic() {
        return magic;
    }

    public void setMagic(RakNet.Magic magic) {
        this.magic = magic;
    }

    public long getCode() {
        return code;
    }

    public void setCode(long code) {
        this.code = code;
    }

}
