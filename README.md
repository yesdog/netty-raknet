# RakNetServer:

RakNet reliable udp protocol implementation using
https://github.com/netty/netty
https://github.com/Shevchik/UdpServerSocketChannel

# License:

GNU GPLv3

# Usage example:

```java
import java.net.InetSocketAddress;

import io.netty.buffer.ByteBuf;
import raknetserver.RakNetServer;
import raknetserver.UserHandler;

public class ExampleRaknetServer {

	public static void main(String[] args) {
		new RakNetServer(new InetSocketAddress(25565), new UserHandler.Factory() {
			@Override
			public UserHandler create() {
				return new UserHandler() {
					@Override
					public void handleException(Throwable t) {
						t.printStackTrace();
						close();
					}
					
					@Override
					public void handleData(ByteBuf data) {
					}

					@Override
					public String getPingInfo() {
						return "test";
					}
				};
			}
		}).start();
	}

}
```