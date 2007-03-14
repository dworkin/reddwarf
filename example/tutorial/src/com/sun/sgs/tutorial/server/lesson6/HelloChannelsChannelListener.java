/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.tutorial.server.lesson6;

import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.sgs.app.Channel;
import com.sun.sgs.app.ChannelListener;
import com.sun.sgs.app.ClientSession;

/**
 * Simple example {@link ChannelListener} for the Sun Game Server.
 * <p>
 * Logs when a channel receives data.
 */
class HelloChannelsChannelListener
    implements Serializable, ChannelListener
{
    /** The version of the serialized form of this class. */
    private static final long serialVersionUID = 1L;

    /** The {@link Logger} for this class. */
    private static final Logger logger =
        Logger.getLogger(HelloChannelsChannelListener.class.getName());

    /**
     * The sequence number of this listener's session, as an example of
     * per-listener state.
     */
    private final int sessionNum;

    /**
     * Creates a new {@code HelloChannelsChannelListener}.
     *
     * @param sessionNum the number of this session; an example of
     *        per-listener state
     */
    public HelloChannelsChannelListener(int sessionNum) {
        this.sessionNum = sessionNum;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Logs when data arrives from our client on this channel.
     */
    public void receivedMessage(Channel channel, ClientSession session,
        byte[] message)
    {
        logger.log(Level.INFO,
            "Channel message from {0}/{1,number,#} on channel {2}",
            new Object[] { session.getName(), sessionNum, channel.getName() }
        );
    }
}
