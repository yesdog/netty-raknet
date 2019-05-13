package network.ycc.raknet.utils;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;

public class EmptyInit extends ChannelInitializer {
    protected void initChannel(Channel ch) throws Exception { }
}
