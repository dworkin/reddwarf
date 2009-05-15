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
 * Represents a channel with ordered message delivery.  The delivery
 * guarantee may be {@link Delivery#RELIABLE RELIABLE} or {@link
 * Delivery#ORDERED_UNRELIABLE ORDERED_UNRELIABLE}.  Instances of this 
 * class handle message delivery details for ordered channel messages.
 */
class OrderedChannel extends ChannelImpl {
    
    /** The serialVersionUID for this class. */
    private static final long serialVersionUID = 1L;

    /**
     * Constructs an instance with the specified {@code name}, {@code listener},
     * {@code delivery} guarantee, write capacity.
     */
    OrderedChannel(String name, ChannelListener listener,
		   Delivery delivery, int writeBufferCapacity)
    {
	super(name, listener, delivery, writeBufferCapacity);
    }

    /** {@inheritDoc}
     *
     * <p>This implementation enqueues the send event so that it can be
     * serviced by the channel's coordiinator to preserve message
     * ordering. 
     */
    @Override
    protected void handleSendEvent(ChannelImpl.SendEvent sendEvent) {
	addEvent(sendEvent);
    }    
}
