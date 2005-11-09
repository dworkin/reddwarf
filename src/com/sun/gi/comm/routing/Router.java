package com.sun.gi.comm.routing;

import java.io.IOException;
import java.nio.ByteBuffer;

import javax.security.auth.callback.Callback;

import com.sun.gi.comm.users.server.SGSUser;



/**
 * This interface defines the fundemental functionality of a Router. Routers
 * create and dispose of UserIds and move messages to users by way of their user
 * IDs.
 * 
 * <p>
 * Title: Router
 * </p>
 * <p>
 * Description: A tier 1 message router
 * </p>
 * <p>
 * Copyright: Copyright (c) 2004
 * </p>
 * <p>
 * Company: Sun Microsystems, TMI
 * </p>
 * 
 * @author Jeff Kesselman
 * @version 1.0
 */
public interface Router {
	/**
	 * This call is made in order to allocate a new unqiue UserID.
	 * 
	 * @return UserID The new ID
	 * @throws IOException 
	 */
	public void registerUser(SGSUser user) throws InstantiationException, IOException;

	/**
	 * This call is used to free a UserID that is no longer needed.
	 * 
	 * @param id
	 *            UserID The ID to dispose.
	 */
	public void deregisterUser(SGSUser user);

	public SGSChannel openChannel( String channelName);	

	public boolean validateReconnectKey(UserID user, byte[] key);

	public void serverMessage(boolean reliable, UserID userID, ByteBuffer databuff);
	
	
}
