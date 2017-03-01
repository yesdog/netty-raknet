package raknetserver.pipeline.raknet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;
import io.netty.util.concurrent.ScheduledFuture;
import raknetserver.packet.EncapsulatedPacket;
import raknetserver.packet.raknet.RakNetEncapsulatedData;
import raknetserver.packet.raknet.RakNetPacket;
import raknetserver.packet.raknet.RakNetReliability.REntry;
import raknetserver.packet.raknet.RakNetReliability.RakNetACK;
import raknetserver.packet.raknet.RakNetReliability.RakNetNACK;

//TODO: figure out if seq numbers can wrap
public class RakNetPacketReliabilityHandler extends MessageToMessageCodec<RakNetPacket, EncapsulatedPacket> {

	protected int lastReceivedACK = -1;
	protected final HashMap<Integer, RakNetEncapsulatedData> sentPackets = new HashMap<>();

	private ScheduledFuture<?> task;

	@Override
	public void channelActive(final ChannelHandlerContext ctx) throws Exception {
		task = ctx.channel().eventLoop().scheduleAtFixedRate(new Runnable() {
			@Override
			public void run() {
				//resend packets that werent confirmed as received on the other side
				//only packets with seq id less than latest received ack are sent
				ArrayList<RakNetEncapsulatedData> toResend = new ArrayList<>();
				Iterator<Entry<Integer, RakNetEncapsulatedData>> iterator = sentPackets.entrySet().iterator();
				while (iterator.hasNext()) {
					Entry<Integer, RakNetEncapsulatedData> entry = iterator.next();
					RakNetEncapsulatedData packet = entry.getValue();
					if (packet.getSeqId() < lastReceivedACK) {
						iterator.remove();
						toResend.add(packet);
					}
				}
				for (RakNetEncapsulatedData packet : toResend) {
					sendRakNetPacket(ctx, packet);
				}
			}
		}, 100, 50, TimeUnit.MILLISECONDS);
		super.channelActive(ctx);
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		task.cancel(false);
		super.channelInactive(ctx);
	}

	private int lastReceivedSeqId = -1;

	//TODO: limit how much packets can be missed and how much ids reliability packet can contain
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
			list.add(edata.getPackets());
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
		for (int id = idstart; id <= idfinish; id++) {
			sentPackets.remove(id);
		}
		lastReceivedACK = idfinish;
	}

	private void resendRakNetPackets(ChannelHandlerContext ctx, int idstart, int idfinish) {
		for (int id = idstart; id <= idfinish; id++) {
			RakNetEncapsulatedData packet = sentPackets.remove(id);
			if (packet != null) {
				sendRakNetPacket(ctx, packet);
			}
		}
	}

	protected void initRakNetPacket(RakNetEncapsulatedData rpacket) {
		rpacket.setSeqId(getNextRakSeqID());
		sentPackets.put(rpacket.getSeqId(), rpacket);
	}

	protected void sendRakNetPacket(ChannelHandlerContext ctx, RakNetEncapsulatedData rpacket) {
		initRakNetPacket(rpacket);
		ctx.writeAndFlush(rpacket);
	}

	private int currentRakSeqID = 0;
	private int getNextRakSeqID() {
		if (currentRakSeqID >= 16777216) {
			throw new IllegalStateException("Rak seq id reached max");
		}
		return currentRakSeqID++;
	}

}
