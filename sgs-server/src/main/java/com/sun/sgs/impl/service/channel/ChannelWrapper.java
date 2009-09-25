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

import com.sun.sgs.app.Channel;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.Delivery;
import com.sun.sgs.app.ManagedObjectRemoval;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.ObjectNotFoundException;
import java.io.Serializable;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Set;

/**
 * A wrapper for a {@code ChannelImpl} object that is returned to the
 * application when a channel is created.  A {@code ChannelWrapper} is
 * handed back to the application instead of a direct reference to a
 * {@code ChannelImpl} instance to avoid the possibility of the application
 * removing the {@code ChannelImpl} instance from the data service and
 * interfering with the channel service's persistent data.
 *
 * <p>When a {@code ChannelWrapper} instance is removed from the data
 * service, the underlying channel is closed.
 */
class ChannelWrapper
    implements Channel, Serializable, ManagedObjectRemoval
{
    /** The serialVersionUID for this class. */
    private static final long serialVersionUID = 1L;

    /** The reference to the channel that this instance wraps. */
    private final ManagedReference<ChannelImpl> channelRef;

    /**
     * Constructs an instance with the specified {@code channelRef}.
     *
     * @param	channelRef a reference to a channel to wrap
     */
    ChannelWrapper(ManagedReference<ChannelImpl> channelRef) {
	this.channelRef = channelRef;
    }

    /* -- Implement Channel -- */

    /** {@inheritDoc} */
    public String getName() {
	return getChannel().getName();
    }
    
    /** {@inheritDoc} */
    public Delivery getDelivery() {
	return getChannel().getDelivery();
    }

    /** {@inheritDoc} */
    public boolean hasSessions() {
	return getChannel().hasSessions();
    }

    /** {@inheritDoc} */
    public Iterator<ClientSession> getSessions() {
	return getChannel().getSessions();
    }

    /** {@inheritDoc} */
    public Channel join(final ClientSession session) {
	getChannel().join(session);
	return this;
    }

    /** {@inheritDoc} */
    public Channel join(final Set<? extends ClientSession> sessions) {
	getChannel().join(sessions);
	return this;
    }

    /** {@inheritDoc} */
    public Channel leave(final ClientSession session) {
	getChannel().leave(session);
	return this;
    }

    /** {@inheritDoc} */
    public Channel leave(final Set<? extends ClientSession> sessions) {
	getChannel().leave(sessions);
	return this;
    }

    /** {@inheritDoc} */
    public Channel leaveAll() {
	getChannel().leaveAll();
	return this;
    }

    /** {@inheritDoc} */
    public Channel send(ClientSession sender, ByteBuffer message) {
	getChannel().send(sender, message);
	return this;
    }

    /* -- Implement ManagedObjectRemoval -- */

    /** {@inheritDoc} */
    public void removingObject() {
	try {
	    channelRef.get().close();
	} catch (ObjectNotFoundException e) {
	    // already closed.
	}
    }

    /* -- Public methods -- */

    /**
     * Returns this channel's ID as a {@code BigInteger}.
     *
     * @return	this channel's ID as a {@code BigInteger}
     */
    public BigInteger getChannelId() {
	return channelRef.getId();
    }

    /* -- Implement Object -- */

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object object) {
	if (object == this) {
	    return true;
	} else if (object instanceof ChannelWrapper) {
	    return channelRef.equals(((ChannelWrapper) object).channelRef);
	} else {
	    return false;
	}
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
	return channelRef.hashCode();
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
	ChannelImpl channelImpl = null;
	try {
	    channelImpl = channelRef.get();
	} catch (Exception e) {
	}
	return getClass().getName() + "[" +
	    (channelImpl == null ?
	     channelRef.toString() :
	     channelImpl.toString()) +
	    "]";
    }
    
    /* -- Other methods -- */

    /**
     * Returns the underlying {@code ChannelImpl} instance for this
     * wrapper.  If the underlying channel has been removed, then the
     * channel has been closed, so {@code IllegalStateException} is thrown.
     */
    private ChannelImpl getChannel() {
	try {
	    return channelRef.get();
	} catch (ObjectNotFoundException e) {
	    throw new IllegalStateException("channel is closed");
	}
    }
}
