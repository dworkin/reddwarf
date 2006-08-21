#!/bin/sh
exec java -cp ./SunGameServer.jar:lib/javax.servlet.jar:lib/org.mortbay.jetty.jar:lib/commons-logging.jar -Dsgs.framework.installurl=file:SGS-appsJME.conf -Djava.util.logging.config.file=logging.properties com.sun.gi.SGS

