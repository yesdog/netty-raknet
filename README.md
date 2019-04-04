# RakNetServer
[![Build Status](https://travis-ci.org/yesdog/RakNetServer.svg?branch=master)](https://travis-ci.org/yesdog/RakNetServer)
[![Known Vulnerabilities](https://snyk.io/test/github/yesdog/RakNetServer/badge.svg?targetFile=build.gradle)](https://snyk.io/test/github/yesdog/RakNetServer?targetFile=build.gradle)

High performance fork of [RakNetServer](https://github.com/Shevchik/RakNetServer) 
targeting unreliable and rate-limited client connections.

This implementation uses [Netty](https://github.com/netty/netty) 
channels to provide a fast and effective [RakNet](http://www.raknet.net) server, 
offering the full feature set of the transport protocol, while providing
room for extension with any plugins or custom behavior. 

## Features
* Recylable objects:
  * Heavily used objects are recycled.
  * Reduces GC pressure.
  * Instrumented with Netty leak detection.
* 0-copy buffer interactions:
  * Retained buffer references throughout.
  * Composite buffers used for encapsulation and defragmentation. 
* Easy-to-use data streaming interface:
  * Configurable packet ID used for raw ByteBuf writing and reading.
  * Extensible to allow for multiple packet ID and channel configurations.
  * True to Netty form, the pipeline can be modified and augmented as needed.
* Advanced flow control
  * Back pressure signals useful for buffer limiting when client is overloaded. 
  * Pending frame-set limits reduce unnecessary resends during high transfer rates.
  * Resend priority based on frame sequence so you get older packets faster.
* Automated flush driver
  * Recommended to write to pipeline with no flush. 
  * Flush cycles condense outbound data for best use of MTU.

# [License](./LICENSE)
