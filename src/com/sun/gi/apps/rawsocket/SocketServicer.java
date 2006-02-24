package com.sun.gi.apps.rawsocket;

import java.io.IOException;
import java.nio.ByteBuffer;

import com.sun.gi.framework.rawsocket.SimRawSocketListener;
import com.sun.gi.gloutils.pdtimer.PDTimer;
import com.sun.gi.logic.GLO;
import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimTask;
import com.sun.gi.logic.SimTimerListener;
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
public class SocketServicer implements GLO, SimRawSocketListener/*, SimTimerListener*/ {

	private static final long serialVersionUID = 8969950991047103803L;
	
	private int curBufferSize = 1;
	
	/**
	 * Called when the socket mapped to the specified socketID has been successfully
	 * opened and ready for communication.
	 * 
	 * @param socketID		the ID of the socket.
	 */
	public void socketOpened(long socketID) {
		//System.out.println("SocketServicer: Socket ID " + socketID + " is open for business!");
		//System.out.flush();
		
		/*try {
			GLOReference thisRef = task.makeReference(this);
			task.registerTimerEvent(ACCESS_TYPE.GET, 5000, true, thisRef);
		}
		catch (InstantiationException ie) {
			ie.printStackTrace();
		}*/

		writeBytes(socketID);
		
	}
	
	/*public void timerEvent(SimTask task, long eventID) {
		writeBytes(task, 0);
	}*/
	
	public void writeBytes(long socketID) {
		SimTask task = SimTask.getCurrent();
		if (curBufferSize == 0 || curBufferSize >= 20) {
			System.out.println("Done sending data");
			task.closeSocket(socketID);
			curBufferSize = 1;
			return;
		}
        
        curBufferSize++;
		ByteBuffer buffer = ByteBuffer.allocate(curBufferSize);
		for (int i = 0; i < curBufferSize; i++) {
			buffer.put((byte) socketID);		// easily identifies which socket is writing
		}
		
		task.sendRawSocketData(socketID, buffer);
		
		System.out.println("Wrote bytes");
	}
	
	/**
	 * Called when there is incoming data on the socket mapped to the given ID.
	 * 
	 * @param socketID		the ID of the socket.
	 * @param data			the incoming data.
	 */
	public void dataReceived(final long socketID, ByteBuffer data) {
		System.out.println("RawSocketTestBoot Received on ID " + socketID + 
				" data:" + new String(data.array()).trim());
		
		writeBytes(socketID);


	}
	
	/**
	 * Called when the socket with the given ID is closed.
	 * 
	 * @param socketID		the ID of the socket.
	 */
	public void socketClosed(long socketID) {
		SimTask task = SimTask.getCurrent();
		System.out.println("RawSocketTestBoot: SocketID " + socketID + " Closed.");
	}
	
	public void socketException(long socketID, IOException exception) {
		System.out.println("RawSocketTestBoot: Exception on SocketID " + socketID);
		exception.printStackTrace();
	}


}
