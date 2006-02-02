package com.sun.gi.comm.users.client;

import java.nio.ByteBuffer;

/**
 * <p>Title: ClientChannelListener
 * <p>Description: This interface defines how events are reported back from
 *   a ClientChannel</p>
 * <p>Copyright: Copyright (c) 2005 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems</p>
 *
 * @author Jeffrey P. Kesselman
 * @version 1.0
 *
 * @see ClientChannel
 */
public interface ClientChannelListener {

    /**
     * A new player has joined the channel this listener is registered on.
     *
     * @param playerID The ID of the joining player.
     */
    public void playerJoined(byte[] playerID);

    /**
     * A player has left the channel this listener is registered on.
     *
     * @param playerID The ID of the leaving player.
     */
    public void playerLeft(byte[] playerID);

    /**
     * A packet has arrived for this listener on this channel.
     *
     * @param from     the ID of the sending player.
     * @param data     the packet data
     * @param reliable true if this packet was sent reliably
     */
    public void dataArrived(byte[] from, ByteBuffer data, boolean reliable);

    /**
     * Notifies this listener that the channel has been closed.
     */
    public void channelClosed();
}
