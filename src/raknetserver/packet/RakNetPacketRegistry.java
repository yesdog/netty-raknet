package raknetserver.packet;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;

import raknetserver.packet.impl.RakNetAlreadyConnected;
import raknetserver.packet.impl.RakNetConnectionReply1;
import raknetserver.packet.impl.RakNetConnectionReply2;
import raknetserver.packet.impl.RakNetConnectionRequest1;
import raknetserver.packet.impl.RakNetConnectionRequest2;
import raknetserver.packet.impl.RakNetEncapsulatedData;
import raknetserver.packet.impl.RakNetInvalidVersion;
import raknetserver.packet.impl.RakNetUnconnectedPing;
import raknetserver.packet.impl.RakNetUnconnectedPong;
import raknetserver.packet.impl.RakNetReliability.RakNetACK;
import raknetserver.packet.impl.RakNetReliability.RakNetNACK;

public class RakNetPacketRegistry {

	@SuppressWarnings("unchecked")
	private static final Constructor<? extends RakNetPacket>[] idToPacket = new Constructor[2 << Byte.SIZE];
	private static final HashMap<Class<? extends RakNetPacket>, Integer> packetToId = new HashMap<>();

	private static final void registerClientBound(int packetId, Class<? extends RakNetPacket> packetClass) {
		packetToId.put(packetClass, packetId);
		try {
			idToPacket[packetId] = packetClass.getConstructor();
		} catch (NoSuchMethodException | SecurityException e) {
		}
	}

	static {
		registerClientBound(RakNetConstants.ID_RN_UNCONNECTED_PING, RakNetUnconnectedPing.class);
		registerClientBound(RakNetConstants.ID_RN_UNCONNECTED_PONG, RakNetUnconnectedPong.class);
		registerClientBound(RakNetConstants.ID_RN_OPEN_CONNECTION_REQUEST_1, RakNetConnectionRequest1.class);
		registerClientBound(RakNetConstants.ID_RN_OPEN_CONNECTION_REPLY_1, RakNetConnectionReply1.class);
		registerClientBound(RakNetConstants.ID_RN_INVALID_VERSION, RakNetInvalidVersion.class);
		registerClientBound(RakNetConstants.ID_RN_ALREADY_CONNECTED, RakNetAlreadyConnected.class);
		registerClientBound(RakNetConstants.ID_RN_OPEN_CONNECTION_REQUEST_2, RakNetConnectionRequest2.class);
		registerClientBound(RakNetConstants.ID_RN_OPEN_CONNECTION_REPLY_2, RakNetConnectionReply2.class);
		registerClientBound(RakNetConstants.ID_RN_ACK, RakNetACK.class);
		registerClientBound(RakNetConstants.ID_RN_NACK, RakNetNACK.class);
		for (int i = 0x80; i <= 0x8f; i++) {
			registerClientBound(i, RakNetEncapsulatedData.class);
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
