package raknetserver.utils;

import io.netty.channel.ChannelHandlerContext;

public interface PacketHandler<TManager, TPacket> {

	public void handlePacket(ChannelHandlerContext ctx, TManager t, TPacket packet);

}
