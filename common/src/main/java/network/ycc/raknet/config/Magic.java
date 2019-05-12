package network.ycc.raknet.config;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;

public interface Magic {

    class MagicDecodeException extends DecoderException {
        public static final long serialVersionUID = 590681756L;

        protected MagicDecodeException() {
            super("Incorrect RakNet magic value");
        }
    }

    class Simple implements Magic {
        final protected byte[] magicData;

        public Simple(byte[] magicData) {
            this.magicData = magicData;
        }

        public void write(ByteBuf buf) {
            buf.writeBytes(magicData);
        }

        public void read(ByteBuf buf) {
            for (byte b : magicData) {
                if (buf.readByte() != b) {
                    throw new MagicDecodeException();
                }
            }
        }
    }

    static Magic decode(ByteBuf buf) {
        final byte[] magicData = new byte[16];
        buf.readBytes(magicData);
        return new Simple(magicData);
    }

    void write(ByteBuf buf);
    void read(ByteBuf buf);

    default void verify(Magic other) {
        final ByteBuf tmp = Unpooled.buffer(16);
        write(tmp);
        other.read(tmp);
    }

}
