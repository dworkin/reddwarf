package com.sun.gi.utils.test;

import com.sun.gi.utils.nio.NIOSocketManager;
import com.sun.gi.utils.nio.NIOConnectionListener;
import com.sun.gi.utils.nio.NIOConnection;
import java.nio.ByteBuffer;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.IOException;

public class NIOTCPTestClient implements NIOConnectionListener {

    NIOSocketManager socketManager;
    NIOConnection conn;
    ByteBuffer outputBuffer = ByteBuffer.allocate(2048);
    ByteBuffer newlineBuffer = ByteBuffer.allocate(1);
    ByteBuffer[] sendBufs = new ByteBuffer[] { outputBuffer, newlineBuffer };

    public NIOTCPTestClient() {
	try {
	    socketManager = new NIOSocketManager();
	}
	catch (IOException ex1) {
	    ex1.printStackTrace();
	    System.exit(1);
	}
	conn = socketManager.makeConnectionTo("localhost",1138);
	conn.addListener(this);
	BufferedReader rdr =
	    new BufferedReader(new InputStreamReader(System.in));

	newlineBuffer.put("\n".getBytes());

	while(true){
	    try {
		boolean reliable = true;

		String line = rdr.readLine();

		if (line.contentEquals("exit"))
		    break;

		reliable = ! line.startsWith("!");

		outputBuffer.clear();
		outputBuffer.put(line.getBytes());
		conn.send(sendBufs, reliable);
	    }
	    catch (IOException ex) {
		ex.printStackTrace();
	    }
	}

	socketManager.shutdown();
    }

    static public void main(String[] args){
	new NIOTCPTestClient();
    }

    // NIOConnectionListener methods

    public void packetReceived(NIOConnection conn, ByteBuffer inputBuffer) {
	byte[] inbytes=new byte[inputBuffer.remaining()];
	inputBuffer.get(inbytes);
	System.out.println("Received: " + new String(inbytes));
    }

    public void disconnected(NIOConnection conn) {
	System.err.println("Disconnected!");
    }
}
