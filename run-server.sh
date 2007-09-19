#!/bin/sh

CLASSPATH=xt:jsr203.jar
export CLASSPATH

exec java -server -Dthreads=1 -Dbacklog=5 -d64 -XX:+UseBoundThreads -XX:+MaxFDLimit -Djava.awt.headless=true -Djava.net.preferIPv4Stack=true -Djava.util.logging.config.file=logging.properties -Dcom.sun.sgs.nio.channels.spi.AsynchronousChannelProvider=com.sun.sgs.impl.nio.ReactiveAsyncChannelProvider com.sun.sgs.test.impl.nio.EchoServer
