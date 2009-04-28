/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
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

import com.sun.sgs.app.Delivery;
import com.sun.sgs.auth.Identity;
import com.sun.sgs.auth.IdentityCoordinator;
import com.sun.sgs.impl.auth.NamePasswordCredentials;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.impl.util.AbstractKernelRunnable;
import com.sun.sgs.impl.util.AbstractService;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.RecurringTaskHandle;
import com.sun.sgs.nio.channels.AsynchronousByteChannel;
import com.sun.sgs.protocol.ProtocolAcceptor;
import com.sun.sgs.protocol.ProtocolDescriptor;
import com.sun.sgs.protocol.ProtocolListener;
import com.sun.sgs.protocol.SessionProtocol;
import com.sun.sgs.protocol.simple.SimpleSgsProtocol;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.transport.ConnectionHandler;
import com.sun.sgs.transport.Transport;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.security.auth.login.LoginException;

/**
 * A protocol acceptor for connections that speak the {@link
 * SimpleSgsProtocol}. The {@link #SimpleSgsProtocolAcceptor constructors}
 * support the following properties: <p>
 *
 * <dl style="margin-left: 1em">
 *
 * <dt> <i>Property:</i> <code><b>
 *	{@value #TRANSPORT_PROPERTY}
 *	</b></code><br>
 *	<i>Default:</i> {@value #DEFAULT_TRANSPORT}
 *
 * <dd style="padding-top: .5em">Specifies the transport. The
 *      specified transport must support {@link Delivery#RELIABLE}.<p>
 *
 * <dt> <i>Property:</i> <code><b>
 *	{@value #READ_BUFFER_SIZE_PROPERTY}
 *	</b></code><br>
 *	<i>Default:</i> {@value #DEFAULT_READ_BUFFER_SIZE}<br>
 *      <i>Minimum:</i> {@value #MIN_READ_BUFFER_SIZE}<br>
 *
 * <dd style="padding-top: .5em"> 
 *	Specifies the read buffer size.<p>
 * 
 * <dt> <i>Property:</i> <code><b>
 *	{@value #DISCONNECT_DELAY_PROPERTY}
 *	</b></code><br>
 *	<i>Default:</i> {@value #DEFAULT_DISCONNECT_DELAY}<br>
 *      <i>Minimum:</i> {@value #MIN_DISCONNECT_DELAY}<br>
 *
 * <dd style="padding-top: .5em"> 
 *	Specifies the disconnect delay (in milliseconds) for disconnecting
 *      sessions.<p>
 * </dl> <p>
 */
public class SimpleSgsProtocolAcceptor
    extends AbstractService
    implements ProtocolAcceptor
{
    /** The package name. */
    private static final String PKG_NAME = "com.sun.sgs.impl.protocol.simple";
    
    /** The logger for this class. */
    private static final LoggerWrapper staticLogger =
	new LoggerWrapper(Logger.getLogger(PKG_NAME + ".acceptor"));
    
    /** The name of the version key. */
    private static final String VERSION_KEY = PKG_NAME + ".service.version";

    /** The major version. */
    private static final int MAJOR_VERSION = 1;
    
    /** The minor version. */
    private static final int MINOR_VERSION = 0;

    /** The name of the read buffer size property. */
    public static final String READ_BUFFER_SIZE_PROPERTY =
        PKG_NAME + ".read.buffer.size";

    /**
     * The transport property. The specified transport must support
     * RELIABLE delivery.
     */
    public static final String TRANSPORT_PROPERTY =
        PKG_NAME + ".transport";
    
    /** The default transport. */
    public static final String DEFAULT_TRANSPORT =
        "com.sun.sgs.impl.transport.tcp.TcpTransport";
            
    /** The default read buffer size: {@value #DEFAULT_READ_BUFFER_SIZE}. */
    public static final int DEFAULT_READ_BUFFER_SIZE = 128 * 1024;
    
    /** The minimum read buffer size value. */
    public static final int MIN_READ_BUFFER_SIZE = 8192;
    
    /** The name of the disconnect delay property. */
    public static final String DISCONNECT_DELAY_PROPERTY =
	PKG_NAME + ".disconnect.delay";
    
    /** The time (in milliseconds) that a disconnecting connection is
     * allowed before this service forcibly disconnects it.
     */
    public static final long DEFAULT_DISCONNECT_DELAY = 1000;
    
    /** The minimum disconnect delay value. */
    public static final long MIN_DISCONNECT_DELAY = 1000;

    /** The identity manager. */
    private final IdentityCoordinator identityManager;

    /** The read buffer size for new connections. */
    protected final int readBufferSize;
    
    /** The transport. */
    protected final Transport transport;
    
    /** The disconnect delay (in milliseconds) for disconnecting sessions. */
    private final long disconnectDelay;

    /** The protocol descriptor. */
    private ProtocolDescriptor protocolDesc;
  
    /** The map of disconnecting {@code ClientSessionHandler}s, keyed by
     * the time the connection should expire.
     */
    private final ConcurrentSkipListMap<Long, SessionProtocol>
	disconnectingHandlersMap =
	    new ConcurrentSkipListMap<Long, SessionProtocol>();

    /** The handle for the task that monitors disconnecting client sessions. */
    private final RecurringTaskHandle monitorDisconnectingSessionsTaskHandle;

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
	this(properties, systemRegistry, txnProxy, staticLogger);
	
    }

    /**
     * Constructs an instance with the specified {@code properties},
     * {@code systemRegistry}, {@code txnProxy}, and {@code logger}.
     *
     * @param	properties the configuration properties
     * @param	systemRegistry the system registry
     * @param	txnProxy a transaction proxy
     * @param	logger a logger for this instance
     *
     * @throws	Exception if a problem occurs
     */
    protected SimpleSgsProtocolAcceptor(Properties properties,
					ComponentRegistry systemRegistry,
					TransactionProxy txnProxy,
					LoggerWrapper logger)
	throws Exception
    {
	super(properties, systemRegistry, txnProxy, logger);
	logger.log(Level.CONFIG,
		   "Creating SimpleSgsProtocolAcceptor properties:{0}",
		   properties);

	PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);
	try {
            readBufferSize = wrappedProps.getIntProperty(
                READ_BUFFER_SIZE_PROPERTY, DEFAULT_READ_BUFFER_SIZE,
                MIN_READ_BUFFER_SIZE, Integer.MAX_VALUE);
	    disconnectDelay = wrappedProps.getLongProperty(
		DISCONNECT_DELAY_PROPERTY, DEFAULT_DISCONNECT_DELAY,
		MIN_DISCONNECT_DELAY, Long.MAX_VALUE);
	    identityManager =
		systemRegistry.getComponent(IdentityCoordinator.class);
               
            transport =
                wrappedProps.getClassInstanceProperty(
		    TRANSPORT_PROPERTY, DEFAULT_TRANSPORT, Transport.class,
		    new Class[] {Properties.class}, properties);
            
            if (!transport.getDelivery().equals(Delivery.RELIABLE)) {
                transport.shutdown();
                throw new IllegalArgumentException(
		    "transport must support RELIABLE delivery");
            }
	    /*
	     * Set up recurring task to monitor disconnecting client sessions.
	     */
	    monitorDisconnectingSessionsTaskHandle =
		taskScheduler.scheduleRecurringTask(
 		    new MonitorDisconnectingSessionsTask(),
		    taskOwner, System.currentTimeMillis(),
		    disconnectDelay);
	    monitorDisconnectingSessionsTaskHandle.start();
	    
	    /*
	     * Check service version.
	     */
	    transactionScheduler.runTask(
		new AbstractKernelRunnable("CheckServiceVersion") {
		    public void run() {
			checkServiceVersion(
			    VERSION_KEY, MAJOR_VERSION, MINOR_VERSION);
		    } },  taskOwner);
	    
	} catch (Exception e) {
	    if (logger.isLoggable(Level.CONFIG)) {
		logger.logThrow(
		    Level.CONFIG, e,
		    "Failed to create SimpleSgsProtocolAcceptor");
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
        transport.shutdown();
        monitorDisconnectingSessionsTaskHandle.cancel(); 
	disconnectingHandlersMap.clear();
    }

    /* -- Implement ProtocolAcceptor -- */

    /** {@inheritDoc} */
    public synchronized ProtocolDescriptor getDescriptor() {
	if (protocolDesc == null) {
            protocolDesc =
		new SimpleSgsProtocolDescriptor(transport.getDescriptor());
	}
        return protocolDesc;
    }
    
    /** {@inheritDoc} */
    public void accept(ProtocolListener protocolListener) throws IOException {
        transport.accept(new ConnectionHandlerImpl(protocolListener));
    }

    /** {@inheritDoc} */
    public void close() {
	shutdown();
    }

    /**
     * Transport connection handler.
     */
    private class ConnectionHandlerImpl implements ConnectionHandler {

        private final ProtocolListener protocolListener;

        ConnectionHandlerImpl(ProtocolListener protocolListener) {
            if (protocolListener == null) {
                throw new NullPointerException("null protocolListener");
            }
            this.protocolListener = protocolListener;
        }
        
        /** {@inheritDoc} */
        public void newConnection(AsynchronousByteChannel byteChannel)
            throws Exception
        {
            new SimpleSgsProtocolImpl(protocolListener,
                                      SimpleSgsProtocolAcceptor.this,
                                      byteChannel,
                                      readBufferSize);
        }

        /** {@inheritDoc} */
        public void shutdown() {
            logger.log(Level.SEVERE, "transport unexpectly shutdown");
            close();
        }
    }
    
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
    public Identity authenticate(String name, String password)
	throws LoginException
    {
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
    public void monitorDisconnection(SessionProtocol protocol) {
	disconnectingHandlersMap.put(
	    System.currentTimeMillis() + disconnectDelay,  protocol);
    }
    
    /**
     * Schedules a non-durable, non-transactional {@code task}.
     *
     * @param	task a non-durable, non-transactional task
     */
    public void scheduleNonTransactionalTask(KernelRunnable task) {
        taskScheduler.scheduleTask(task, taskOwner);
    }
    
    /* -- Private methods and classes -- */

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
