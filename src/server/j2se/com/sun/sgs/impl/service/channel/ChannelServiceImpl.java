/*
 * Copyright 2007 Sun Microsystems, Inc.
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
import com.sun.sgs.app.ChannelManager;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.Delivery;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.kernel.TaskOwnerImpl;
import com.sun.sgs.impl.sharedutil.HexDumper;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.MessageBuffer;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.impl.util.AbstractKernelRunnable;
import com.sun.sgs.impl.util.AbstractService;
import com.sun.sgs.impl.util.Exporter;
import com.sun.sgs.impl.util.TransactionContext;
import com.sun.sgs.impl.util.TransactionContextFactory;
import com.sun.sgs.impl.util.TransactionContextMap;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.protocol.simple.SimpleSgsProtocol;
import com.sun.sgs.service.ClientSessionService;
import com.sun.sgs.service.Node;
import com.sun.sgs.service.ProtocolMessageListener;
import com.sun.sgs.service.RecoveryCompleteFuture;
import com.sun.sgs.service.RecoveryListener;
import com.sun.sgs.service.Service;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.service.TransactionRunner;
import com.sun.sgs.service.WatchdogService;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Simple ChannelService implementation. <p>
 * 
 * The {@link #ChannelServiceImpl constructor} requires the <a
 * href="../../../app/doc-files/config-properties.html#com.sun.sgs.app.name">
 * <code>com.sun.sgs.app.name</code></a> property. <p>
 */
public class ChannelServiceImpl
    extends AbstractService implements ChannelManager
{
    /** The name of this class. */
    private static final String CLASSNAME = ChannelServiceImpl.class.getName();

    private static final String PKG_NAME = "com.sun.sgs.impl.service.channel";

    /** The prefix of a session key which maps to its channel membership. */
    private static final String SESSION_PREFIX = PKG_NAME + ".session.";
    
    /** The prefix of a channel key which maps to its channel state. */
    private static final String CHANNEL_STATE_PREFIX = PKG_NAME + ".state.";
    
    /** The logger for this class. */
    private static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(PKG_NAME));

    /** The name of the server port property. */
    private static final String SERVER_PORT_PROPERTY =
	PKG_NAME + ".server.port";
	
    /** The default server port. */
    private static final int DEFAULT_SERVER_PORT = 0;
    
    /**
     * The transaction context map, or null if configure has not been called.
     */
    private static volatile TransactionContextMap<Context> contextMap = null;

    /** List of contexts that have been prepared (non-readonly) or commited. */
    private final List<Context> contextList = new LinkedList<Context>();

    /** The watchdog service. */
    private final WatchdogService watchdogService;

    /** The client session service. */
    private final ClientSessionService sessionService;

    /** The transaction context factory. */
    private final TransactionContextFactory<Context> contextFactory;
    
    /** The exporter for the ChannelServer. */
    private final Exporter<ChannelServer> exporter;

    /** The ChannelServer remote interface implementation. */
    private final ChannelServerImpl serverImpl;
	
    /** The proxy for the ChannelServer. */
    private final ChannelServer serverProxy;

    /** The ID for the local node. */
    private final long localNodeId;

    /**
     * Constructs an instance of this class with the specified {@code
     * properties}, {@code systemRegistry}, and {@code txnProxy}.
     *
     * @param	properties service properties
     * @param	systemRegistry system registry
     * @param	txnProxy transaction proxy
     *
     * @throws Exception if a problem occurs when creating the service
     */
    public ChannelServiceImpl(Properties properties,
			      ComponentRegistry systemRegistry,
			      TransactionProxy txnProxy)
	throws Exception
    {
	super(properties, systemRegistry, txnProxy, logger);
	
	logger.log(
	    Level.CONFIG, "Creating ChannelServiceImpl properties:{0}",
	    properties);
	PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);

	try {
	    synchronized (ChannelServiceImpl.class) {
		if (ChannelServiceImpl.contextMap == null) {
		    contextMap = new TransactionContextMap<Context>(txnProxy);
		}
	    }
	    contextFactory = new ContextFactory(contextMap);
	    watchdogService = txnProxy.getService(WatchdogService.class);
	    sessionService = txnProxy.getService(ClientSessionService.class);
	    localNodeId = watchdogService.getLocalNodeId();
	    
	    /*
	     * Export the ChannelServer.
	     */
	    int serverPort = wrappedProps.getIntProperty(
		SERVER_PORT_PROPERTY, DEFAULT_SERVER_PORT, 0, 65535);
	    serverImpl = new ChannelServerImpl();
	    exporter = new Exporter<ChannelServer>(ChannelServer.class);
	    try {
		int port = exporter.export(serverImpl, serverPort);
		serverProxy = exporter.getProxy();
		logger.log(
		    Level.CONFIG,
		    "ChannelServer export successful. port:{0,number,#}", port);
	    } catch (Exception e) {
		try {
		    exporter.unexport();
		} catch (RuntimeException re) {
		}
		throw e;
	    }

	    /*
	     * Store the ChannelServer proxy in the data store.
	     */
	    runTransactionally(
		new AbstractKernelRunnable() {
		    public void run() {
			dataService.setServiceBinding(
			    getChannelServerKey(localNodeId),
			    new ChannelServerWrapper(serverProxy));
		    }}
		);

	    /*
	     * Add listeners for handling recovery and for handling
	     * protocol messages for the channel service.
	     */
	    watchdogService.addRecoveryListener(
		new ChannelServiceRecoveryListener());
	    sessionService.registerProtocolMessageListener(
		SimpleSgsProtocol.CHANNEL_SERVICE,
		new ChannelProtocolMessageListener());

	} catch (Exception e) {
	    if (logger.isLoggable(Level.CONFIG)) {
		logger.logThrow(
		    Level.CONFIG, e, "Failed to create ChannelServiceImpl");
	    }
	    throw e;
	}
    }
 
    /* -- Implement AbstractService methods -- */

    /** {@inheritDoc} */
    protected void doReady() {
    }

    /** {@inheritDoc} */
    protected void doShutdown() {
	logger.log(Level.FINEST, "shutdown");
	
	try {
	    exporter.unexport();
	} catch (RuntimeException e) {
	    logger.logThrow(Level.FINEST, e, "unexport server throws");
	    // swallow exception
	}
    }
    
    /* -- Implement ChannelManager -- */

    /** {@inheritDoc} */
    public Channel createChannel(String name, Delivery delivery) {
	try {
	    if (name == null) {
		throw new NullPointerException("null name");
	    }
	    Context context = contextFactory.joinTransaction();
	    Channel channel = context.createChannel(name, delivery);
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(
		    Level.FINEST, "createChannel name:{0} returns {1}",
		    name, channel);
	    }
	    return channel;
	    
	} catch (RuntimeException e) {
	    if (logger.isLoggable(Level.FINEST)) {
		logger.logThrow(
		    Level.FINEST, e, "createChannel name:{0} throws", name);
	    }
	    throw e;
	}
    }

    /** {@inheritDoc} */
    public Channel getChannel(String name) {
	try {
	    if (name == null) {
		throw new NullPointerException("null name");
	    }
	    Context context = contextFactory.joinTransaction();
	    Channel channel = context.getChannel(name);
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(
		    Level.FINEST, "getChannel name:{0} returns {1}",
		    name, channel);
	    }
	    return channel;
	    
	} catch (RuntimeException e) {
	    if (logger.isLoggable(Level.FINEST)) {
		logger.logThrow(
		    Level.FINEST, e, "getChannel name:{0} throws", name);
	    }
	    throw e;
	}
    }

    /* -- Implement TransactionContextFactory -- */
       
    private class ContextFactory extends TransactionContextFactory<Context> {
	ContextFactory(TransactionContextMap<Context> contextMap) {
	    super(contextMap);
	}
	
	public Context createContext(Transaction txn) {
	    return new Context(txn);
	}
    }

    /**
     * Iterates through the context list, in order, to flush any
     * committed changes.  During iteration, this method invokes
     * {@code flush} on the {@code Context} returned by {@code next}.
     * Iteration ceases when either a context's {@code flush} method
     * returns {@code false} (indicating that the transaction
     * associated with the context has not yet committed) or when
     * there are no more contexts in the context list.
     */
    private void flushContexts() {
	synchronized (contextList) {
	    Iterator<Context> iter = contextList.iterator();
	    while (iter.hasNext()) {
		Context context = iter.next();
		if (context.flush()) {
		    iter.remove();
		} else {
		    break;
		}
	    }
	}
    }

    /**
     * Checks that the specified context is currently active, throwing
     * TransactionNotActiveException if it isn't.
     */
    static void checkContext(Context context) {
	getContextMap().checkContext(context);
    }

    /**
     * Returns the transaction context map.
     *
     * @return the transaction context map
     */
    private synchronized static TransactionContextMap<Context> getContextMap()
    {
	if (contextMap == null) {
	    throw new IllegalStateException("Service not configured");
	}
	return contextMap;
    }

    /* -- Implement ChannelServer -- */

    private final class ChannelServerImpl implements ChannelServer {
	
	/** {@inheritDoc} */
	public void join(byte[] channelId, long nodeId) {
	    callStarted();
	    try {
		throw new AssertionError("not implemented");
	    } finally {
		callFinished();
	    }
	}
	
	/** {@inheritDoc} */
	public void leave(byte[] channelId, long nodeId) {
	    callStarted();
	    try {
		throw new AssertionError("not implemented");
	    } finally {
		callFinished();
	    }
	}

	/** {@inheritDoc} */
	public void send(byte[] channelId,
			 byte[][] recipients,
			 byte[] protocolMessage,
			 Delivery delivery)
	{
	    callStarted();
	    try {
		for (int i = 0; i < recipients.length; i++) {
		    ClientSession session =
			sessionService.getLocalClientSession(recipients[i]);
		    if (session != null && session.isConnected()) {
			sessionService.sendProtocolMessageNonTransactional(
			    session, protocolMessage, delivery);
		    }
		}
			
	    } finally {
		callFinished();
	    }
	}
	
	/** {@inheritDoc} */
	public void close(byte[] channelId, long nodeId) {
	    callStarted();
	    try {
		throw new AssertionError("not implemented");
	    } finally {
		callFinished();
	    }
	}
    }
    
    /* -- Implement ProtocolMessageListener -- */

    private final class ChannelProtocolMessageListener
	implements ProtocolMessageListener
    {
	/** {@inheritDoc} */
	public void receivedMessage(final ClientSession session, byte[] message) {
	    try {
		final MessageBuffer buf = new MessageBuffer(message);
	    
		buf.getByte(); // discard version
		
		/*
		 * Handle service id.
		 */
		byte serviceId = buf.getByte();

		if (serviceId != SimpleSgsProtocol.CHANNEL_SERVICE) {
		    if (logger.isLoggable(Level.SEVERE)) {
			logger.log(
                            Level.SEVERE,
			    "expected channel service ID, got: {0}",
			    serviceId);
		    }
		    return;
		}

		/*
		 * Handle op code.
		 */
		byte opcode = buf.getByte();

		switch (opcode) {
		    
		case SimpleSgsProtocol.CHANNEL_SEND_REQUEST:

		    logger.log(
			Level.WARNING,
			"Dropping CHANNEL_SEND_REQUEST:{0}",
			HexDumper.format(message));
		    break;
		    
		default:
		    if (logger.isLoggable(Level.SEVERE)) {
			logger.log(
			    Level.SEVERE,
			    "receivedMessage session:{0} message:{1} " +
			    "unknown opcode:{2}",
			    session, HexDumper.format(message), opcode);
		    }
		    break;
		}

		if (logger.isLoggable(Level.FINEST)) {
		    logger.log(
			Level.FINEST,
			"receivedMessage session:{0} message:{1} returns",
			session, HexDumper.format(message));
		}
		
	    } catch (RuntimeException e) {
		if (logger.isLoggable(Level.SEVERE)) {
		    logger.logThrow(
			Level.SEVERE, e,
			"receivedMessage session:{0} message:{1} throws",
			session, HexDumper.format(message));
		}
	    }
	}

	/** {@inheritDoc} */
	public void disconnected(final ClientSession session) {
	    /*
	     * Schedule a transactional task to remove the
	     * disconnected session from all channels that it is
	     * currently a member of (unless the session has a null
	     * identity, which means the session was not logged in, so
	     * it can't be a member of any channel).
	     */
	    Identity identity = session.getIdentity();
	    if (identity != null) {
		taskScheduler.scheduleTask(
 		     new TransactionRunner(
			new AbstractKernelRunnable() {
			    public void run() {
				removeSessionFromAllChannels(session);
			    }
			}),
		     new TaskOwnerImpl(identity, taskOwner.getContext()));
	    }
	}
    }
    
    /**
     * Stores information relating to a specific transaction operating on
     * channels.
     *
     * <p>This context maintains an internal table that maps (for the
     * channels used in the context's associated transaction) channel
     * name to channel implementation.  To create or obtain a channel
     * within a transaction, the {@code createChannel} or {@code
     * getChannel} methods (respectively) must be called on the
     * context so that the proper channel instances are used.
     */
    final class Context extends TransactionContext {

	/**
	 * Map of channel name to transient channel impl (for those
	 * channels used during this context's associated
	 * transaction).
	 */
	private final Map<String, ChannelImpl> internalTable =
	    new HashMap<String, ChannelImpl>();

	/**
	 * Constructs a context with the specified transaction. 
	 */
	private Context(Transaction txn) {
	    super(txn);
	}

	/* -- ChannelManager methods -- */

	/**
	 * Creates a channel with the specified {@code name} and
	 * {@code delivery} requirement.  The channel's state is bound
	 * to a name composed of the channel service's prefix followed
	 * by ".state." followed by the channel name.
	 */
	Channel createChannel(String name, Delivery delivery) {
	    ChannelImpl channel =
		ChannelImpl.newInstance(name, delivery);
	    internalTable.put(name, channel);
	    return channel;
	}

	/**
	 * Returns a channel with the specified {@code name}.  If the
	 * channel is already present in the internal channel table
	 * for this transaction, then the channel is returned;
	 * otherwise, this method gets the channel's state by looking
	 * up the service binding for the channel.
	 *
	 * @param   name a channel name
	 * @return  the channel with the specified {@code name}
	 * @throws  NameNotBoundException if the channel does not exist
	 */
	Channel getChannel(String name) {
	    ChannelImpl channel = internalTable.get(name);
	    if (channel == null) {
		channel = ChannelImpl.getInstance(name);
		internalTable.put(name, channel);
	    } else if (channel.isClosed) {
		throw new NameNotBoundException(name);
	    }
	    return channel;
	}

	/**
	 * Returns a channel with the specified {@code channelId}, or
	 * {@code null} if the channel doesn't exist.  If the channel
	 * is already present in the internal channel table for this
	 * transaction, then the channel is returned.  This method
	 * uses the {@code channelId} as a {@code ManagedReference} ID
	 * to the channel's state.
	 *
	 * @return  the channel with the specified {@code channelId},
	 *	    or {@code null} if the channel doesn't exist
	 * @throws  NameNotBoundException if the channel does not exist
	 */
	ChannelImpl getChannel(byte[] channelId) {
	    assert channelId != null;
	    ChannelImpl channel = null;
	    ChannelState channelState =
		ChannelState.getInstance(channelId);
	    if (channelState != null) {
		channel = internalTable.get(channelState.name);
		if (channel == null) {
		    channel = ChannelImpl.newInstance(channelState);
		    internalTable.put(channelState.name, channel);
		}

		if (channel.isClosed) {
		    channel = null;
		}
	    }
		
	    return channel;
	}

	/* -- transaction participant methods -- */

	/**
	 * Throws a {@code TransactionNotActiveException} if this
	 * transaction is prepared.
	 */
	private void checkPrepared() {
	    if (isPrepared) {
		throw new TransactionNotActiveException("Already prepared");
	    }
	}
	
	/**
	 * Marks this transaction as prepared, and if there are
	 * pending changes, adds this context to the context list and
	 * returns {@code false}.  Otherwise, if there are no pending
	 * changes returns {@code true} indicating readonly status.
	 */
        public boolean prepare() {
	    isPrepared = true;
	    boolean readOnly = internalTable.isEmpty();
	    if (! readOnly) {
		synchronized (contextList) {
		    contextList.add(this);
		}
	    }
            return readOnly;
        }

	/**
	 * Marks this transaction as aborted, removes the context from
	 * the context list containing pending updates, and flushes
	 * all committed contexts preceding prepared ones.
	 */
	public void abort(boolean retryable) {
	    synchronized (contextList) {
		contextList.remove(this);
	    }
	    flushContexts();
	}

	/**
	 * Marks this transaction as committed and flushes all
	 * committed contexts preceding prepared ones.
	 */
	public void commit() {
	    isCommitted = true;
	    flushContexts();
        }

	/**
	 * If the context is committed, flushes channel state updates
	 * to the channel state cache and returns true; otherwise
	 * returns false.
	 */
	private boolean flush() {
	    if (isCommitted) {
		return true;
	    } else {
		return false;
	    }
	}

	/* -- other methods -- */

	/**
	 * Returns a service of the given {@code type}.
	 */
	<T extends Service> T getService(Class<T> type) {
	    return txnProxy.getService(type);
	}
    }
    
    /**
     * Returns the client session service.
     */
    static ClientSessionService getClientSessionService() {
	return txnProxy.getService(ClientSessionService.class);
    }

    /**
     * Returns the local node ID.
     */
    static long getLocalNodeId() {
	return txnProxy.getService(WatchdogService.class).getLocalNodeId();
    }

    /**
     * The {@code RecoveryListener} for handling requests to recover
     * for a failed {@code ChannelService}.
     */
    private class ChannelServiceRecoveryListener
	implements RecoveryListener
    {
	/** {@inheritDoc} */
	public void recover(Node node, RecoveryCompleteFuture future) {
	    final long nodeId = node.getId();
	    try {
		if (logger.isLoggable(Level.INFO)) {
		    logger.log(Level.INFO, "Node:{0} recovering for node:{0}",
			       localNodeId, nodeId);
		}
		/*
		 * For each session on the failed node, remove the
		 * given session from all channels it is a member of.
		 */
		GetNodeSessionIdsTask task = new GetNodeSessionIdsTask(nodeId);
		runTransactionally(task);
		
		for (final byte[] sessionId : task.getSessionIds()) {
		    if (logger.isLoggable(Level.FINEST)) {
			logger.log(
			    Level.FINEST,
			    "Removing session:{0} from all channels",
			    HexDumper.toHexString(sessionId));
		    }
		    
		    runTransactionally(
			new AbstractKernelRunnable() {
			    public void run() {
				removeSessionFromAllChannels(nodeId, sessionId);
			    }
			});
		}
		/*
		 * Remove binding to channel server proxy for failed
		 * node, and remove proxy's wrapper.
		 */
		runTransactionally(
		    new AbstractKernelRunnable() {
			public void run() {
			    removeChannelServerProxy(nodeId);
			}
		    });
		
		future.done();

	    } catch (Exception e) {
		logger.logThrow(
 		    Level.WARNING, e,
		    "Recovering for failed node:{0} throws", nodeId);
		// TBD: what should it do if it can't recover?
	    }
	}
    }

    /**
     * Task to perform recovery actions for a specific node,
     * specifically to remove all client sessions that were connected
     * to the node from all channels that those sessions were a member
     * of.
     */
    private class GetNodeSessionIdsTask extends AbstractKernelRunnable {

	private final long nodeId;
	private Set<byte[]> sessionIds = new HashSet<byte[]>();
	
	GetNodeSessionIdsTask(long nodeId) {
	    this.nodeId = nodeId;
	}

	/** {@inheritDoc} */
	public void run() {
	    Iterator<byte[]> iter =
		ChannelState.getSessionIdsAnyChannel(dataService, nodeId);
	    while (iter.hasNext()) {
		sessionIds.add(iter.next());
	    }
	}

	Set<byte[]> getSessionIds() {
	    return sessionIds;
	}
    }

    /**
     * Removes the specified client {@code session} from all channels
     * that it is currently a member of.
     */
    private void removeSessionFromAllChannels(ClientSession session) {
	long nodeId = ChannelState.getNodeId(session);
	byte[] sessionIdBytes = session.getSessionId().getBytes();
	removeSessionFromAllChannels(nodeId, sessionIdBytes);
    }
				     
    /**
     * Removes the specified client {@code session} from all channels
     * that it is currently a member of.  This method is invoked when
     * a session is disconnected from this node, gracefully or
     * otherwise, or if this node is recovering for a failed node
     * whose sessions all became disconnected.
     *
     * This method should be call within a transaction.
     */
    private void removeSessionFromAllChannels(
	long nodeId, byte[] sessionIdBytes)
    {
	Set<String> channelNames =
	     ChannelState.getChannelsForSession(
		dataService, nodeId, sessionIdBytes);
	for (String name : channelNames) {
	    try {
		ChannelImpl channel = (ChannelImpl) getChannel(name);
		channel.state.removeSession(nodeId, sessionIdBytes);
	    } catch (NameNotBoundException e) {
		logger.logThrow(Level.FINE, e, "channel removed:{0}", name);
	    }
	}
    }

    void removeChannelServerProxy(long nodeId) {
	String channelServerKey = getChannelServerKey(nodeId);
	try {
	    ChannelServerWrapper proxyWrapper =
		dataService.getServiceBinding(
		    channelServerKey, ChannelServerWrapper.class);
	    dataService.removeObject(proxyWrapper);
	} catch (NameNotBoundException e) {
	    // already removed
	    return;
	} catch (ObjectNotFoundException e) {
	}
	dataService.removeServiceBinding(channelServerKey);
    }
    
    /**
     * Returns the key for accessing the {@code ChannelServer}
     * instance (which is wrapped in a {@code ChannelServerWrapper})
     * for the specified {@code nodeId}.
     */
    static String getChannelServerKey(long nodeId) {
	return PKG_NAME + ".server." + nodeId;
    }
}
