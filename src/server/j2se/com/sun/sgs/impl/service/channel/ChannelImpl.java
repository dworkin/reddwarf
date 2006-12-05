package com.sun.sgs.impl.service.channel;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.Channel;
import com.sun.sgs.app.ChannelListener;
import com.sun.sgs.app.ChannelManager;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.Delivery;
import com.sun.sgs.impl.util.LoggerWrapper;
import com.sun.sgs.kernel.KernelRunnable;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Channel implementation for use within a single transaction
 * specified by the context passed during construction.
 */
final class ChannelImpl implements Channel, Serializable {
    private static final long serialVersionUID = 1L;

    /** The logger for this class. */
    final static LoggerWrapper logger =
	new LoggerWrapper(
	    Logger.getLogger(ChannelImpl.class.getName()));

    /** Transaction-related context information. */
    final Context context;

    /** Persistent channel state. */
    private final ChannelState state;

    /** Flag that is 'true' if this channel is closed. */
    private boolean channelClosed = false;

    /**
     * Constructs an instance of this class with the specified context
     * and channel state.
     *
     * @param context a context
     * @param state a channel state
     */
    ChannelImpl(Context context, ChannelState state) {
	if (logger.isLoggable(Level.FINER)) {
	    logger.log(Level.FINER, "Created ChannelImpl context:{0} state:{1}",
		       context, state);
	}
	this.state =  state;
	this.context = context;
    }

    /* -- Implement Channel -- */
    
    /** {@inheritDoc} */
    public String getName() {
	checkContext();
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST, "getName returns {0}", state.name);
	}
	return state.name;
    }

    /** {@inheritDoc} */
    public Delivery getDeliveryRequirement() {
	checkContext();
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST,
		       "getDeliveryRequirement returns {0}", state.delivery);
	}
	return state.delivery;
    }

    /** {@inheritDoc} */
    public void join(ClientSession session, ChannelListener listener) {
	checkClosed();
	if (session == null) {
	    throw new NullPointerException("null session");
	}
	if (listener != null && !(listener instanceof Serializable)) {
	    throw new IllegalArgumentException("listener not serializable");
	}
	if (state.sessions.get(session) == null) {
	    context.dataService.markForUpdate(state);
	    state.sessions.put(session, listener);
	}
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST, "join session:{0} returns", session);
	}
    }

    /** {@inheritDoc} */
    public void leave(ClientSession session) {
	checkClosed();
	if (session == null) {
	    throw new NullPointerException("null client session");
	}
	if (state.sessions.get(session) != null) {
	    context.dataService.markForUpdate(state);
	    state.sessions.remove(session);
	}
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST, "leave session:{0} returns", session);
	}
    }

    /** {@inheritDoc} */
    public void leaveAll() {
	checkClosed();
	if (!state.sessions.isEmpty()) {
	    context.dataService.markForUpdate(state);
	    state.sessions.clear();
	}
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST, "leaveAll returns");
	}
    }

    /** {@inheritDoc} */
    public boolean hasSessions() {
	checkClosed();
	boolean hasSessions = !state.sessions.isEmpty();
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST, "hasSessions returns {0}", hasSessions);
	}
	return hasSessions;
    }

    /** {@inheritDoc} */
    public Collection<ClientSession> getSessions() {
	checkClosed();
	Collection<ClientSession> sessions =
	    Collections.unmodifiableCollection(state.sessions.keySet());	
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST, "getSessions returns {0}", sessions);
	}
	return sessions;
    }

    /** {@inheritDoc} */
    public void send(byte[] message) {
	checkClosed();
	if (message == null) {
	    throw new NullPointerException("null message");
	}
	scheduleSend(state.getSessions(), message);
    }

    /** {@inheritDoc} */
    public void send(ClientSession recipient, byte[] message) { 
	checkClosed();
	if (recipient == null) {
	    throw new NullPointerException("null recipient");
	} else if (message == null) {
	    throw new NullPointerException("null message");
	}
	
	Collection<ClientSession> sessions = new ArrayList<ClientSession>();
	sessions.add(recipient);
	scheduleSend(sessions, message);
    }

    /** {@inheritDoc} */
    public void send(Collection<ClientSession> recipients,
		     byte[] message)
    {
	checkClosed();
	if (recipients == null) {
	    throw new NullPointerException("null recipients");
	} else if (message == null) {
	    throw new NullPointerException("null message");
	}

	if (recipients.isEmpty()) {
	    return;
	}
	scheduleSend(recipients, message);
    }

    /** {@inheritDoc} */
    public void close() {
	checkContext();
	channelClosed = true;
	context.removeChannel(state.name);
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST, "close returns");
	}
    }

    /* -- Implement Object -- */

    /** {@inheritDoc} */
    public boolean equals(Object obj) {
	return
	    (this == obj) ||
	    (obj.getClass() == this.getClass() &&
	     state.equals(((ChannelImpl) obj).state));
    }

    /** {@inheritDoc} */
    public int hashCode() {
	return state.name.hashCode();
    }

    /** {@inheritDoc} */
    public String toString() {
	return getClass().getName() + "[" + state.name + "]";
    }

    /* -- Serialization methods -- */

    private Object writeReplace() {
	return new External(state.name);
    }

    /**
     * Represents the persistent represntation for a channel (just its name).
     */
    private final static class External implements Serializable {

	private final static long serialVersionUID = 1L;

	private final String name;

	External(String name) {
	    this.name = name;
	}

	private void writeObject(ObjectOutputStream out) throws IOException {
	    out.defaultWriteObject();
	}

	private void readObject(ObjectInputStream in)
	    throws IOException, ClassNotFoundException
	{
	    in.defaultReadObject();
	}

	private Object readResolve() throws ObjectStreamException {
	    try {
		ChannelManager cm = AppContext.getChannelManager();
		Channel channel = cm.getChannel(name);
		return channel;
	    } catch (RuntimeException e) {
		throw (InvalidObjectException)
		    new InvalidObjectException(e.getMessage()).initCause(e);
	    }
	}
    }

    /* -- other methods and classes -- */

    /**
     * Checks that this channel's context is currently active,
     * throwing TransactionNotActiveException if it isn't.
     */
    private void checkContext() {
	ChannelServiceImpl.checkContext(context);
    }

    /**
     * Checks the context, and then checks that this channel is not
     * closed, throwing an IllegalStateException if the channel is
     * closed.
     */
    private void checkClosed() {
	checkContext();
	if (channelClosed) {
	    throw new IllegalStateException("channel is closed");
	}
    }

    private void scheduleSend(
	final Collection<ClientSession> sessions, final byte[] message)
    {
	/*
	 * Schedule a non-durable task that runs outside a transaction
	 * that will enqueue work (sending the specified message to
	 * the specified sessions) for the channel manager.
	 */
	context.taskService.scheduleNonDurableTask(
		new SendTask(sessions, message));
    }

    /**
     * Task for sending a message to a set of clients.
     */
    final class SendTask implements KernelRunnable {

	private final Collection<byte[]> clients = new ArrayList<byte[]>();
	final byte[] message;

	SendTask(Collection<ClientSession> sessions, byte[] message) {
	    for (ClientSession session : sessions) {
		this.clients.add(session.getSessionId());
	    }
	    this.message = message;
	}

	public void run() throws Exception {
	    for (byte[] id : clients) {
		// TBI: send message to client specified by identifier...
	    }
	}
    }
}
