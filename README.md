# RakNetServer:

High performance fork of [RakNetServer](https://github.com/Shevchik/RakNetServer) 
targeting unreliable and rate-limited client connections.

This implementation uses [Netty](https://github.com/netty/netty) 
channels to provide a fast and effective [RakNet](http://www.raknet.net) server, 
offering the full feature set of the transport protocol while providing
room for extension with any plugins or custom behavior. 

## Features:
* Recylable objects
  * Heavily used objects are recycled.
  * Reduces GC pressure.
  * Instrumented with Netty leak detection.
* 0-copy buffer interactions
  * Retained buffer references throughout.
  * Composite buffers used for encapsulation and defragmentation. 
* Easy-to-use data streaming interface
  * Configurable packet ID used for raw ByteBuf writing and reading.
  * Extensible to allow for multiple packet ID and channel configurations.
  * True to netty form, the pipeline can be modified and augmented as needed.

# License:

GNU GPLv3
