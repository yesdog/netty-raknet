package network.ycc.raknet.pipeline;

import java.util.List;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;

import network.ycc.raknet.packet.FrameSet;
import network.ycc.raknet.utils.UINT;
import network.ycc.raknet.RakNet;
import network.ycc.raknet.frame.Frame;

public class FrameSplitter extends MessageToMessageEncoder<Frame> {

    public static final String NAME = "rn-split";

    protected int nextSplitId = 0;
    protected int nextReliableId = 0;

    @Override
    protected void encode(ChannelHandlerContext ctx, Frame packet, List<Object> list) {
        final RakNet.Config config = RakNet.config(ctx);
        final int maxSize = config.getMTU() - 2 * (FrameSet.HEADER_SIZE + Frame.HEADER_SIZE);
        if (packet.getRoughPacketSize() > maxSize) {
            final int splits = packet.fragment(getNextSplitID(), maxSize, nextReliableId, list);
            nextReliableId = UINT.B3.plus(nextReliableId, splits);
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
