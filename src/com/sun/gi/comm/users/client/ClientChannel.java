package com.sun.gi.comm.users.client;

import java.nio.ByteBuffer;

/**
 <p>Title: ClientChannel
 * <p>Description: This interface defines a SGS channel for use by a client
 * program.</p> <p>Copyright: Copyright (c) 2005 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems</p>
 * @author Jeffrey P. Kesselman
 * @version 1.0
 *
 */
public interface ClientChannel {

    /**
     * This method returns the name of the channel.
     *
     * @return  the name of the channel.
     */
    public String getName();

    /**
     * Sets the one-and-only listener for events on this channel.
     * Only one listener is allowed per channel
     *
     * @param l the new listener for events on this channel.
     *
     * @see ClientChannelListener
     */
    public void setListener(ClientChannelListener l);

    /**
     * Sends data to a single other user of a channel.
     *
     * @param to        the ID of the user to send the data to
     * @param data      the data to transmit
     * @param reliable true if the data requires reliable delivery
     */
    public void sendUnicastData(byte[] to, ByteBuffer data, boolean reliable);

    /**
     * Sends data to a list of other users of a channel.
     *
     * @param to       the array of IDs of the users to send the data to
     * @param data     the data to transmit
     * @param reliable true if the data requires reliable delivery
     */
    public void sendMulticastData(byte[][]to, ByteBuffer data,
	boolean reliable);

    /**
     * Sends data to all other users of a channel.
     *
     * @param data     the data to transmit
     * @param reliable true if the data requires reliable delivery
     */
    public void sendBroadcastData(ByteBuffer data, boolean reliable);

    /**
     * Closes the channel for this user.  Everyone else sees
     * this as a "userLeftChannel" event.
     */
    public void close();
}
