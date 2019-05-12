package network.ycc.raknet.pipeline;

import java.util.List;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;

import network.ycc.raknet.RakNet;
import network.ycc.raknet.packet.FramedPacket;
import network.ycc.raknet.frame.FrameData;
import network.ycc.raknet.utils.UINT;
import network.ycc.raknet.frame.Frame;

public class FrameOrderOut extends MessageToMessageEncoder<FramedPacket> {

    public static final String NAME = "rn-order-out";

    protected int[] nextOrderIndex = new int[8];
    protected int[] nextSequenceIndex = new int[8];

    protected void encode(ChannelHandlerContext ctx, FramedPacket packet, List<Object> list) {
        final RakNet.Config config = RakNet.config(ctx);
        final FrameData data = config.getCodec().encode(packet, ctx.alloc());
        try {
            if (data.getReliability().isOrdered) {
                final int channel = data.getOrderChannel();
                final int sequenceIndex = data.getReliability().isSequenced ? getNextSequenceIndex(channel) : 0;
                list.add(Frame.createOrdered(data, getNextOrderIndex(channel), sequenceIndex));
            } else {
                list.add(Frame.create(data));
            }
        } finally {
            data.release();
        }
    }

    protected int getNextOrderIndex(int channel) {
        final int orderIndex = nextOrderIndex[channel];
        nextOrderIndex[channel] = UINT.B3.plus(nextOrderIndex[channel], 1);
        return orderIndex;
    }

    protected int getNextSequenceIndex(int channel) {
        final int sequenceIndex = nextSequenceIndex[channel];
        nextSequenceIndex[channel] = UINT.B3.plus(nextSequenceIndex[channel], 1);
        return sequenceIndex;
    }

}
