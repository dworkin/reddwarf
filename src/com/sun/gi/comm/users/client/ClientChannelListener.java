package com.sun.gi.comm.users.client;

import java.nio.ByteBuffer;

/**<p>Title: ClientChannelListener
* <p>Description: This interface defines how events are reported back from a ClientChannel</p>
* <p>Copyright: Copyright (c) 2005 Sun Microsystems, Inc.</p>
* <p>Company: Sun Microsystems</p>
* @author Jeffrey P. Kesselman
* @version 1.0
* @see ClientChannel
*/

public interface ClientChannelListener {
	/**
	 * This method is called when a new player joins the channel.
	 * @param playerID The ID of the joining player.
	 */
	public void playerJoined(byte[] playerID);
	/**
	 * This method is called when a player leaves the channel.
	 * @param playerID The ID of the leaving player.
	 */
	public void playerLeft(byte[] playerID);
	/**
	 * This method is called to report the arrival of a packet intended for us on the
	 * channel.
	 * @param from The ID of the sending player.
	 * @param data The actual packet data
	 * @param reliable Whether this packet was sent reliably or unreliably.
	 */
	public void dataArrived(byte[] from, ByteBuffer data, boolean reliable);
	
	public void channelClosed();
}

