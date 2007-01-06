package com.sun.sgs.impl.service.channel;

import com.sun.sgs.app.ChannelListener;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.Delivery;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.impl.service.session.SgsProtocol;
import com.sun.sgs.impl.util.LoggerWrapper;
import com.sun.sgs.impl.util.MessageBuffer;
import com.sun.sgs.impl.util.WrappedSerializable;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.service.SgsClientSession;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Persistent state of a channel.
 */
final class ChannelState implements ManagedObject, Serializable {

    /** The logger for this class. */
    private final static LoggerWrapper logger =
        new LoggerWrapper(
            Logger.getLogger(ChannelState.class.getName()));
    
    /** Serialization version. */
    private static final long serialVersionUID = 1L;
    
    /** The name of this channel. */
    final String name;

    /** The listener for this channel. */
    private final WrappedSerializable<ChannelListener> listener;

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
	this.listener =
	    listener != null ?
	    new WrappedSerializable<ChannelListener>(listener) :
	    null;
	this.delivery = delivery;
    }

    /**
     * Returns a collection containing the client sessions joined to
     * the channel represented by this state.
     */
    Collection<ClientSession> getSessions() {
        Collection<ClientSession> collection = new ArrayList<ClientSession>();
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
    Collection<ClientSession> getSessionsExcludingId(byte[] sessionId) {
	Collection<ClientSession> collection = new ArrayList<ClientSession>();
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

	int nameSize = MessageBuffer.getSize(name);
	MessageBuffer buf = new MessageBuffer(3 + nameSize);
	    buf.putByte(SgsProtocol.VERSION).
	    putByte(SgsProtocol.CHANNEL_SERVICE).
	    putByte(SgsProtocol.CHANNEL_JOIN).
	    putString(name);
	sendProtocolMessageOnCommit(session, buf.getBuffer());
    }

    void removeSession(ClientSession session) {
	listeners.remove(session);
        
	int nameSize = MessageBuffer.getSize(name);
	MessageBuffer buf = new MessageBuffer(3 + nameSize);
	    buf.putByte(SgsProtocol.VERSION).
	    putByte(SgsProtocol.CHANNEL_SERVICE).
	    putByte(SgsProtocol.CHANNEL_LEAVE).
	    putString(name);
	sendProtocolMessageOnCommit(session, buf.getBuffer());
    }

    void removeAll() {
	listeners.clear();
    }

    ChannelListener getListener() {
	return
	    listener != null  ?
	    listener.get(ChannelListener.class) :
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
    
    /**
     * If this transaction commits, send a protocol message to the
     * specified session, logging (but not throwing) any exception.
     */
    private void sendProtocolMessageOnCommit(ClientSession session,
            byte[] message)
    {
        try {
            ((SgsClientSession) session).sendMessageOnCommit(message, delivery);
        } catch (RuntimeException e) {
            if (logger.isLoggable(Level.FINEST)) {
                logger.logThrow(
                    Level.FINEST, e,
                    "sendProtcolMessage session:{0} message:{1} throws",
                    session, message);
            }
            // eat exception
        }
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
