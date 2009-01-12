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

package com.sun.sgs.test.impl.protocol.simple;

import com.sun.sgs.app.Delivery;
import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.nio.AttachedFuture;
import com.sun.sgs.impl.protocol.simple.SimpleSgsProtocolAcceptor;
import com.sun.sgs.nio.channels.AsynchronousByteChannel;
import com.sun.sgs.nio.channels.CompletionHandler;
import com.sun.sgs.nio.channels.IoFuture;
import com.sun.sgs.protocol.CompletionFuture;
import com.sun.sgs.protocol.LoginCompletionFuture;
import com.sun.sgs.protocol.ProtocolAcceptor;
import com.sun.sgs.protocol.ProtocolListener;
import com.sun.sgs.protocol.SessionProtocol;
import com.sun.sgs.protocol.SessionProtocolHandler;
import com.sun.sgs.test.util.NameRunner;
import com.sun.sgs.test.util.SgsTestNode;
import com.sun.sgs.test.util.SgsTestNode.DummyAppListener;
import com.sun.sgs.transport.ConnectionHandler;
import com.sun.sgs.transport.Transport;
import com.sun.sgs.transport.TransportDescriptor;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test the simple sgs protocol.
 */
@RunWith(NameRunner.class)
public class TestSimpleSgsProtocol {

    private static final String APP_NAME = "TestSimpleSgsProtocol";
    
    private SgsTestNode serverNode;
    
    private volatile ProtocolAcceptor acceptor;
    
    @Before
    public void setUp() throws Exception {
        Properties props = 
            SgsTestNode.getDefaultProperties(APP_NAME, null, 
                                             DummyAppListener.class);
        serverNode = 
                new SgsTestNode(APP_NAME, DummyAppListener.class, props, true);
        acceptor = null;
    }

    @After
    public void tearDown() throws Exception {
        close();
        Thread.sleep(100);
        serverNode.shutdown(true);
        serverNode = null;
    }
    
    @Test(expected=NullPointerException.class)
    public void testNullProperties() throws Exception {
        acceptor = new SimpleSgsProtocolAcceptor(null,
                                                 serverNode.getSystemRegistry(),
                                                 serverNode.getProxy());
    }
    
    @Test
    public void testDefaults() throws Exception {
        final Properties props = new Properties();
        props.setProperty(StandardProperties.APP_NAME, APP_NAME);
        acceptor = new SimpleSgsProtocolAcceptor(props,
                                                 serverNode.getSystemRegistry(),
                                                 serverNode.getProxy());
        close();
    }
    
    @Test
    public void testAcceptNPE() throws Exception {
        final Properties props = new Properties();
        props.setProperty(StandardProperties.APP_NAME, APP_NAME);
        props.setProperty(SimpleSgsProtocolAcceptor.TRANSPORT_PROPERTY,
                          DummyTransport.class.getName());
 
        acceptor = new SimpleSgsProtocolAcceptor(props,
                                                 serverNode.getSystemRegistry(),
                                                 serverNode.getProxy());
        try {
            acceptor.accept(null);
            throw new Exception("Expected NullPointerException");
        } catch (NullPointerException expected) {}
    }
    
    @Test
    public void testAccept() throws Exception {
        final Properties props = new Properties();
        props.setProperty(StandardProperties.APP_NAME, APP_NAME);
        props.setProperty(SimpleSgsProtocolAcceptor.TRANSPORT_PROPERTY,
                          DummyTransport.class.getName());
 
        acceptor = new SimpleSgsProtocolAcceptor(props,
                                                 serverNode.getSystemRegistry(),
                                                 serverNode.getProxy());
        DummyListener listener = new DummyListener();
        acceptor.accept(listener);
        close();
    }
    
    private void close() throws IOException {
        if (acceptor != null) {
            acceptor.close();
            acceptor = null;
        }
    }
    
    static private class DummyListener implements ProtocolListener {

        Identity identity = null;
        SessionProtocol protocol = null;
        
        @Override
        public LoginCompletionFuture newLogin(Identity identity,
                                              SessionProtocol protocol)
        {
            System.out.println("newLogn "+ identity + "  " + protocol);
            if (identity == null || protocol == null)
                throw new RuntimeException("identity or protocol are null");
            this.identity = identity;
            this.protocol = protocol;
            return new Future(new SessionHandler());
        }
        
        private class Future implements LoginCompletionFuture {
            private final SessionProtocolHandler handler;
            
            Future(SessionProtocolHandler handler) {
                this.handler = handler;
            }
            @Override
            public SessionProtocolHandler get() {
                return handler;
            }

            @Override
            public SessionProtocolHandler get(long timeout, TimeUnit unit) {
                return handler;
            }

            @Override
            public boolean cancel(boolean arg0) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean isCancelled() {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean isDone() {
                throw new UnsupportedOperationException();
            }
        }
        
        private class SessionHandler implements SessionProtocolHandler {

            @Override
            public CompletionFuture sessionMessage(ByteBuffer message) {
                throw new UnsupportedOperationException();
            }

            @Override
            public CompletionFuture channelMessage(BigInteger channelId,
                                                   ByteBuffer message) {
                throw new UnsupportedOperationException();
            }

            @Override
            public CompletionFuture logoutRequest() {
                throw new UnsupportedOperationException();
            }

            @Override
            public CompletionFuture disconnect() {
                throw new UnsupportedOperationException();
            }
        }
    }
            
    static public class DummyTransport implements Transport {

        public DummyTransport(Properties properties) {}
        
        @Override
        public TransportDescriptor getDescriptor() {
            return new DummyDescriptor();
        }

        @Override
        public void accept(ConnectionHandler handler) {
            System.err.println("DummyTrabsport.accept " + handler);
            if (handler == null)
                throw new IllegalArgumentException(
                                "Transport.accept called with null handler");
            try {
                handler.newConnection(new DummyChannel(),
                                      new DummyDescriptor());
            } catch (Exception ex) {
                throw new RuntimeException(
                        "Unexpected exception from newConnection", ex);
            }
        }

        @Override
        public void shutdown() {
            throw new UnsupportedOperationException();
        }
        
        private class DummyChannel implements AsynchronousByteChannel {

            @Override
            public <A> IoFuture<Integer, A> read(ByteBuffer dst,
                                                 A attachment,
                                  CompletionHandler<Integer, ? super A> handler)
            {
                System.err.println("DummyChannel.read ");
                return AttachedFuture.wrap(new Future<Integer>() {

                    @Override
                    public boolean cancel(boolean arg0) {
                        return true;}

                    @Override
                    public boolean isCancelled() {
                        return false;
                    }

                    @Override
                    public boolean isDone() {
                        System.err.println("FU.isDone");
                        return true;
                    }

                    @Override
                    public Integer get() throws InterruptedException,
                                                ExecutionException
                    {
                        System.err.println("FU.get");
                       throw new UnsupportedOperationException();
                    }

                    @Override
                    public Integer get(long arg0, TimeUnit arg1)
                            throws InterruptedException,
                                   ExecutionException,
                                   TimeoutException
                    {
                        System.err.println("FU.get");
                       throw new UnsupportedOperationException();
                    }
                }, attachment);
            }

            @Override
            public <A> IoFuture<Integer, A> read(ByteBuffer dst,
                                  CompletionHandler<Integer, ? super A> handler)
            {
                return read(dst, null, handler);
            }

            @Override
            public <A> IoFuture<Integer, A> write(ByteBuffer src, A attachment,
                                  CompletionHandler<Integer, ? super A> handler)
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public <A> IoFuture<Integer, A> write(ByteBuffer src,
                                  CompletionHandler<Integer, ? super A> handler)
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean isOpen() {
                return true;
            }

            @Override
            public void close() throws IOException {}
        }
        
        private class DummyDescriptor implements TransportDescriptor {

            @Override
            public Delivery[] getSupportedDelivery() {
                return Delivery.values();
            }

            @Override
            public boolean canSupport(Delivery required) {
                return true;
            }

            @Override
            public boolean isCompatibleWith(TransportDescriptor descriptor) {
                return true;
            }

            @Override
            public byte[] getConnectionData() {
                throw new UnsupportedOperationException();
            }
        }
    }
}
