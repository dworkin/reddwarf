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

package com.sun.sgs.impl.protocol.ipwrapper;

import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.protocol.simple.SimpleSgsProtocolAcceptor;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.nio.channels.AsynchronousByteChannel;
import com.sun.sgs.protocol.ProtocolListener;
import com.sun.sgs.protocol.RequestCompletionHandler;
import com.sun.sgs.protocol.SessionProtocol;
import com.sun.sgs.protocol.SessionProtocolHandler;
import com.sun.sgs.protocol.simple.SimpleSgsProtocol;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.transport.ConnectionHandler;
import java.io.IOException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A wrapper protocol acceptor for {@link SimpleSgsProtocol}.
 */
public class IPProtocolAcceptor extends SimpleSgsProtocolAcceptor {
    /** The package name. */
    private static final String PKG_NAME ="com.sun.sgs.impl.protocol.ipwrapper";
    
    /** The logger for this class. */
    private static final LoggerWrapper staticLogger =
	new LoggerWrapper(Logger.getLogger(PKG_NAME + ".acceptor"));
  
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
    public IPProtocolAcceptor(Properties properties,
			      ComponentRegistry systemRegistry,
			      TransactionProxy txnProxy)
	throws Exception
    {
	super(properties, systemRegistry, txnProxy, staticLogger);
        logger.log(Level.CONFIG, "Created IPProtocolAcceptor");
    }
 
    
    /** {@inheritDoc} */
    @Override
    public void accept(ProtocolListener protocolListener) throws IOException {
        transport.accept(new MyConnectionHandlerImpl(
                                    new MyProtocolListener(protocolListener)));
    }

    /**
     * Connection handler to capture the new connections.
     */
    private class MyConnectionHandlerImpl implements ConnectionHandler {

        private final ProtocolListener protocolListener;

        MyConnectionHandlerImpl(ProtocolListener protocolListener) {
            if (protocolListener == null) {
                throw new NullPointerException("null protocolListener");
            }
            this.protocolListener = protocolListener;
        }
        
        /** {@inheritDoc} */
        public void newConnection(AsynchronousByteChannel byteChannel)
            throws Exception
        {
            new IPProtocolWrapper(protocolListener,
                                  IPProtocolAcceptor.this,
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
     * Listener to capture the login, so that we can replace the identity with
     * our own which will contain the ip address of the client.
     */
    private class MyProtocolListener implements ProtocolListener {

        private final ProtocolListener wrappedListener;

        MyProtocolListener(ProtocolListener listener) {
            wrappedListener = listener;
        }

        @Override
        public void newLogin(Identity identity,
                             SessionProtocol protocol,
                             RequestCompletionHandler<SessionProtocolHandler>
                                                    completionHandler)
        {
            // Replace the authenticated identity with ours
            Identity ipIdentity =
                 new IPIdentity(identity,
                                ((IPProtocolWrapper)protocol).getInetAddress());
            wrappedListener.newLogin(ipIdentity,
                                     protocol, completionHandler);
        }
    }
}
