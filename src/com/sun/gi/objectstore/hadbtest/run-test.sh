#!/bin/csh -f

set root = ../../../../../..
set classes = "$root/bin"
set derby = "$root/support/db-derby-10.1.2.1-bin/lib/derby.jar"
set hadb = "./hadbjdbc4.jar"
set package = com.sun.gi.objectstore.hadbtest

set classpath = $classes\:$hadb\:$derby

echo $classpath
echo $package.$*

set heapProf = "-agentlib:hprof=heap=all,depth=22"

if ($?testParams) then
    echo "WITH TESTPARAMS"
    java $testParams -classpath $classpath $package.$*
else
    java -classpath $classpath $package.$*
endif
