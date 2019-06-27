package network.ycc.raknet.config;

import network.ycc.raknet.RakNet;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class DefaultMagic implements RakNet.Magic {

    //TODO: static cache?

    protected final byte[] magicData;

    public DefaultMagic(byte[] magicData) {
        this.magicData = magicData;
    }

    public static DefaultMagic decode(ByteBuf buf) {
        final byte[] magicData = new byte[16];
        buf.readBytes(magicData);
        return new DefaultMagic(magicData);
    }

    public void write(ByteBuf buf) {
        buf.writeBytes(magicData);
    }

    public void read(ByteBuf buf) {
        for (byte b : magicData) {
            if (buf.readByte() != b) {
                throw new MagicMismatchException();
            }
        }
    }

    public void verify(RakNet.Magic other) {
        final ByteBuf tmp = Unpooled.buffer(16);
        write(tmp);
        other.read(tmp);
    }

}
