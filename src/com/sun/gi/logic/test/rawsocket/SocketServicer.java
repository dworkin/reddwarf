package com.sun.gi.logic.test.rawsocket;

import java.io.IOException;
import java.io.Serializable;
import java.nio.ByteBuffer;

import com.sun.gi.framework.rawsocket.SimRawSocketListener;
import com.sun.gi.logic.SimTask;
import com.sun.gi.logic.SimTask.ACCESS_TYPE;

/**
 * <p>Title: SocketServicer</p>
 * 
 * <p>Description: A helper class to aid in testing the <code>RawSocketManager</code></p>
 * 
 * <p>Copyright: Copyright (c) 2006</p>
 * <p>Company: Sun Microsystems, TMI</p>
 * 
 * @author	Sten Anderson
 * @version 1.0
 */
public class SocketServicer implements Serializable, SimRawSocketListener {

	private static final long serialVersionUID = 8969950991047103803L;
	
	private int curBufferSize = 1;
	
	/**
	 * Called when the socket mapped to the specified socketID has been successfully
	 * opened and ready for communication.
	 * 
	 * @param socketID		the ID of the socket.
	 */
	public void socketOpened(SimTask task, long socketID) {
		//System.out.println("SocketServicer: Socket ID " + socketID + " is open for business!");
		//System.out.flush();
		writeBytes(task, socketID);
		
	}
	
	private void writeBytes(SimTask task, long socketID) {
		if (curBufferSize == 0 || curBufferSize >= 20) {
			task.closeSocket(socketID);
			curBufferSize = 1;
			return;
		}
		else {
			curBufferSize++;
		}
		ByteBuffer buffer = ByteBuffer.allocate(curBufferSize);
		for (int i = 0; i < curBufferSize; i++) {
			buffer.put((byte) socketID);		// easily identifies which socket is writing
		}
		buffer.flip();
		
		task.sendRawSocketData(socketID, buffer);
		
		//System.out.println("Wrote " + written + " bytes");
	}
	
	/**
	 * Called when there is incoming data on the socket mapped to the given ID.
	 * 
	 * @param socketID		the ID of the socket.
	 * @param data			the incoming data.
	 */
	public void dataReceived(SimTask task, long socketID, ByteBuffer data) {
		System.out.println("RawSocketTestBoot Received: " + data.capacity() + 
				" data:" + new String(data.array()).trim());
		
		writeBytes(task, socketID);
	}
	
	/**
	 * Called when the socket with the given ID is closed.
	 * 
	 * @param socketID		the ID of the socket.
	 */
	public void socketClosed(SimTask task, long socketID) {
		System.out.println("RawSocketTestBoot: SocketID " + socketID + " Closed.");
	}
	
	public void socketException(SimTask task, long socketID, IOException exception) {
		System.out.println("RawSocketTestBoot: Exception on SocketID " + socketID);
		exception.printStackTrace();
	}


}
