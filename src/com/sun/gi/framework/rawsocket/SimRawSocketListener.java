package com.sun.gi.framework.rawsocket;

import java.io.IOException;
import java.nio.ByteBuffer;

import com.sun.gi.logic.SimTask;

/**
 * <p>Title: SimRawSocketListener</p>
 * 
 * <p>Description: Objects interested in communicating with sockets to arbitrary hosts
 * should implement this interface.</p>
 * 
 * <p>Copyright: Copyright (c) 2006</p>
 * <p>Company: Sun Microsystems, TMI</p>
 * 
 * @author	Sten Anderson
 * @version 1.0
 */
public interface SimRawSocketListener {
	
	/**
	 * Called when the socket mapped to the specified socketID has been successfully
	 * opened and ready for communication.
	 * 
	 * @param socketID		the ID of the socket.
	 */
	public void socketOpened(long socketID);
	
	/**
	 * Called when there is incoming data on the socket mapped to the given ID.
	 * The data in the buffer is ready to be read (i.e. has already been flipped
	 * if necessary).
	 * 
	 * @param socketID		the ID of the socket.
	 * @param data			the incoming data.
	 */
	public void dataReceived(long socketID, ByteBuffer data);
	
	/**
	 * Called when the socket with the given ID is closed.
	 * 
	 * @param socketID		the ID of the socket.
	 */
	public void socketClosed(long socketID);
	
	/**
	 * Called when an exception is thrown after an attempt to perform 
	 * one of the other socket operations.  Since the I/O is non-blocking,
	 * requested operations happen asynchronously.  This callback serves as a 
	 * "catch all" error message that some requested operation failed.  The
	 * IOException will contain the details. 
	 * 
	 * @param task
	 * @param socketID			the ID of the socket on which the exception was thrown
	 * @param exception			the actual exception
	 */
	public void socketException(long socketID, IOException exception);

}
