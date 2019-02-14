package raknetserver.packet;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

public interface Packet {

    ByteBuf createData(ByteBufAllocator alloc);

}
