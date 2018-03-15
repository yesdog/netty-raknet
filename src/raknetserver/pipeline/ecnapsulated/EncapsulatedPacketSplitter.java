package raknetserver.pipeline.ecnapsulated;

import java.util.Arrays;
import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
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
			//TODO: direct array split
			ByteBuf buffer = Unpooled.wrappedBuffer(packet.getData());
			for (int splitIndex = 0; splitIndex < epackets.length; splitIndex++) {
				epackets[splitIndex] = new EncapsulatedPacket(
					Utils.readBytes(buffer, buffer.readableBytes() < splitSize ? buffer.readableBytes() : splitSize),
					getNextMessageIndex(), packet.getOrderChannel(), packet.getOrderIndex(),
					splitID, epackets.length, splitIndex
				);
			}
			list.addAll(Arrays.asList(epackets));
		} else {
			list.add(new EncapsulatedPacket(packet.getData(), getNextMessageIndex(), packet.getOrderChannel(), packet.getOrderIndex()));
		}
	}

	protected int currentMessageIndex = 0;
	protected int getNextMessageIndex() {
		return currentMessageIndex++ % 16777216;
	}

	protected int currentSplitID = 0;
	protected int getNextSplitID() {
		return currentSplitID++ % Short.MAX_VALUE;
	}

}
