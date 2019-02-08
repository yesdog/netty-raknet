package raknetserver.pipeline.encapsulated;

import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import raknetserver.packet.EncapsulatedPacket;
import raknetserver.packet.RakNetConstants;
import raknetserver.utils.UINT;

public class EncapsulatedPacketSplitter extends MessageToMessageEncoder<EncapsulatedPacket> {

	protected int nextMessageIndex = 0;
	protected int nextSplitId = 0;

	@Override
	protected void encode(ChannelHandlerContext ctx, EncapsulatedPacket packet, List<Object> list) {
		final int mtu = ctx.channel().attr(RakNetConstants.MTU).get();
		//TODO: real values
		if (packet.getDataSize() > mtu - 100) {
			final int splitSize = mtu - 200;
			final int splitCount = (packet.getDataSize() + splitSize - 1) / splitSize; //round up
			final int splitID = getNextSplitID();
			final ByteBuf data = packet.retainedData();
			for (int splitIndex = 0 ; splitIndex < splitCount ; splitIndex++) {
				list.add(new EncapsulatedPacket(
						data.readRetainedSlice(data.readableBytes() < splitSize ? data.readableBytes() : splitSize),
						getNextMessageIndex(), packet.getOrderChannel(), packet.getOrderIndex(),
						splitID, splitCount, splitIndex
				));
			}
			data.release(); //original packet reference
		} else {
			list.add(new EncapsulatedPacket(packet.retainedData(), getNextMessageIndex(), packet.getOrderChannel(), packet.getOrderIndex()));
		}
	}

	protected int getNextMessageIndex() {
		final int messageIndex = nextMessageIndex;
		nextMessageIndex = UINT.B3.plus(nextMessageIndex, 1);
		return messageIndex;
	}

	protected int getNextSplitID() {
		final int splitId = nextSplitId;
		nextSplitId = UINT.B2.plus(nextSplitId, 1);
		return splitId;
	}

}
