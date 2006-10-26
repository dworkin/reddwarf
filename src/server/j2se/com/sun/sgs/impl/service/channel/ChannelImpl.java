package com.sun.sgs.impl.service.channel;

import com.sun.sgs.app.Channel;
import com.sun.sgs.app.ChannelListener;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.Delivery;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Collections;

/**
 * Channel implementation for use within a single transaction
 * specified by the context passed during construction.
 */
final class ChannelImpl implements Channel {

    private final Context context;
    private final ChannelState state;
    private boolean channelClosed = false;

    /**
     * Constructs an instance of this class with the specified context
     * and channel state.
     *
     * @param context a context
     * @param state a channel state
     */
    ChannelImpl(Context context, ChannelState state) {
	this.state =  state;
	this.context = context;
    }

    /** {@inheritDoc} */
    public String getName() {
	checkContext();
	return state.name;
    }

    /** {@inheritDoc} */
    public Delivery getDeliveryRequirement() {
	checkContext();
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
    }

    /** {@inheritDoc} */
    public void leaveAll() {
	checkClosed();
	if (!state.sessions.isEmpty()) {
	    context.dataService.markForUpdate(state);
	    state.sessions.clear();
	}
    }

    /** {@inheritDoc} */
    public boolean hasSessions() {
	checkClosed();
	return !state.sessions.isEmpty();
    }

    /** {@inheritDoc} */
    public Collection<ClientSession> getSessions() {
	checkClosed();
	return Collections.unmodifiableCollection(state.sessions.keySet());
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
