
TARGETS

clean           Removes all files created the run-* targets from the temporary
                directory.

run-client      Runs a client process.

run-single      Runs a single node application server.

run-server      Runs the application server in multi-node mode.

run-core        Runs the core services in multi-node mode.

PROPERTIES

property name:  server.host
default value:  localhost
description:    Host name of application server node. Used in the run-client
                target and should be set to the name of the host where either
                the run-single or run-server targets were invoked.

property name:  server.port
default value:  1139
description:    Host port of application server. Used in the run-client,
                run-server, and run-single targets.

property name:  core.host
default value:  localhost
description:    Host name of core server node. Used in run-server target and
                should be set to the name of the host where run-core was invoked.

property name:  use.je
default value:  <undefined>
description:    If defined use Berkley DB Java Edition.

property name:  tmp.dir
default value:  /tmp
description:    Directory name to use for temporary property files and
                database files.

property name:  retain.datastore
default value:  <undefined>
description:    If defined, the database files will not be deleted each time
                run-server or run-core is invoked.

EXAMPLES

To run the Wonderland server and a client process on the same node:

    ant run-single
    ant run-client

To run a multi-node Wonderland server and a client on three nodes using BDB Java edition:

    on node1:

    ant -Duse.je= run-core

    on node2

    ant -Dcore.host=node1 run-server

    on node3

    ant -Dserver.host=node2 run-client

