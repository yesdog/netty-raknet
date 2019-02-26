package raknetserver.packet;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

public interface Packet {

    void write(ByteBuf out);

    default ByteBuf createData(ByteBufAllocator alloc) {
        final ByteBuf out = alloc.ioBuffer();
        write(out);
        return out;
    }

}
