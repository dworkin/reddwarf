/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sun.sgs.tutorial.server.lesson6;

import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.sgs.app.Channel;
import com.sun.sgs.app.ChannelListener;
import com.sun.sgs.app.ClientSession;

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
