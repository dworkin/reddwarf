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

package com.sun.sgs.impl.protocol.simple;

import com.sun.sgs.auth.Identity;
import com.sun.sgs.auth.IdentityCoordinator;
import com.sun.sgs.impl.auth.NamePasswordCredentials;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.impl.util.AbstractKernelRunnable;
import com.sun.sgs.impl.util.AbstractService;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.RecurringTaskHandle;
import com.sun.sgs.nio.channels.AsynchronousChannelGroup;
import com.sun.sgs.nio.channels.AsynchronousServerSocketChannel;
import com.sun.sgs.nio.channels.AsynchronousSocketChannel;
import com.sun.sgs.nio.channels.CompletionHandler;
import com.sun.sgs.nio.channels.IoFuture;
import com.sun.sgs.nio.channels.spi.AsynchronousChannelProvider;
import com.sun.sgs.protocol.ProtocolAcceptor;
import com.sun.sgs.protocol.ProtocolListener;
import com.sun.sgs.protocol.SessionProtocol;
import com.sun.sgs.protocol.SessionProtocolHandler;
import com.sun.sgs.protocol.simple.SimpleSgsProtocol;
import com.sun.sgs.service.TransactionProxy;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.security.auth.login.LoginException;

/**
 * A protocol acceptor for connections that speak the {@link SimpleSgsProtocol}.
 */
public class SimpleSgsProtocolAcceptor
    extends AbstractService
    implements ProtocolAcceptor
{
    /** The package name. */
    public static final String PKG_NAME = "com.sun.sgs.impl.protocol.simple";
    
    /** The logger for this class. */
    private static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(PKG_NAME + "acceptor"));

    /**
     * The server listen address property.
     * This is the host interface we are listening on. Default is listen
     * on all interfaces.
     */
    private static final String LISTEN_HOST_PROPERTY =
        PKG_NAME + ".listen.address";
    
    /** The name of the acceptor backlog property. */
    private static final String ACCEPTOR_BACKLOG_PROPERTY =
        PKG_NAME + ".acceptor.backlog";

    /** The default acceptor backlog (&lt;= 0 means default). */
    private static final int DEFAULT_ACCEPTOR_BACKLOG = 0;

    /** The name of the read buffer size property. */
    private static final String READ_BUFFER_SIZE_PROPERTY =
        PKG_NAME + ".buffer.read.max";

    /** The default read buffer size: {@value #DEFAULT_READ_BUFFER_SIZE} */
    private static final int DEFAULT_READ_BUFFER_SIZE = 128 * 1024;
    
    /** The name of the disconnect delay property. */
    private static final String DISCONNECT_DELAY_PROPERTY =
	PKG_NAME + ".disconnect.delay";
    
    /** The time (in milliseconds) that a disconnecting connection is
     * allowed before this service forcibly disconnects it.
     */
    private static final long DEFAULT_DISCONNECT_DELAY = 1000;

    /** The identity manager. */
    private final IdentityCoordinator identityManager;
    
    /** The port for accepting connections. */
    private final int appPort;

    /** The read buffer size for new connections. */
    private final int readBufferSize;

    /** The disconnect delay (in milliseconds) for disconnecting sessions. */
    private final long disconnectDelay;

    /** The protocol listener. */
    private volatile ProtocolListener protocolListener;

    /** The async channel group for this service. */
    private final AsynchronousChannelGroup asyncChannelGroup;
    
    /** The acceptor for listening for new connections. */
    private final AsynchronousServerSocketChannel acceptor;
    
    /** The currently-active accept operation, or {@code null} if none. */
    volatile IoFuture<?, ?> acceptFuture;

    /** The acceptor listener. */
    private final AcceptorListener acceptorListener =
	new AcceptorListener();
    
    /** The map of disconnecting {@code ClientSessionHandler}s, keyed by
     * the time the connection should expire.
     */
    private final ConcurrentSkipListMap<Long, SessionProtocol>
	disconnectingHandlersMap =
	    new ConcurrentSkipListMap<Long, SessionProtocol>();

    /** The handle for the task that monitors disconnecting client sessions. */
    private RecurringTaskHandle monitorDisconnectingSessionsTaskHandle;

    /**
     * Constructs an instance with the specified {@code properties},
     * {@code systemRegistry}, and {@code txnProxy}.
     *
     * @param	properties the configuration properties
     * @param	systemRegistry the system registry
     * @param	txnProxy a transaction proxy
     *
     * @throws	Exception if a problem occurs
     */
    public SimpleSgsProtocolAcceptor(Properties properties,
				     ComponentRegistry systemRegistry,
				     TransactionProxy txnProxy)
	throws Exception
    {
	super(properties, systemRegistry, txnProxy, logger);
	
	logger.log(Level.CONFIG,
		   "Creating SimpleSgsProtcolAcceptor properties:{0}",
		   properties);

	
	PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);
	try {
            String hostAddress =
		properties.getProperty(LISTEN_HOST_PROPERTY);
            appPort = wrappedProps.getRequiredIntProperty(
                StandardProperties.APP_PORT, 1, 65535);
	    int acceptorBacklog = wrappedProps.getIntProperty(
	                ACCEPTOR_BACKLOG_PROPERTY, DEFAULT_ACCEPTOR_BACKLOG);
            readBufferSize = wrappedProps.getIntProperty(
                READ_BUFFER_SIZE_PROPERTY, DEFAULT_READ_BUFFER_SIZE,
                8192, Integer.MAX_VALUE);
	    disconnectDelay = wrappedProps.getLongProperty(
		DISCONNECT_DELAY_PROPERTY, DEFAULT_DISCONNECT_DELAY,
		200, Long.MAX_VALUE);
	    identityManager =
		systemRegistry.getComponent(IdentityCoordinator.class);
	    
	    InetSocketAddress listenAddress =
                hostAddress == null ?
		new InetSocketAddress(appPort) :
		new InetSocketAddress(hostAddress, appPort);
	    AsynchronousChannelProvider provider =
		// TODO fetch from config
		AsynchronousChannelProvider.provider();
	    asyncChannelGroup =
		// TODO fetch from config
		provider.openAsynchronousChannelGroup(
 		    Executors.newCachedThreadPool());
	    acceptor =
		provider.openAsynchronousServerSocketChannel(asyncChannelGroup);
	    try {
		acceptor.bind(listenAddress, acceptorBacklog);
		if (logger.isLoggable(Level.CONFIG)) {
		    logger.log(
		        Level.CONFIG, "bound to port:{0,number,#}",
			getListenPort());
		}
	    } catch (Exception e) {
		logger.logThrow(Level.WARNING, e,
				"acceptor failed to listen on {0}",
				listenAddress);
		try {
		    acceptor.close();
                } catch (IOException ioe) {
                    logger.logThrow(Level.WARNING, ioe,
                        "problem closing acceptor");
                }
		throw e;
	    }

	    /*
	     * Set up recurring task to monitor disconnecting client sessions.
	     */
	    monitorDisconnectingSessionsTaskHandle =
		taskScheduler.scheduleRecurringTask(
 		    new MonitorDisconnectingSessionsTask(),
		    taskOwner, System.currentTimeMillis(),
		    Math.max(disconnectDelay, DEFAULT_DISCONNECT_DELAY) / 2);
	    monitorDisconnectingSessionsTaskHandle.start();
	    
	    // TBD: check service version?
	    
	} catch (Exception e) {
	    if (logger.isLoggable(Level.CONFIG)) {
		logger.logThrow(
		    Level.CONFIG, e,
		    "Failed to create SimpleSgsProtcolAcceptor");
	    }
	    throw e;
	}
    }

    /* -- Implement AbstractService -- */
    
    /** {@inheritDoc} */
    protected void handleServiceVersionMismatch(
	Version oldVersion, Version currentVersion)
    {
	throw new IllegalStateException(
	    "unable to convert version:" + oldVersion +
	    " to current version:" + currentVersion);
    }
    
    /** {@inheritDoc} */
    public void doReady() {
    }
    
    /** {@inheritDoc} */
    public void doShutdown() {
	final IoFuture<?, ?> future = acceptFuture;
	acceptFuture = null;
	if (future != null) {
	    future.cancel(true);
	}
	
	if (acceptor != null) {
	    try {
		acceptor.close();
	    } catch (IOException e) {
		logger.logThrow(Level.FINEST, e, "closing acceptor throws");
		// swallow exception
	    } 
	}
	
	if (asyncChannelGroup != null) {
	    asyncChannelGroup.shutdown();
	    boolean groupShutdownCompleted = false;
	    try {
		groupShutdownCompleted =
		    asyncChannelGroup.awaitTermination(1, TimeUnit.SECONDS);
	    } catch (InterruptedException e) {
		logger.logThrow(Level.FINEST, e,
				"shutdown acceptor interrupted");
		Thread.currentThread().interrupt();
	    }
	    if (!groupShutdownCompleted) {
		logger.log(Level.WARNING, "forcing async group shutdown");
		try {
		    asyncChannelGroup.shutdownNow();
		} catch (IOException e) {
		    logger.logThrow(Level.FINEST, e,
				    "shutdown acceptor throws");
		    // swallow exception
		}
	    }
	}
	logger.log(Level.FINEST, "acceptor shutdown");

	if (monitorDisconnectingSessionsTaskHandle != null) {
	    try {
		monitorDisconnectingSessionsTaskHandle.cancel();
	    } finally {
		monitorDisconnectingSessionsTaskHandle = null;
	    }
	}
	    
	disconnectingHandlersMap.clear();
    }

    /* -- Implement ProtocolAcceptor -- */

    /** {@inheritDoc} */
    public void accept(ProtocolListener protocolListener) {
	if (protocolListener == null) {
	    throw new NullPointerException("null protocolListener");
	}
	this.protocolListener = protocolListener;
	accept();
    }

    /** {@inheritDoc} */
    public void close() {
	shutdown();
    }

    /* -- Public methods -- */

    /**
     * Returns the port this service is listening on for incoming
     * client session connections.
     *
     * @return the port this service is listening on
     * @throws IOException if an IO problem occurs
     */
    public int getListenPort() throws IOException {
        return ((InetSocketAddress) acceptor.getLocalAddress()).getPort();
    }
    
    /* -- Package access methods -- */

    /**
     * Returns the authenticated identity for the specified {@code name} and
     * {@code password}.
     *
     * @param	name a name
     * @param	password a password
     * @return	the authenticated identity
     * @throws	LoginException if a problem occurs authenticating the name and
     *		password 
     */
    Identity authenticate(String name, String password) throws LoginException {
	return identityManager.authenticateIdentity(
	    new NamePasswordCredentials(name, password.toCharArray()));
    }

    /**
     * Adds the specified {@code protocol} to the map containing {@code
     * SessionProtocol}s that are disconnecting.  The map is keyed by
     * connection expiration time.  The connection will expire after a fixed
     * delay and will be forcibly terminated if the client hasn't already
     * closed the connection.
     *
     * @param	protocol a {@code SessionProtocol} that is disconnecting
     */
    void monitorDisconnection(SessionProtocol protocol) {
	disconnectingHandlersMap.put(
	    System.currentTimeMillis() + disconnectDelay,  protocol);
    }
    
    /**
     * Schedules a non-durable, non-transactional {@code task}.
     *
     * @param	task a non-durable, non-transactional task
     */
    void scheduleNonTransactionalTask(KernelRunnable task) {
        taskScheduler.scheduleTask(task, taskOwner);
    }
    
    /* -- Private methods and classes -- */

    /**
     * Asynchronously accepts the next connection.
     */
    private void accept() {
	    
	acceptFuture = acceptor.accept(acceptorListener);
	SocketAddress addr = null;
	try {
	    addr = acceptor.getLocalAddress();
	} catch (IOException ioe) {
	    // ignore
	}
	
	if (logger.isLoggable(Level.CONFIG)) {
	    logger.log(Level.CONFIG, "listening on {0}", addr);
	}
    }

    /** A completion handler for accepting connections. */
    private class AcceptorListener
	implements CompletionHandler<AsynchronousSocketChannel, Void>
    {
	/** Handle new connection or report failure. */
	public void completed(IoFuture<AsynchronousSocketChannel, Void> result)
	{
	    try {
		try {
		    AsynchronousSocketChannel socketChannel =
			result.getNow();
		    logger.log(Level.FINER, "Accepted {0}", socketChannel);
		    
		    /*
		     * The protocol will call the ProtocolListener's
		     * newLogin method if the authentication succeeds.
		     */
		    new SimpleSgsProtocolImpl(
			protocolListener,
			SimpleSgsProtocolAcceptor.this,
			socketChannel, readBufferSize);
		    
		    // Resume accepting connections
		    accept();
		    
		} catch (ExecutionException e) {
		    throw (e.getCause() == null) ? e : e.getCause();
		}
	    } catch (CancellationException e) {               
		logger.logThrow(Level.FINE, e, "acceptor cancelled"); 
		//ignore
	    } catch (Throwable e) {
		SocketAddress addr = null;
		try {
		    addr = acceptor.getLocalAddress();
		} catch (IOException ioe) {
		    // ignore
		}
		
		logger.logThrow(
				Level.SEVERE, e, "acceptor error on {0}", addr);
		
		// TBD: take other actions, such as restarting acceptor?
	    }
	}
    }

    /**
     * A task to monitor disconnecting sessions to ensure that their
     * associated connections are closed by the client in a timely manner.
     * If a connection is not terminated by the expiration time, then the
     * connection is forcibly closed.
     */
    private class MonitorDisconnectingSessionsTask
	extends AbstractKernelRunnable
    {
	/** Constructs and instance. */
	MonitorDisconnectingSessionsTask() {
	    super(null);
	}
	
	/** {@inheritDoc} */
	public void run() {
	    long now = System.currentTimeMillis();
	    if (!disconnectingHandlersMap.isEmpty() &&
		disconnectingHandlersMap.firstKey() < now) {

		Map<Long, SessionProtocol> expiredSessions = 
		    disconnectingHandlersMap.headMap(now);
		for (SessionProtocol protocol : expiredSessions.values()) {
		    try {
			protocol.close();
		    } catch (IOException e) {
		    }
		}
		expiredSessions.clear();
	    }
	}
    }
}
