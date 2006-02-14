package com.sun.gi.comm.routing;

import java.io.IOException;
import java.nio.ByteBuffer;

import javax.security.auth.Subject;
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
	 * @param user 
	 * @param subject 
	 * 
	 * @throws IOException 
	 */
	public void registerUser(SGSUser user, Subject subject) throws InstantiationException, IOException;

	/**
	 * This call is used to free a UserID that is no longer needed.
	 * 
	 * @param user The ID to dispose.
	 */
	public void deregisterUser(SGSUser user);

	public SGSChannel openChannel( String channelName);
	
	public SGSChannel getChannel(ChannelID id);

	public boolean validateReconnectKey(UserID user, byte[] key);

	public void serverMessage(boolean reliable, UserID userID, ByteBuffer databuff);
	
	public void addRouterListener(RouterListener listener);
	
	/**
	 * Joins the specified user to the Channel referenced by the
	 * given ChannelID.
	 * 
	 * @param user				the user
	 * @param id				the ChannelID
	 */
	public void join(UserID user, ChannelID id);
	
	/**
	 * Removes the specified user from the Channel referenced by the
	 * given ChannelID.
	 * 
	 * @param user				the user
	 * @param id				the ChannelID
	 */
	public void leave(UserID user, ChannelID id);
	
	/**
	 * Locks the given channel based on shouldLock.  Users cannot join/leave locked channels
	 * except by way of the Router.
	 * 
	 * @param cid				the channel ID
	 * @param shouldLock		if true, will lock the channel, otherwise unlock it.
	 */
	public void lock(ChannelID cid, boolean shouldLock);
	
	/**
	 * Closes the local view of the channel mapped to ChannelID.
	 * Any remaining users will be notified as the channel is closing.
	 * 
	 * @param id		the ID of the channel to close.
	 */
	public void closeChannel(ChannelID id);
	
}
