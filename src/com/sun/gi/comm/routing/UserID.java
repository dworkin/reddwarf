package com.sun.gi.comm.routing;

import java.io.Serializable;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.io.InputStream;
import com.sun.gi.utils.Communicable;
import com.sun.gi.utils.StatisticalUUID;

/**
 * This is an opaque type that represents a user connection to the router
 * system.  A "user" of the router system may be a game client conecting
 * through a UserManager or might be a backend server such as a game logic
 * engine that exists in the same process space as the Router itself.
 *
 * <p>Title: UserID</p>
 * <p>Description: An ID for a connection to the Router</p>
 * <p>Copyright: Copyright (c) 2004</p>
 * <p>Company: Sun Microsystems, TMI</p>
 * @author Jeff Kesselman
 * @version 1.0
 */
public class UserID extends StatisticalUUID {
	public static final UserID SERVER_ID = getLogicEngineID();
	
	public UserID() throws InstantiationException {
		super();
	}
	public UserID(byte[] ba) throws InstantiationException {
		super(ba);
	}
	
	private UserID(long time, long rand){
		super(time,rand);
	}
	
	public static UserID getLogicEngineID(){
		return new UserID(-1,-1);
	}
}
