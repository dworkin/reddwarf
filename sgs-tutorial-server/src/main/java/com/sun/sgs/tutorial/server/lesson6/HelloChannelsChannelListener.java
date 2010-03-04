/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.tutorial.server.lesson6;

import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.sgs.app.Channel;
import com.sun.sgs.app.ChannelListener;
import com.sun.sgs.app.ClientSession;
import java.nio.ByteBuffer;

/**
 * Simple example {@link ChannelListener} for the Project Darkstar Server.
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
     * {@inheritDoc}
     * <p>
     * Logs when data arrives on a channel. A typical listener would 
     * examine the message to decide whether it should be discarded, 
     * modified, or sent unchanged.
     */
    public void receivedMessage(Channel channel, 
                                ClientSession session, 
                                ByteBuffer message)
    {
        if (logger.isLoggable(Level.INFO)) {
            logger.log(Level.INFO,
                "Channel message from {0} on channel {1}",
                new Object[] { session.getName(), channel.getName() }
            );
        }
        channel.send(session, message);
    }
}
