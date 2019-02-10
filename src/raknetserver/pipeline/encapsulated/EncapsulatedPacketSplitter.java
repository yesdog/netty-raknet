package raknetserver.pipeline.encapsulated;

import java.util.List;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.util.ReferenceCountUtil;
import raknetserver.packet.EncapsulatedPacket;
import raknetserver.RakNetServer;
import raknetserver.utils.UINT;

public class EncapsulatedPacketSplitter extends MessageToMessageEncoder<EncapsulatedPacket> {

	protected int nextSplitId = 0;
	protected int nextReliableId = 0;
	protected int nextSequenceId = 0;

	@Override
	protected void encode(ChannelHandlerContext ctx, EncapsulatedPacket packet, List<Object> list) {
		final int mtu = ctx.channel().attr(RakNetServer.MTU).get();
		if (packet.getReliability().isSequenced) {
		    packet.setSequenceIndex(getNextSequenceId());
        }
		//TODO: real MTU values?
		if (packet.getRoughPacketSize() > mtu - 100) {
			try {
				final int splitSize = mtu - 200;
				final int splits = packet.fragment(ctx.alloc(), list, getNextSplitID(), splitSize, nextReliableId);
				nextReliableId = UINT.B3.plus(nextReliableId, splits);
			} catch (Throwable t) {
				list.forEach(ReferenceCountUtil::release);
				list.clear();
				throw t;
			}
		} else {
			if (packet.getReliability().isReliable) {
				packet.setReliableIndex(getNextReliableId());
			}
			list.add(packet.retain());
		}
	}

	protected int getNextSplitID() {
		final int splitId = nextSplitId;
		nextSplitId = UINT.B2.plus(nextSplitId, 1);
		return splitId;
	}

	protected int getNextReliableId() {
		final int reliableIndex = nextReliableId;
		nextReliableId = UINT.B3.plus(nextReliableId, 1);
		return reliableIndex;
	}

    protected int getNextSequenceId() {
        final int sequenceIndex = nextSequenceId;
        nextSequenceId = UINT.B3.plus(nextSequenceId, 1);
        return sequenceIndex;
    }

}
