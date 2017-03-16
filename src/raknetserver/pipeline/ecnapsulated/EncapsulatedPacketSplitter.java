package raknetserver.pipeline.ecnapsulated;

import java.util.Arrays;
import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import raknetserver.packet.EncapsulatedPacket;
import raknetserver.packet.RakNetConstants;
import raknetserver.utils.Utils;

public class EncapsulatedPacketSplitter extends MessageToMessageEncoder<EncapsulatedPacket> {

	@Override
	protected void encode(ChannelHandlerContext ctx, EncapsulatedPacket packet, List<Object> list) throws Exception {
		int mtu = ctx.channel().attr(RakNetConstants.MTU).get();
		int splitSize = mtu - 200;
		if (packet.getDataSize() > (mtu - 100)) {
			EncapsulatedPacket[] epackets = new EncapsulatedPacket[Utils.getSplitCount(packet.getDataSize(), splitSize)];
			int splitID = getNextSplitID();
			ByteBuf buffer = packet.getData();
			for (int splitIndex = 0; splitIndex < epackets.length; splitIndex++) {
				epackets[splitIndex] = new EncapsulatedPacket(
					buffer.readBytes(buffer.readableBytes() < splitSize ? buffer.readableBytes() : splitSize),
					getNextMessageIndex(), packet.getOrderChannel(), packet.getOrderIndex(),
					splitID, epackets.length, splitIndex
				);
			}
			list.addAll(Arrays.asList(epackets));
		} else {
			list.add(new EncapsulatedPacket(packet.getData(), getNextMessageIndex(), packet.getOrderChannel(), packet.getOrderIndex()));
		}
	}

	private int currentMessageIndex = 0;
	private int getNextMessageIndex() {
		return currentMessageIndex++ % 16777216;
	}

	private int currentSplitID = 0;
	private int getNextSplitID() {
		return currentSplitID++ % Short.MAX_VALUE;
	}

}
