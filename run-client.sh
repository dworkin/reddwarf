#!/bin/bash

#host="-Dhost=dstar2.east.sun.com"
host="-Dhost=oni.east.sun.com"
provider=ReactiveAsyncChannelProvider
reactors=2
log=client-log.properties
args='-Dclients=2000 -Dmessages=100'
extra_args='-Dthreads=8 -Dmaxthreads=16 -Dtcp_nodelay=true'
app=com.sun.sgs.test.impl.nio.EchoClient

. ./test-run.sh

