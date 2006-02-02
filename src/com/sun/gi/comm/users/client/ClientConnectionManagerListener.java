package com.sun.gi.comm.users.client;

import java.nio.ByteBuffer;

import javax.security.auth.callback.Callback;

/**
 * <p>Title: ClientConnectionManagerListener
 * <p>Description: This interface defines a listener
 * for CLientConnectionManager events.
 *
 * @see ClientConnectionManager
 *
 * <p>Copyright: Copyright (c) 2005 Sun Microsystems, Inc.</p> New look
 * <p>Company: Sun Microsystems</p>
 * @author Jeffrey P. Kesselman
 * @version 1.0
 */
public interface ClientConnectionManagerListener {

    public void validationRequest(Callback[] callbacks);

    public void connected(byte[] myID);

    public void connectionRefused(String message);

    public void failOverInProgress();

    public void reconnected();

    public void disconnected();

    /**
     * This event is fired when a user joins the game.  When a client
     * initially connects to the game it will receive a userJoined
     * callback for every other user rpesent.  Aftre that every time a
     * new user joins, another callback will be issued.
     *
     * @param userID The ID of the joining user.
     */
    public void userJoined(byte[] userID);

    /**
     * This event is fired whenever a user leaves the game.
     * This occurs either when a user purposefully disconnects or when
     * they drop and do not re-connect within the timeout specified
     * for the reconnection key in the Darkstar
     * backend.
     * <p>
     * <b>NOTE: In certain rare cases (such as the death of a slice),
     * notification may be delayed.  (In the slice-death case it is
     * delayed until a watchdog notices the dead slice.)</b>
     *
     * @param userID The ID of the user leaving the system.
     */
    public void userLeft(byte[] userID);

    /**
     * This event is fired to notify the listener of sucessful
     * completion of a channel open operation.
     *
     * @param channel the channel object used to communicate on
     *                the opened channel.
     */
    public void joinedChannel(ClientChannel channel);

    /**
     * Called whenever an attempted join/leave fails due to the target
     * channel being locked.
     *
     * @param channelName	the name of the channel
     * @param userID		the ID of the user attemping to join/leave
     */
    public void channelLocked(String channelName, byte[] userID);
}
