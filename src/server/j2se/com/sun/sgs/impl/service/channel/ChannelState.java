package com.sun.sgs.impl.service.channel;

import com.sun.sgs.app.ChannelListener;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.Delivery;
import com.sun.sgs.app.ManagedObject;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Persistent state of a channel.
 */
final class ChannelState implements ManagedObject, Serializable {
    
    /** Serialization version. */
    private static final long serialVersionUID = 1L;
    
    /** The name of this channel. */
    final String name;

    /** The listener for this channel. */
    final ChannelListener listener;

    /** The delivery requirement for messages sent on this channel. */
    final Delivery delivery;

    /**
     * A map whose keys are the client sessions joined to this channel
     * and whose values are per-session ChannelListeners (null values
     * allowed).
     */
    final Map<ClientSession, ChannelListener> sessions =
	new HashMap<ClientSession, ChannelListener>();

    /**
     * Constructs an instance of this class with the specified name,
     * listener, and delivery requirement.
     */
    ChannelState(String name, ChannelListener listener, Delivery delivery) {
	this.name = name;
	this.listener = listener;
	this.delivery = delivery;
    }

    /**
     * Returns a collection containing the client sessions joined to
     * the channel represented by this state.
     */
    Collection<ClientSession> getSessions() {
	Collection<ClientSession> collection = new ArrayList<ClientSession>();
	for (ClientSession session : sessions.keySet()) {
	    collection.add(session);
	}
	return collection;
    }

    /* -- Implement Object -- */

    /** {@inheritDoc} */
    public boolean equals(Object obj) {
	if (this == obj) {
	    return true;
	} else if (obj.getClass() == this.getClass()) {
	    ChannelState state = (ChannelState) obj;
	    return
		name.equals(state.name) &&
		delivery.equals(state.delivery) &&
		listener.equals(state.listener);
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
    }
}
