package com.sun.sgs.impl.service.channel;

import com.sun.sgs.app.Channel;
import com.sun.sgs.app.ChannelListener;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.Delivery;
import com.sun.sgs.impl.util.LoggerWrapper;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Channel implementation for use within a single transaction
 * specified by the context passed during construction.
 */
final class ChannelImpl implements Channel {

    /** The logger for this class. */
    private final static LoggerWrapper logger =
	new LoggerWrapper(
	    Logger.getLogger(ChannelImpl.class.getName()));
    
    /** Transaction-related context information. */
    private final Context context;

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
	if (session == null || listener == null) {
	    throw new NullPointerException("null argument");
	}
	if (!(listener instanceof Serializable)) {
	    throw new IllegalStateException("listener not serializable");
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
    public void send(ByteBuffer message) {
	checkClosed();
	// TBI
    }

    /** {@inheritDoc} */
    public void send(ClientSession recipient, ByteBuffer message) { 
	checkClosed();
	// TBI
    }

    /** {@inheritDoc} */
    public void send(Collection<ClientSession> recipients,
		     ByteBuffer message)
    {
	checkClosed();
	// TBI
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

    /* -- other methods -- */

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
}
