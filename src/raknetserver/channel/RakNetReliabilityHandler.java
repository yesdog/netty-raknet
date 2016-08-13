package raknetserver.channel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.concurrent.ScheduledFuture;
import raknetserver.packet.EncapsulatedPacket;
import raknetserver.packet.impl.RakNetReliability.REntry;
import raknetserver.packet.impl.RakNetReliability.RakNetACK;
import raknetserver.packet.impl.RakNetReliability.RakNetNACK;
import raknetserver.packet.impl.RakNetEncapsulatedData;

public class RakNetReliabilityHandler extends ChannelDuplexHandler {

	protected int lastReceivedACK = -1;
	protected final HashMap<Integer, RakNetEncapsulatedData> sentPackets = new HashMap<>();

	private ScheduledFuture<?> task;

	@Override
	public void channelActive(final ChannelHandlerContext ctx) throws Exception {
		super.channelInactive(ctx);
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
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		super.channelInactive(ctx);
		task.cancel(false);
	}

	private int lastReceivedSeqId = -1;

	//TODO: limit how much packets can be missed and how much ids reliability packet can contain
	@Override
	public void channelRead(ChannelHandlerContext ctx, Object obj) throws Exception {
		if (obj instanceof RakNetEncapsulatedData) {
			RakNetEncapsulatedData edata = (RakNetEncapsulatedData) obj;
			//check for missing packets
			int packetSeqId = edata.getSeqId();
			int prevSeqId = lastReceivedSeqId;
			lastReceivedSeqId = packetSeqId;
			//if id is after last received, which means that we don't have any missing packets, send ACK for it
			if (packetSeqId - prevSeqId == 1) {
				ctx.writeAndFlush(new RakNetACK(packetSeqId));
			} else
			//id is not the after last received, which means we have missing packets, send NACK for missing ones
			if (packetSeqId - prevSeqId > 1) {
				ctx.writeAndFlush(new RakNetNACK(prevSeqId + 1, packetSeqId - 1));
			}
			//id is before last received, which means that it is a duplicate packet, ignore it
			else {
				return;
			}
			//fire channel read for encapsulated packets
			ctx.fireChannelRead(edata.getPackets());
		} else if (obj instanceof RakNetACK) {
			for (REntry entry : ((RakNetACK) obj).getEntries()) {
				confirmRakNetPackets(entry.idstart, entry.idfinish);
			}
		} else if (obj instanceof RakNetNACK) {
			for (REntry entry : ((RakNetNACK) obj).getEntries()) {
				resendRakNetPackets(ctx, entry.idstart, entry.idfinish);
			}			
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public void write(ChannelHandlerContext ctx, Object data, ChannelPromise pr) throws Exception {
		Collection<EncapsulatedPacket> epackets = (Collection<EncapsulatedPacket>) data;
		for (EncapsulatedPacket epacket : epackets) {
			sendRakNetPacket(ctx, new RakNetEncapsulatedData(epacket));
		}
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

	protected void sendRakNetPacket(ChannelHandlerContext ctx, RakNetEncapsulatedData rpacket) {
		rpacket.setSeqId(getNextRakSeqID());
		sentPackets.put(rpacket.getSeqId(), rpacket);
		ctx.writeAndFlush(rpacket);
	}

	private int currentRakSeqID = 0;
	private int getNextRakSeqID() {
		return currentRakSeqID++ % 16777216;
	}

}
