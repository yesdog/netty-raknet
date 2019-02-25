package raknetserver.packet;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

public interface Packet {

    void write(ByteBuf out);
    ByteBuf createData(ByteBufAllocator alloc);

}
