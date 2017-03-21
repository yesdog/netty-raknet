package raknetserver.packet.raknet;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;

import raknetserver.packet.RakNetConstants;
import raknetserver.packet.raknet.RakNetReliability.RakNetACK;
import raknetserver.packet.raknet.RakNetReliability.RakNetNACK;

public class RakNetPacketRegistry {

	@SuppressWarnings("unchecked")
	private static final Constructor<? extends RakNetPacket>[] idToPacket = new Constructor[2 << Byte.SIZE];
	private static final HashMap<Class<? extends RakNetPacket>, Integer> packetToId = new HashMap<>();

	private static final void register(int packetId, Class<? extends RakNetPacket> packetClass) {
		packetToId.put(packetClass, packetId);
		try {
			idToPacket[packetId] = packetClass.getConstructor();
		} catch (NoSuchMethodException | SecurityException e) {
		}
	}

	static {
		register(RakNetConstants.ID_RN_UNCONNECTED_PING, RakNetUnconnectedPing.class);
		register(RakNetConstants.ID_RN_UNCONNECTED_PONG, RakNetUnconnectedPong.class);
		register(RakNetConstants.ID_RN_OPEN_CONNECTION_REQUEST_1, RakNetConnectionRequest1.class);
		register(RakNetConstants.ID_RN_OPEN_CONNECTION_REPLY_1, RakNetConnectionReply1.class);
		register(RakNetConstants.ID_RN_INVALID_VERSION, RakNetInvalidVersion.class);
		register(RakNetConstants.ID_RN_CONNECTION_FAILED, RakNetConnectionFailed.class);
		register(RakNetConstants.ID_RN_OPEN_CONNECTION_REQUEST_2, RakNetConnectionRequest2.class);
		register(RakNetConstants.ID_RN_OPEN_CONNECTION_REPLY_2, RakNetConnectionReply2.class);
		register(RakNetConstants.ID_RN_ACK, RakNetACK.class);
		register(RakNetConstants.ID_RN_NACK, RakNetNACK.class);
		for (int i = 0x80; i <= 0x8f; i++) {
			register(i, RakNetEncapsulatedData.class);
		}
	}

	public static int getId(RakNetPacket packet) {
		Integer packetId = packetToId.get(packet.getClass());
		if (packetId == null) {
			throw new IllegalArgumentException("RakNet packet class " + packet.getClass().getName() + " is not registered");
		}
		return packetId;
	}

	public static RakNetPacket getPacket(int id) {
		Constructor<? extends RakNetPacket> constr = idToPacket[id];
		if (constr == null) {
			throw new IllegalArgumentException(id + " is not a known(registered) RakNet packet");
		}
		try {
			return constr.newInstance();
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			throw new RuntimeException("Unable to construct new packet instance", e);
		}
	}

}
