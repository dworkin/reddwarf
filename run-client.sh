#!/bin/sh

CLASSPATH=xt:jsr203.jar
export CLASSPATH

exec java -server -Dmaxthreads=16 -Dclients=20000 -Dmessages=1000 -Dhost=dstar2 -d64 -XX:+UseBoundThreads -XX:+MaxFDLimit -Djava.awt.headless=true -Djava.net.preferIPv4Stack=true -Djava.util.logging.config.file=client-log.properties -Dcom.sun.sgs.nio.channels.spi.AsynchronousChannelProvider=com.sun.sgs.impl.nio.ReactiveAsyncChannelProvider com.sun.sgs.test.impl.nio.EchoClient
