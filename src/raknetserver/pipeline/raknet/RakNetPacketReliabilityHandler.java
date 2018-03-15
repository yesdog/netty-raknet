package raknetserver.pipeline.raknet;

import java.util.HashMap;
import java.util.List;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.MessageToMessageCodec;
import raknetserver.packet.EncapsulatedPacket;
import raknetserver.packet.raknet.RakNetEncapsulatedData;
import raknetserver.packet.raknet.RakNetPacket;
import raknetserver.packet.raknet.RakNetReliability.REntry;
import raknetserver.packet.raknet.RakNetReliability.RakNetACK;
import raknetserver.packet.raknet.RakNetReliability.RakNetNACK;
import raknetserver.utils.Constants;

//TODO: figure out if seq numbers can wrap
public class RakNetPacketReliabilityHandler extends MessageToMessageCodec<RakNetPacket, EncapsulatedPacket> {

	protected final HashMap<Integer, RakNetEncapsulatedData> sentPackets = new HashMap<>();
	protected int lastReceivedSeqId = -1;

	@Override
	protected void decode(ChannelHandlerContext ctx, RakNetPacket packet, List<Object> list) throws Exception {
		if (packet instanceof RakNetEncapsulatedData) {
			RakNetEncapsulatedData edata = (RakNetEncapsulatedData) packet;
			//check for missing packets
			int packetSeqId = edata.getSeqId();
			int prevSeqId = lastReceivedSeqId;
			lastReceivedSeqId = packetSeqId;
			//if id is after last received, which means that we don't have any missing packets, send ACK for it
			if ((packetSeqId - prevSeqId) == 1) {
				ctx.writeAndFlush(new RakNetACK(packetSeqId));
			} else
			//id is not the after last received, which means we have missing packets, send NACK for missing ones
			if ((packetSeqId - prevSeqId) > 1) {
				ctx.writeAndFlush(new RakNetNACK(prevSeqId + 1, packetSeqId - 1));
			}
			//id is before last received, which means that it is a duplicate packet, ignore it
			else {
				return;
			}
			//add encapsulated packet
			list.addAll(edata.getPackets());
		} else if (packet instanceof RakNetACK) {
			for (REntry entry : ((RakNetACK) packet).getEntries()) {
				confirmRakNetPackets(entry.idstart, entry.idfinish);
			}
		} else if (packet instanceof RakNetNACK) {
			for (REntry entry : ((RakNetNACK) packet).getEntries()) {
				resendRakNetPackets(ctx, entry.idstart, entry.idfinish);
			}
		}
	}

	@Override
	protected void encode(ChannelHandlerContext ctx, EncapsulatedPacket packet, List<Object> list) throws Exception {
		RakNetEncapsulatedData rpacket = new RakNetEncapsulatedData(packet);
		initRakNetPacket(rpacket);
		list.add(rpacket);
	}

	private void confirmRakNetPackets(int idstart, int idfinish) {
		if ((idfinish - idstart) > Constants.MAX_PACKET_LOSS) {
			throw new DecoderException("Too big packet loss (ack confirm range)");
		}
		for (int id = idstart; id <= idfinish; id++) {
			sentPackets.remove(id);
		}
	}

	private void resendRakNetPackets(ChannelHandlerContext ctx, int idstart, int idfinish) {
		if ((idfinish - idstart) > Constants.MAX_PACKET_LOSS) {
			throw new DecoderException("Too big packet loss (nack resend range)");
		}
		for (int id = idstart; id <= idfinish; id++) {
			RakNetEncapsulatedData packet = sentPackets.remove(id);
			if (packet != null) {
				sendRakNetPacket(ctx, packet);
			}
		}
	}

	protected void initRakNetPacket(RakNetEncapsulatedData rpacket) {
		rpacket.setSeqId(getNextRakSeqID());
		if (sentPackets.size() > Constants.MAX_PACKET_LOSS) {
			throw new DecoderException("Too big packet loss (unconfirmed sent packets)");
		}
		sentPackets.put(rpacket.getSeqId(), rpacket);
	}

	protected void sendRakNetPacket(ChannelHandlerContext ctx, RakNetEncapsulatedData rpacket) {
		initRakNetPacket(rpacket);
		ctx.writeAndFlush(rpacket);
	}

	protected int currentRakSeqID = 0;
	protected int getNextRakSeqID() {
		if (currentRakSeqID >= 16777216) {
			throw new IllegalStateException("Rak seq id reached max");
		}
		return currentRakSeqID++;
	}

}
