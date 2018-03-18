package raknetserver.packet.internal;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;

import raknetserver.packet.RakNetConstants;

public class InternalPacketRegistry {

	@SuppressWarnings("unchecked")
	private static final Constructor<? extends InternalPacket>[] idToPacket = new Constructor[2 << Byte.SIZE];
	private static final HashMap<Class<? extends InternalPacket>, Integer> packetToId = new HashMap<>();

	protected static final void register(int packetId, Class<? extends InternalPacket> packetClass) {
		packetToId.put(packetClass, packetId);
		try {
			idToPacket[packetId] = packetClass.getConstructor();
		} catch (NoSuchMethodException | SecurityException e) {
		}
	}

	static {
		register(RakNetConstants.ID_I_CONNECTION_REQUEST, InternalConnectionRequest.class);
		register(RakNetConstants.ID_I_SERVER_HANDSHAKE, InternalServerHandshake.class);
		register(RakNetConstants.ID_I_CLIENT_HANDSHAKE, InternalClientHandshake.class);
		register(RakNetConstants.ID_I_CLIENT_DISCONNECT, InternalDisconnect.class);
		register(RakNetConstants.ID_I_PING, InternalPing.class);
		register(RakNetConstants.ID_I_PONG, InternalPong.class);
	}

	public static int getId(InternalPacket packet) {
		Integer packetId = packetToId.get(packet.getClass());
		if (packetId == null) {
			throw new IllegalArgumentException("internal packet class " + packet.getClass().getName() + " is not registered");
		}
		return packetId;
	}

	public static InternalPacket getPacket(int id) {
		Constructor<? extends InternalPacket> constr = idToPacket[id];
		if (constr == null) {
			throw new IllegalArgumentException(id + " is not a known(registered) RakNet internal packet");
		}
		try {
			return constr.newInstance();
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			throw new RuntimeException("Unable to construct new packet instance", e);
		}
	}

}
