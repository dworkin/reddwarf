rem
rem Runs the Chat Test MIDlet file in the emulator.
rem

set EMULATOR_PATH=c:\WTK23\bin

%EMULATOR_PATH%\emulator -Xdevice:Nokia6620 -classpath ..\SunGameServerJMEClient.jar;..\client\JMEChatClient.jar -Xdescriptor:..\client\JMEChatClient.jad
