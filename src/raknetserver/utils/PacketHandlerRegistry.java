package raknetserver.utils;

import java.util.HashMap;

import io.netty.channel.ChannelHandlerContext;

public class PacketHandlerRegistry<TManager, TPacket> {

	@SuppressWarnings("rawtypes")
	private final HashMap<Class, PacketHandler> registry = new HashMap<>();

	public <T extends TPacket> void register(Class<T> packet, PacketHandler<TManager, T> handler) {
		registry.put(packet, handler);
	}

	@SuppressWarnings("unchecked")
	public void handle(ChannelHandlerContext ctx, TManager manager, TPacket packet) {
		PacketHandler<TManager, TPacket> handler = registry.get(packet.getClass());
		if (handler == null) {
			throw new IllegalArgumentException("Handler for packet " + packet.getClass() + " not found");
		}
		handler.handlePacket(ctx, manager, packet);
	}

}
