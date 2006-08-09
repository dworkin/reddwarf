rem
rem Runs the Chat Test MIDlet file in the emulator.
rem

set EMULATOR_PATH=c:\WTK23\bin

%EMULATOR_PATH%\emulator -Xdevice:Nokia6620 -Xheapsize:5m -classpath ..\J2MEClientAPI\dist\SunGameServerJMEClient.jar;dist\JMEChatClient.jar -Xdescriptor:dist\JMEChatClient.jad
