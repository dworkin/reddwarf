package com.sun.gi.framework.rawsocket;

import java.nio.ByteBuffer;

import com.sun.gi.logic.Simulation;
import com.sun.gi.logic.SimTask.ACCESS_TYPE;


/**
 * <p>Title: RawSocketManager </p>
 * 
 * <p>Description: The RawSocketManager manages creation and control of sockets connected
 * to arbitrary hosts in the "outside world".  Once a <code>RawSocketListener</code>
 * initiates a connection via openSocket, it implicitly becomes registered to
 * receive data on that socket until closeSocket is called.</p>
 * 
 * <p>Copyright: Copyright (c) 2006</p>
 * <p>Company: Sun Microsystems, TMI</p>
 * 
 * @author	Sten Anderson
 * @version	1.0
 *
 */
public interface RawSocketManager {
	
	/**
	 * <p>Attempts to open a socket to the specified host on the specified port.
	 * This will implicitly register the given <code>RawSocketListener</code>
	 * for events on this socket.</p>
	 * 
	 * <p>The socket is not immediately opened, but rather queued to be
	 * opened at a near future time.  Once the connection process completes
	 * a socketOpened event is generated.</p>
	 * 
	 * @param socketID		the ID of the socket.
	 * @param sim			the simulation.
	 * @param access		the type of access.
	 * @param startObjectID	the ID of the GLO.
	 * @param host			the host to connect to.
	 * @param port			the port to connect to.
	 * @param reliable		if true, the connection with be reliable.
	 * 
	 * @return a socketID that can be used in future communication to reference the socket. 
	 */
	public long openSocket(long socketID, Simulation sim, ACCESS_TYPE access, long startObjectID, 
							String host, int port, boolean reliable);
	
	/**
	 * Attempts to send the given data over the socket associated with
	 * the given socketID.  The method will not return until the entire buffer
	 * has been drained.
	 * 
	 * @param socketID			used to lookup the associated socket.
	 * @param data				the data to send.
	 * 
	 * @return	the number of bytes actually sent.
	 */
	public long sendData(long socketID, ByteBuffer data);
	
	/**
	 * <p>Closes the socket associated with the specified socketID.
	 * This implicitly deregisters the <code>RawSocketListener</code> 
	 * associated with this socketID.</p>
	 * 
	 * <p>Socket closure is not immediate, rather the socket is queued 
	 * to be closed.  Multiple calls to this method with the same 
	 * socketID will be ignored.</p>
	 * 	
	 * @param socketID			the ID of the socket to close.
	 */
	public void closeSocket(long socketID);

	/**
	 * Returns the next available socket ID.
	 * 
	 * @return		the next socket ID.
	 */
	public long getNextSocketID();

}
