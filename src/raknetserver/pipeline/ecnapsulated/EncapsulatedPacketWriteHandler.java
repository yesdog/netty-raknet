package raknetserver.pipeline.ecnapsulated;

import java.util.Arrays;
import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import raknetserver.packet.EncapsulatedPacket;
import raknetserver.packet.RakNetConstants;
import raknetserver.utils.Utils;

public class EncapsulatedPacketWriteHandler extends MessageToMessageEncoder<ByteBuf> {

	@Override
	protected void encode(ChannelHandlerContext ctx, ByteBuf buffer, List<Object> list) throws Exception {
		int mtu = ctx.channel().attr(RakNetConstants.MTU).get();
		int splitSize = mtu - 200;
		int orderIndex = getNextOrderIndex();
		if (buffer.readableBytes() > (mtu - 100)) {
			EncapsulatedPacket[] epackets = new EncapsulatedPacket[Utils.getSplitCount(buffer.readableBytes(), splitSize)];
			int splitID = getNextSplitID();
			for (int splitIndex = 0; splitIndex < epackets.length; splitIndex++) {
				epackets[splitIndex] = new EncapsulatedPacket(
					buffer.readBytes(buffer.readableBytes() < splitSize ? buffer.readableBytes() : splitSize),
					getNextMessageIndex(), orderIndex,
					splitID, epackets.length, splitIndex
				);
			}
			list.addAll(Arrays.asList(epackets));
		} else {
			list.add(new EncapsulatedPacket(buffer, getNextMessageIndex(), orderIndex));
		}
	}

	private int currentSplitID = 0;
	private int getNextSplitID() {
		return currentSplitID++ % Short.MAX_VALUE;
	}

	private int currentMessageIndex = 0;
	private int getNextMessageIndex() {
		return currentMessageIndex++ % 16777216;
	}

	private int currentOrderIndex = 0;
	private int getNextOrderIndex() {
		return currentOrderIndex++ % 16777216;
	}

}
