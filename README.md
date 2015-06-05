# whiskey
This is beta software. Bug reports and contribution are welcome, but caution should be exercised in deployment.

Whiskey is a Java HTTP library based on nio and intended especially to address the needs of Android mobile clients. It has no external dependencies.

The library shares some code with Netty's codec implementations, but adopts a client performance-focused approach to handling HTTP requests. It has also been developed specifically for support of newer protocols: SPDY, HTTP/2 and QUIC.

The application interface is designed to be extremely flexible, and supports both streaming and atomic operations, with both synchronous and asynchronous interaction.

The internals of the library are built to support lock-free and zero-copy operation, with most logic executing on a single internal run loop managing many sockets.
