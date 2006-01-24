package com.sun.gi.comm.users.client;

import java.nio.ByteBuffer;


/**
 <p>Title: ClientChannel
 * <p>Description: This interface defines a SGS channel for use by a client program.</p>
 * <p>Copyright: Copyright (c) 2005 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems</p>
 * @author Jeffrey P. Kesselman
 * @version 1.0
 *
 */

public interface ClientChannel {
	/**
	 * This method returns the name of the channel.
	 * @return  The name of the channel.
	 */
	public String getName();
	/**
	 * This method sets a listener for evenst on this channel.  Only one listener is 
	 * allowed per channel
	 * @param l The object that wil lreceieve channel events on this channel.
	 * @see ClientChannelListener
	 */
	public void setListener(ClientChannelListener l);
	
	/**
	 * This method is used to send data to a single other user of a channel.
	 * 
	 * @param to The ID of the user to send the data to.
	 * @param data The data to transmit
	 * @param reliable Whether we need a delivery gaurantee or not.
	 */
	public void sendUnicastData(byte[] to, ByteBuffer data, boolean reliable);
	
	/**
	 * This method is used to send data to a list of other users of a channel.
	 * 
	 * @param to A list of IDs of the users to send the data to.
	 * @param data The data to transmit
	 * @param reliable Whether we need a delivery gaurantee or not.
	 */
	public void sendMulticastData(byte[][]to, ByteBuffer data, boolean reliable);
	
	/**
	 * This method is used to send data to all other users of a channel.
	 * 
	 * @param data The data to transmit
	 * @param reliable Whether we need a delivery gaurantee or not.
	 */
	public void sendBroadcastData(ByteBuffer data, boolean reliable);
	
	/**
	 * 
	 * This method closes the channel for thsi user.  Everyone else sees
	 * this as a "userLeftChannel" event.
	 */
	
	public void close();
	
}
