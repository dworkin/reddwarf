#!/bin/sh

exec emulator -Xdevice:Nokia6620 -classpath ../SunGameServerJMEClient.jar:../client/JMEChatClient.jar -Xdescriptor:../client/JMEChatClient.jad
