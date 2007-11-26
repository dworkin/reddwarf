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
import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.kernel.TaskOwnerImpl;
import com.sun.sgs.impl.sharedutil.HexDumper;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.MessageBuffer;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.impl.util.AbstractKernelRunnable;
import com.sun.sgs.impl.util.AbstractService;
import com.sun.sgs.impl.util.Exporter;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.protocol.simple.SimpleSgsProtocol;
import com.sun.sgs.service.ClientSessionService;
import com.sun.sgs.service.Node;
import com.sun.sgs.service.ProtocolMessageListener;
import com.sun.sgs.service.RecoveryCompleteFuture;
import com.sun.sgs.service.RecoveryListener;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.service.TransactionRunner;
import com.sun.sgs.service.WatchdogService;
import java.util.HashSet;
import java.util.Iterator;
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
    
    /** The watchdog service. */
    private final WatchdogService watchdogService;

    /** The client session service. */
    private final ClientSessionService sessionService;

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
    public Channel createChannel(Delivery delivery) {
	try {
	    Channel channel = ChannelImpl.newInstance(delivery);
	    logger.log(Level.FINEST, "createChannel returns {0}", channel);
	    return channel;
	    
	} catch (RuntimeException e) {
	    logger.logThrow(Level.FINEST, e, "createChannel:{0} throws");
	    throw e;
	}
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
				ChannelImpl.removeSessionFromAllChannels(session);
			    }
			}),
		     new TaskOwnerImpl(identity, taskOwner.getContext()));
	    }
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
     * Throws {@code TransactionNotActiveException} if a transaction
     * is not currently active.
     */
    static void checkContext() {
	txnProxy.getCurrentTransaction();
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
				ChannelImpl.removeSessionFromAllChannels(
				    nodeId, sessionId);
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
		ChannelImpl.getSessionIdsAnyChannel(dataService, nodeId);
	    while (iter.hasNext()) {
		sessionIds.add(iter.next());
	    }
	}

	Set<byte[]> getSessionIds() {
	    return sessionIds;
	}
    }

    /**
     * Removes channel server proxy and binding for the specified node.
     */
    private void removeChannelServerProxy(long nodeId) {
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
