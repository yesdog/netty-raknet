package raknetserver;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public abstract class UserHandler extends SimpleChannelInboundHandler<ByteBuf> {

	public static final String PIPELINE_NAME = "userHandler";

	private Channel channel;

	@Override
	public final void channelActive(ChannelHandlerContext ctx) throws Exception {
		super.channelActive(ctx);
		channel = ctx.channel();
	}

	@Override
	public final void exceptionCaught(ChannelHandlerContext ctx, Throwable t) throws Exception {
		handleException(t);
	}

	@Override
	protected final void channelRead0(ChannelHandlerContext ctx, ByteBuf data) throws Exception {
		handleData(data);
	}

	protected final void writeData(ByteBuf data) {
		channel.writeAndFlush(data);
	}

	protected final void writeDataAndClose(ByteBuf data) {
		channel.writeAndFlush(data).addListener(ChannelFutureListener.CLOSE);
	}

	protected final void close() {
		channel.close();
	}

	public abstract void handleException(Throwable t);

	public abstract void handleData(ByteBuf data);

	public abstract String getPingInfo();

	public static interface Factory {
		public UserHandler create();
	}

}
