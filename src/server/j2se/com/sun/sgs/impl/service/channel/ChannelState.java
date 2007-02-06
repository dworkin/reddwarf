package com.sun.sgs.impl.service.channel;

import com.sun.sgs.app.ChannelListener;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.Delivery;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.impl.util.WrappedSerializable;
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

    /** The listener for this channel, or null. */
    private WrappedSerializable<ChannelListener> channelListener;

    /** The delivery requirement for messages sent on this channel. */
    final Delivery delivery;

    /**
     * A map whose keys are the client sessions joined to this channel
     * and whose values are per-session ChannelListeners (null values
     * allowed).
     */
    private final
	Map<ClientSession, WrappedSerializable<ChannelListener>> listeners =
	    new HashMap<ClientSession, WrappedSerializable<ChannelListener>>();

    /**
     * Constructs an instance of this class with the specified name,
     * listener, and delivery requirement.
     */
    ChannelState(String name, ChannelListener listener, Delivery delivery) {
	this.name = name;
	this.channelListener =
	    listener != null ?
	    new WrappedSerializable<ChannelListener>(listener) :
	    null;
	this.delivery = delivery;
    }

    /**
     * Returns a collection containing the client sessions joined to
     * the channel represented by this state.
     */
    Set<ClientSession> getSessions() {
	Set<ClientSession> collection = new HashSet<ClientSession>();
	for (ClientSession session : listeners.keySet()) {
	    collection.add(session);
	}
	return collection;
    }

    /**
     * Returns a collection containing the client sessions joined to
     * the channel represented by this state, excluding the session
     * with the given sessionId.
     * 
     * @param sessionId the sessionId to exclude
     */
    Set<ClientSession> getSessionsExcludingId(byte[] sessionId) {
	Set<ClientSession> collection = new HashSet<ClientSession>();
	for (ClientSession session : listeners.keySet()) {
            try {
                if (! sessionId.equals(session.getSessionId())) {
                    collection.add(session);
                }
            } catch (IllegalStateException e) {
                // skip disconnected sessions
            }
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
	return listeners.containsKey(session);
    }

    boolean hasSessions() {
	return !listeners.isEmpty();
    }

    void addSession(ClientSession session, ChannelListener listener) {
	WrappedSerializable<ChannelListener> wrappedListener =
	    listener != null ?
	    new WrappedSerializable<ChannelListener>(listener) :
	    null;
	
	listeners.put(session, wrappedListener);
    }

    void removeSession(ClientSession session) {
	WrappedSerializable<ChannelListener> listener =
	    listeners.remove(session);
	if (listener != null) {
	    listener.remove();
	}
    }

    void removeAllSessions() {
	for (WrappedSerializable<ChannelListener> listener :
	     listeners.values())
	{
	    if (listener != null) {
		listener.remove();
	    }
	}
	listeners.clear();
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
    }
}
