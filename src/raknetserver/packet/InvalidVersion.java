package raknetserver.packet;

import io.netty.buffer.ByteBuf;

import raknetserver.utils.Constants;

public class InvalidVersion extends SimplePacket implements Packet {

    public static final int VALID_VERSION = 9;

    private final long serverId;

    public InvalidVersion(long serverId) {
        this.serverId = serverId;
    }

    @Override
    public void decode(ByteBuf buf) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void encode(ByteBuf buf) {
        buf.writeByte(VALID_VERSION);
        buf.writeBytes(Constants.MAGIC);
        buf.writeLong(serverId);
    }

}
