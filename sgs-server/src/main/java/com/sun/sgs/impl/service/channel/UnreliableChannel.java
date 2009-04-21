/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
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

package com.sun.sgs.impl.service.channel;

import com.sun.sgs.app.ChannelListener;
import com.sun.sgs.app.Delivery;

/**
 * Represents an unreliable channel. Instances of this class handle message
 * delivery details for unreliable channel messages.
 */
class UnreliableChannel extends ChannelImpl {
    
    /** The serialVersionUID for this class. */
    private static final long serialVersionUID = 1L;

    /**
     * Constructs an instance with the specified {@code name}, {@code listener},
     * {@code delivery} guarantee, write capacity.
     */
    UnreliableChannel(String name, ChannelListener listener,
		      Delivery delivery, int writeBufferCapacity)
    {
	super(name, listener, delivery, writeBufferCapacity);
    }

    /** {@inheritDoc}
    *
    * <p>This implementation services the channel send event immediately,
    * even if the local node is not the coordinator node,  because central
    * coordination of message ordering is not needed for unreliable
    * messages.
    */
    @Override
    protected void handleSendEvent(ChannelImpl.SendEvent sendEvent) {
	sendEvent.serviceEvent(this);
    }
}
