package com.sun.sgs.impl.service.channel;

import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.Channel;
import com.sun.sgs.app.ChannelListener;
import com.sun.sgs.app.Delivery;
import com.sun.sgs.app.NameExistsException;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.Service;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionParticipant;
import java.util.Map;
import java.util.HashMap;

/**
 * Stores information relating to a specific transaction operating on
 * channels.
 *
 * <p>This context maintains an internal table that maps (for the
 * channels used in the context's associated transaction) channel name
 * to channel implementation.  To create, obtain, or remove a channel
 * within a transaction, the <code>createChannel</code>,
 * <code>getChannel</code>, or <code>removeChannel</code> methods
 * (respectively) must be called on the context so that the proper
 * channel instances are used.
 */
final class Context {

    /** The data service. */
    final DataService dataService;

    /** The channel service. */
    final ChannelServiceImpl channelService;

    /** The transaction. */
    final Transaction txn;

    /** Table of all channels, obtained from the data service. */
    private ChannelTable table = null;

    /**
     * Map of channel name to transient channel impl (for those channels used
     * during this context's associated transaction).
     */
    private final Map<String,Channel> internalTable =
	new HashMap<String,Channel>();

    /**
     * Creates an instance of this class with the specified data
     * service and transaction.
     */
    Context(DataService dataService,
	    ChannelServiceImpl channelService,
	    Transaction txn)
    {
	assert dataService != null && channelService != null && txn != null;
	this.dataService = dataService;
	this.channelService = channelService;
	this.txn = txn;
	this.table = dataService.getServiceBinding(
	    ChannelTable.NAME, ChannelTable.class);
    }

    /* -- ChannelManager methods -- */

    /**
     * Creates a channel with the specified name, listener, and
     * delivery requirement.
     */
    Channel createChannel(String name,
			  ChannelListener listener,
			  Delivery delivery)
    {
	assert name != null;
	if (table.get(name) != null) {
	    throw new NameExistsException(name);
	}

	ChannelState channelState = new ChannelState(name, listener, delivery);
	ManagedReference<ChannelState> ref =
	    dataService.createReference(channelState);
	dataService.markForUpdate(table);
	table.put(name, ref);
	Channel channel = new ChannelImpl(this, channelState);
	internalTable.put(name, channel);
	return channel;
    }

    /**
     * Returns a channel with the specified name.
     */
    Channel getChannel(String name) {
	assert name != null;
	Channel channel = internalTable.get(name);
	if (channel == null) {
	    ManagedReference<ChannelState> ref = table.get(name);
	    if (ref == null) {
		throw new NameNotBoundException(name);
	    }
	    ChannelState channelState = ref.get();
	    channel = new ChannelImpl(this, channelState);
	    internalTable.put(name, channel);
	}
	return channel;
    }

    /**
     * Removes the channel with the specified name.  This method is
     * called when the 'close' method is invoked on a 'ChannelImpl'.
     */
    void removeChannel(String name) {
	assert name != null;
	if (table.get(name) != null) {
	    dataService.markForUpdate(table);
	    table.remove(name);
	    internalTable.remove(name);
	}
    }
}
