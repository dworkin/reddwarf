package com.sun.gi.logic;

import java.nio.ByteBuffer;

import com.sun.gi.framework.rawsocket.RawSocketManager;
import com.sun.gi.framework.timer.TimerManager;
import com.sun.gi.logic.SimTask.ACCESS_TYPE;
import com.sun.gi.logic.impl.SimThreadImpl;
import com.sun.gi.objectstore.ObjectStore;
import com.sun.gi.objectstore.Transaction;

/**
 * <p>
 * Title: Sim Kernel
 * </p>
 * <p>
 * Description: This is the interface to the logic engine. Each game
 * (simulation) in a slice has its own run-time sim object that implements this
 * interface and provides the operating context for the game.
 * </p>
 * <p>
 * Copyright: Copyright (c) 2003
 * </p>
 * <p>
 * Company: Sun Microsystems, TMI
 * </p>
 * 
 * @author Jeff Kesselman
 * @version 1.0
 */

public interface SimKernel {
	

	
	
	public void addSimulation(Simulation sim);
	
	public void simHasNewTask();

	public void removeSimulation(Simulation sim);

	/**
	 * @param impl
	 */
	public void returnToThreadPool(SimThreadImpl impl);

	/**
	 * @param timerManager
	 */
	public void setTimerManager(TimerManager timerManager);
	
	/**
	 * 
	 * @param tid
	 * @param access 
	 * @param sim
	 * @param objID
	 * @param delay
	 * @param repeat
	 * @return an identifier that refers to the timer event
	 */
	public long registerTimerEvent(long tid, ACCESS_TYPE access, Simulation sim, long objID, long delay,
			boolean repeat);
	
	
//	 Hooks into the RawSocketManager, added 1/16/2006
	
	/**
	 * Sets the Raw Socket Manager.
	 * 
	 */
	public void setRawSocketManager(RawSocketManager socketManager);
	
	/**
	 * Requests that a socket be opened at the given host on the given port.
	 * The returned ID can be used for future communication with the socket that will
	 * be opened.  The socket ID will not be valid, and therefore should not be used 
	 * until the connection is complete.  Connection is complete once the 
	 * SimRawSocketListener.socketOpened() call back is called.
	 * 
	 * @param sim				the simulation requesting the connection.
	 * @param access			the access type (GET, PEEK, or ATTEMPT)
	 * @param objID				a reference to the GLO initiating the connection.
	 * @param host				a String representation of the remote host.
	 * @param port				the remote port.
	 * @param reliable			if true, the connection will use a reliable protocol.
	 * 
	 * @return an identifier that can be used for future communication with the socket.
	 */
	public long openSocket(long socketID, Simulation sim, ACCESS_TYPE access, long objID, String host, 
			int port, boolean reliable);

	
	/**
	 * Sends data on the socket mapped to the given socketID.  This method 
	 * will not return until the entire buffer has been drained.
	 * 
	 * @param socketID			the socket identifier.
	 * @param data				the data to send.  The buffer should be in a ready
	 * 							state, i.e. flipped if necessary. 
	 * 
	 * @return the number of bytes sent.
	 */
	public long sendRawSocketData(long socketID, ByteBuffer data);
	
	/**
	 * Requests that the socket matching the given socketID be closed.
	 * The socket should not be assumed to be closed, however, until the 
	 * call back SimRawSocketListener.socketClosed() is called.
	 * 
	 * @param socketID		the identifier of the socket.
	 */
	public void closeSocket(long socketID);

	/**
	 * @return the next timer id.
	 */
	public long getNextTimerID();

	/**
	 * @return the next socket id.
	 */
	public long getNextSocketID();
	
}
