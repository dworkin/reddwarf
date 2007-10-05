#!/bin/bash

machine_args="-server -d64 -XX:+UseBoundThreads -XX:+MaxFDLimit"
#machine_args="-ea"

echo java \
    ${machine_args} \
    -classpath 'build/test/classes/shared:build/classes/shared' \
    -Djava.awt.headless=true \
    -Djava.net.preferIPv4Stack=true \
    -Dcom.sun.sgs.nio.channels.spi.AsynchronousChannelProvider=com.sun.sgs.impl.nio.${provider} \
    -Dcom.sun.sgs.nio.async.reactive.reactors=${reactors} \
    -Djava.util.logging.config.file=${log} \
    ${host} -Dport=38502 \
    ${args} \
    ${extra_args} \
    ${app}

exec java \
    ${machine_args} \
    -classpath 'build/test/classes/shared:build/classes/shared' \
    -Djava.awt.headless=true \
    -Djava.net.preferIPv4Stack=true \
    -Dcom.sun.sgs.nio.channels.spi.AsynchronousChannelProvider=com.sun.sgs.impl.nio.${provider} \
    -Dcom.sun.sgs.nio.async.reactive.reactors=${reactors} \
    -Djava.util.logging.config.file=${log} \
    ${host} -Dport=38502 \
    ${args} \
    ${extra_args} \
    ${app}
