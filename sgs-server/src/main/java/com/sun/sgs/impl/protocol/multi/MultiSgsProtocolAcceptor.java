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

package com.sun.sgs.impl.protocol.multi;

import com.sun.sgs.impl.protocol.simple.SimpleSgsProtocolAcceptor;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.nio.channels.AsynchronousByteChannel;
import com.sun.sgs.protocol.ProtocolDescriptor;
import com.sun.sgs.protocol.ProtocolListener;
import com.sun.sgs.protocol.simple.SimpleSgsProtocol;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.transport.ConnectionHandler;
import com.sun.sgs.transport.Transport;
import com.sun.sgs.transport.TransportDescriptor;
import com.sun.sgs.transport.TransportFactory;
import java.math.BigInteger;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A protocol acceptor for connections that speak the {@link SimpleSgsProtocol}
 * using two transports. The {@link #MultiSgsProtocolAcceptor constructor}
 * supports the properties specified for the {@link
 * SimplSgsProtocolAcceptor} constructor and following properties: <p>
 *
 * <dl style="margin-left: 1em">
 *
 * <dt> <i>Property:</i> <code><b>
 *	{@value #SECONDARY_TRANSPORT_PROPERTY}
 *	</b></code><br>
 *	<i>Default:</i> {@value #DEFAULT_SECONDARY_TRANSPORT}<br>
 *
 * <dd style="padding-top: .5em"> 
 *	Specifies the secondary transport.<p>
 * </dl> <p>
 */
public class MultiSgsProtocolAcceptor
    extends SimpleSgsProtocolAcceptor
{
    /** The package name. */
    public static final String PKG_NAME = "com.sun.sgs.impl.protocol.multi";
    
    /** The logger for this class. */
    private static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(PKG_NAME + "acceptor"));

//    /**
//     * The primary transport property. The primary transport must
//     * support RELIABLE delivery.
//     */
//    public static final String PRIMARY_TRANSPORT_PROPERTY =
//        PKG_NAME + ".transport.primary";
//    
//    /** The default primary transport */
//    public static final String DEFAULT_PRIMARY_TRANSPORT =
//        "com.sun.sgs.impl.transport.tcp.TcpTransport";
            
    /**  The secondary transport property. */
    public static final String SECONDARY_TRANSPORT_PROPERTY =
        PKG_NAME + ".transport.primary";
    
    /** The default primary transport */
    public static final String DEFAULT_SECONDARY_TRANSPORT =
        "com.sun.sgs.impl.transport.udp.UdpTransport";
    
    /** The secondary transport. */
    private final Transport secondaryTransport;
    
    /** The protocol descriptor. */
    private ProtocolDescriptor protocolDesc;
  
    /**
     * Map of connections that have processed successful logins an the
     * reconnect key. Used to attach the secondary connection to the
     * primary connection.
     */
    private final Map<BigInteger, MultiSgsProtocolImpl> logins =
            new ConcurrentHashMap<BigInteger, MultiSgsProtocolImpl>();
    
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
    public MultiSgsProtocolAcceptor(Properties properties,
				    ComponentRegistry systemRegistry,
				    TransactionProxy txnProxy)
	throws Exception
    {
	super(properties, systemRegistry, txnProxy, logger);
	
	logger.log(Level.CONFIG,
		   "Creating MultiSgsProtcolAcceptor properties:{0}",
		   properties);

	PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);
	try {
            String transportClassName =
                wrappedProps.getProperty(SECONDARY_TRANSPORT_PROPERTY,
					 DEFAULT_SECONDARY_TRANSPORT);
            
            try {
                secondaryTransport =
                        TransportFactory.newTransport(transportClassName,
						      properties);
            } catch (Exception e) {
                transport.shutdown();
                throw e;
            }
	    
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
    @Override
    public void doShutdown() {
	super.doShutdown();
        secondaryTransport.shutdown();
    }

    /* -- Implement ProtocolAcceptor -- */

    /** {@inheritDoc} */
    @Override
    public synchronized ProtocolDescriptor getDescriptor() {
	if (protocolDesc == null) {
	    protocolDesc =
		new MultiSgsProtocolDescriptor(
		    transport.getDescriptor(),
		    secondaryTransport.getDescriptor());
	}
	return protocolDesc;
    }
    
    /** {@inheritDoc} */
    @Override
    public void accept(ProtocolListener protocolListener) {
	if (protocolListener == null) {
	    throw new NullPointerException("null protocolListener");
	}
	this.protocolListener = protocolListener;
        transport.accept(new PrimaryHandlerImpl());
        secondaryTransport.accept(new SecondaryHandlerImpl());
    }

    /**
     * Primary transport connection handler.
     */
    private class PrimaryHandlerImpl implements ConnectionHandler {

        /** {@inheritDoc} */
        @Override
        public void newConnection(AsynchronousByteChannel byteChannel,
                                  TransportDescriptor descriptor)
            throws Exception
        {
            new MultiSgsProtocolImpl(protocolListener,
                                     MultiSgsProtocolAcceptor.this,
                                     byteChannel,
                                     readBufferSize);
        }
    }
    
    /**
     * Secondary transport connection handler.
     */
    private class SecondaryHandlerImpl implements ConnectionHandler {

        /** {@inheritDoc} */
        @Override
        public void newConnection(AsynchronousByteChannel byteChannel,
                                  TransportDescriptor descriptor)
            throws Exception
        {
            new SecondaryChannel(descriptor.getSupportedDelivery(),
				 MultiSgsProtocolAcceptor.this,
                                    byteChannel,
                                    readBufferSize);
        }
    }
    
    /* -- Package access methods -- */

    /**
     * Record a successful login.
     * @param key the reconnect key
     * @param connection the session connection
     */
    void successfulLogin(byte[] key, MultiSgsProtocolImpl protocol) {
        logins.put(new BigInteger(1, key), protocol);
    }
    
    /**
     * A session has logged out (or otherwise disconnected)
     * @param key the reconnect key for that session
     */
    void disconnect(byte[] key) {
        logins.remove(new BigInteger(1, key));
    }
    
    /**
     * Return the session connection associated with the specified
     * reconnect key. Returns {@code null} if no association exists. 
     * @param key the reconnect key
     * @return the session connection or {@code null}
     */
    MultiSgsProtocolImpl attach(byte[] key) {
        return logins.get(new BigInteger(1, key));
    }
}
