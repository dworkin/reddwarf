#!/bin/bash

provider=ReactiveAsyncChannelProvider
log=server-log.properties
reactors=2
args='-Dbacklog=5'
extra_args='-Dthreads=8 -Dmaxthreads=16 -Dtcp_nodelay=true'
app=com.sun.sgs.test.impl.nio.EchoServer

. ./test-run.sh

