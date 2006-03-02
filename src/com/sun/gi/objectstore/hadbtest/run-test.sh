#!/bin/csh -f

set root = ../../../../../..
set classes = "$root/bin"
#set derby = "$root/resources/libs/derby/derby.jar"
set derby = "$root/resources/libs/derby/derby-10.1.1.0.jar"
set hadb = "./hadbjdbc4.jar"
set package = com.sun.gi.objectstore.hadbtest

set classpath = $classes\:$hadb\:$derby

echo $classpath
echo $package.$*

set heapProf = ""
#set heapProf = "-agentlib:hprof=heap=all,depth=22"

if ($?testParams) then
    echo "WITH TESTPARAMS"
    java $testParams $heapProf -classpath $classpath $package.$*
else
    java $heapProf -classpath $classpath $package.$*
endif
