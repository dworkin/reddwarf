#!/bin/sh

exec emulator -Xdevice:Nokia6620 -Xheapsize:5m -classpath ../J2MEClientAPI/dist/SunGameServerJMEClient.jar:dist/JMEChatClient.jar -Xdescriptor:dist/JMEChatClient.jad
