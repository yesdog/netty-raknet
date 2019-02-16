package raknetserver.pipeline;

import java.util.List;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.util.ReferenceCountUtil;
import raknetserver.frame.Frame;
import raknetserver.RakNetServer;
import raknetserver.packet.FrameSet;
import raknetserver.utils.UINT;

public class FrameSplitter extends MessageToMessageEncoder<Frame> {

    public static final String NAME = "rn-split";

	protected int nextSplitId = 0;
	protected int nextReliableId = 0;

	@Override
	protected void encode(ChannelHandlerContext ctx, Frame packet, List<Object> list) {
		final int mtu = ctx.channel().attr(RakNetServer.MTU).get();
		final int maxSize = mtu - (2 * FrameSet.HEADER_SIZE + 2 * Frame.HEADER_SIZE);
		if (packet.getRoughPacketSize() > maxSize) {
			try {
				final int splits = packet.fragment(getNextSplitID(), maxSize, nextReliableId, list);
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

}
