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

package com.sun.sgs.impl.service.channel;

import com.sun.sgs.app.ChannelListener;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.Delivery;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.impl.sharedutil.CompactId;
import com.sun.sgs.impl.util.WrappedSerializable;
import com.sun.sgs.service.DataService;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Persistent state of a channel.
 */
final class ChannelState implements ManagedObject, Serializable {
    
    /** Serialization version. */
    private static final long serialVersionUID = 1L;
    
    /** The name of this channel. */
    final String name;

    /** The ID from a managed reference to this instance. */
    final byte[] idBytes;

    /** The channel ID for this instance, constructed from {@code idBytes}. */
    transient CompactId id;

    /** The listener for this channel, or null. */
    private WrappedSerializable<ChannelListener> channelListener;

    /** The delivery requirement for messages sent on this channel. */
    final Delivery delivery;

    /** The set of client sessions joined to this channel. */
    private final Set<ClientSession> sessions = new HashSet<ClientSession>();

    /**
     * A map whose keys are the client sessions joined to this channel
     * which have a listener, and whose values are per-session
     * ChannelListener for that session (null values *not* allowed).
     */
    private final
	Map<ClientSession, WrappedSerializable<ChannelListener>> listeners =
	    new HashMap<ClientSession, WrappedSerializable<ChannelListener>>();

    /**
     * Constructs an instance of this class with the specified name,
     * listener, and delivery requirement.
     */
    ChannelState(String name, ChannelListener listener, Delivery delivery,
		 DataService dataService)
    {
	this.name = name;
	this.channelListener =
	    listener != null ?
	    new WrappedSerializable<ChannelListener>(listener) :
	    null;
	this.delivery = delivery;
	ManagedReference ref = dataService.createReference(this);
	idBytes = ref.getId().toByteArray();
	id = new CompactId(idBytes);
    }

    /**
     * Returns a collection containing the client sessions joined to
     * the channel represented by this state.
     */
    Set<ClientSession> getSessions() {
	return new HashSet<ClientSession>(sessions);
    }

    /**
     * Returns {@code true} if this channel has at least one channel listener,
     * either a global channel listener or a per-session channel
     * listener for any member session.
     */
    boolean hasChannelListeners() {
	return channelListener != null || !listeners.isEmpty();
    }
    
    /* -- Implement Object -- */

    /** {@inheritDoc} */
    public boolean equals(Object obj) {
	if (this == obj) {
	    return true;
	} else if (obj.getClass() == this.getClass()) {
	    ChannelState state = (ChannelState) obj;
	    return name.equals(state.name);
	}
	return false;
    }

    /** {@inheritDoc} */
    public int hashCode() {
	return name.hashCode();
    }

    /** {@inheritDoc} */
    public String toString() {
	return getClass().getName() + "[" + name + "]";
    }

    /* -- other methods -- */

    boolean hasSession(ClientSession session) {
	return sessions.contains(session);
    }

    boolean hasSessions() {
	return !sessions.isEmpty();
    }

    void addSession(ClientSession session, ChannelListener listener) {
	sessions.add(session);
	if (listener != null) {
	    listeners.put(
		session, new WrappedSerializable<ChannelListener>(listener));
	}
    }

    void removeSession(ClientSession session) {
	WrappedSerializable<ChannelListener> listener =
	    listeners.remove(session);
	if (listener != null) {
	    listener.remove(ChannelServiceImpl.getDataService());
	}
	sessions.remove(session);
    }

    void removeAllSessions() {
	for (WrappedSerializable<ChannelListener> listener :
	     listeners.values())
	{
	    listener.remove(ChannelServiceImpl.getDataService());
	}
	listeners.clear();
	sessions.clear();
    }

    void removeAll() {
	removeAllSessions();
	if (channelListener != null) {
	    channelListener.remove();
	}
	channelListener = null;
    }

    ChannelListener getListener() {
	return
	    channelListener != null  ?
	    channelListener.get(ChannelListener.class) :
	    null;
    }

    ChannelListener getListener(ClientSession session) {
	WrappedSerializable<ChannelListener> listener =
	    listeners.get(session);
	return
	    listener != null ?
	    listener.get(ChannelListener.class) :
	    null;
    }
    
    /* -- Serialization methods -- */

    private void writeObject(ObjectOutputStream out)
	throws IOException
    {
	out.defaultWriteObject();
    }

    private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException
    {
	in.defaultReadObject();
	id = new CompactId(idBytes);
    }
}
