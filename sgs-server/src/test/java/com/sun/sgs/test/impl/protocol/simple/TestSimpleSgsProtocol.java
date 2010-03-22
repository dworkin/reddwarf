/*
 * Copyright 2007-2010 Sun Microsystems, Inc.
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
 *
 * --
 */

package com.sun.sgs.test.impl.protocol.simple;

import com.sun.sgs.app.Delivery;
import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.nio.AttachedFuture;
import com.sun.sgs.impl.protocol.simple.SimpleSgsProtocolAcceptor;
import com.sun.sgs.impl.sharedutil.MessageBuffer;
import com.sun.sgs.nio.channels.AsynchronousByteChannel;
import com.sun.sgs.nio.channels.CompletionHandler;
import com.sun.sgs.nio.channels.IoFuture;
import com.sun.sgs.protocol.ProtocolAcceptor;
import com.sun.sgs.protocol.ProtocolListener;
import com.sun.sgs.protocol.RequestCompletionHandler;
import com.sun.sgs.protocol.SessionProtocol;
import com.sun.sgs.protocol.SessionProtocolHandler;
import com.sun.sgs.protocol.simple.SimpleSgsProtocol;
import com.sun.sgs.test.util.SgsTestNode;
import com.sun.sgs.test.util.SgsTestNode.DummyAppListener;
import com.sun.sgs.tools.test.FilteredNameRunner;
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
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test the simple sgs protocol.
 */
@RunWith(FilteredNameRunner.class)
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
    
    @Test(expected=IllegalArgumentException.class)
    public void testUnreliableTransport() throws Exception {
        final Properties props = new Properties();
        props.setProperty(StandardProperties.APP_NAME, APP_NAME);
        props.setProperty(SimpleSgsProtocolAcceptor.TRANSPORT_PROPERTY,
                          DummyTransport.class.getName());
        props.setProperty("UnreliableDelivery", "true");
        acceptor = new SimpleSgsProtocolAcceptor(props,
                                                 serverNode.getSystemRegistry(),
                                                 serverNode.getProxy());
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
    
//    @Test
//    public void testSessionMessage() throws Exception {
//        final Properties props = new Properties();
//        props.setProperty(StandardProperties.APP_NAME, APP_NAME);
//        props.setProperty(SimpleSgsProtocolAcceptor.TRANSPORT_PROPERTY,
//                          DummyTransport.class.getName());
// 
//        acceptor = new SimpleSgsProtocolAcceptor(props,
//                                                 serverNode.getSystemRegistry(),
//                                                 serverNode.getProxy());
//        DummyListener listener = new DummyListener();
//        acceptor.accept(listener);
//        System.err.println("proto?..." + listener.protocol);
//        Thread.sleep(10);
//        listener.protocol.sessionMessage(ByteBuffer.allocate(1));
//        close();
//    }
        
    private void close() throws IOException {
        if (acceptor != null) {
            acceptor.close();
            acceptor = null;
        }
    }
    
    private static class DummyListener implements ProtocolListener {

        Identity identity = null;
        SessionProtocol protocol = null;
	RequestCompletionHandler<SessionProtocolHandler> completionHandler;
        
        public void newLogin(
	    Identity identity, SessionProtocol protocol,
	    RequestCompletionHandler<SessionProtocolHandler> completionHandler)
        {
            System.err.println("ProtocolListener.newLogin called...");
            if (identity == null || protocol == null)
                throw new RuntimeException("identity or protocol are null");
            this.identity = identity;
            this.protocol = protocol;
	    this.completionHandler = completionHandler;
        }

	public void relocatedSession(
	    BigInteger relocationKey, SessionProtocol protocol,
	    RequestCompletionHandler<SessionProtocolHandler> completionHandler)

	{
	}
	
        private class SessionHandler implements SessionProtocolHandler {

            public void sessionMessage(
		ByteBuffer message,
		RequestCompletionHandler<Void> completionHandler)
	    {
                System.err.println("***** sessionMessage called..." +
				   message.remaining());
		completionHandler.completed(new CompletedFuture());
            }

            public void channelMessage(
		BigInteger channelId, ByteBuffer message,
		RequestCompletionHandler<Void> completionHandler)
	    {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            public void logoutRequest(
		RequestCompletionHandler<Void> completionHandler)
	    {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            public void disconnect(
		RequestCompletionHandler<Void> completionHandler)
	    {
                throw new UnsupportedOperationException("Not supported yet.");
            }
        }
    }

    private static class CompletedFuture implements Future<Void> {

	CompletedFuture() {
	}
                
	public boolean cancel(boolean mayInterrupteIfRunning) {
	    return false;
	}

	public boolean isCancelled() {
	    return false;
	}

	public boolean isDone() {
	    return true;
	}

	public Void get() {
	    return null;
	}
	
	public Void get(long timeout, TimeUnit unit) {
	    return null;
	}
    }
            
    public static class DummyTransport implements Transport {

        private final TransportDescriptor descriptor;
        private final Delivery delivery;
        
        public DummyTransport(Properties properties) {
            delivery = properties.getProperty("UnreliableDelivery") != null ?
                                                        Delivery.UNRELIABLE :
                                                        Delivery.RELIABLE;
            descriptor = new DummyDescriptor();
        }
        
        public TransportDescriptor getDescriptor() {
            return descriptor;
        }

        public Delivery getDelivery() {
            return delivery;
        }
                
        public void accept(ConnectionHandler handler) {
            if (handler == null)
                throw new IllegalArgumentException(
                                "Transport.accept called with null handler");
            try {
                System.err.println("Transport.accept called...");
                handler.newConnection(new DummyChannel());
            } catch (Exception ex) {
                throw new RuntimeException(
                        "Unexpected exception from newConnection", ex);
            }
        }

        @Override
        public void shutdown() {}
        
        private class DummyChannel implements AsynchronousByteChannel {

            private boolean loggedIn = false;
            private ByteBuffer message;
            CompletionHandler handler = null;
            
            DummyChannel() {
                final MessageBuffer msg =
                        new MessageBuffer(4 +
                                          MessageBuffer.getSize("username") +
                                          MessageBuffer.getSize("password"));
                msg.putShort(msg.capacity() - 2);
                msg.putByte(SimpleSgsProtocol.LOGIN_REQUEST);
                msg.putByte(SimpleSgsProtocol.VERSION);
                msg.putString("username");
                msg.putString("password");
                message = ByteBuffer.allocate(msg.capacity());
                message.put(msg.getBuffer());
                message.flip();
            }
            
            public <A> IoFuture<Integer, A> read(ByteBuffer dst,
                                                 A attachment,
                                  CompletionHandler<Integer, ? super A> handler)
            {
                System.err.println("**Read called: buff= " + dst.capacity() + " loggedin= " + loggedIn);
                IoFuture<Integer, A> result;
                if (message != null) {
                    dst.put(message);
                    result =
                        AttachedFuture.wrap(new DummyFuture(message.capacity()),
                                            attachment);
                    message = null;
                    callCompletion(handler, attachment, result);
                } else {
                    result =
                            AttachedFuture.wrap(new DummyFuture(0), attachment);
                    this.handler = handler;
                }
                return result;
            }

            public <A> IoFuture<Integer, A> read(ByteBuffer dst,
                                  CompletionHandler<Integer, ? super A> handler)
            {
                return read(dst, null, handler);
            }

            public <A> IoFuture<Integer, A> write(ByteBuffer src, A attachment,
                                  CompletionHandler<Integer, ? super A> handler)
            {
                message = ByteBuffer.allocate(src.remaining());
                message.put(src);
                if (handler != null) {
                    callCompletion(handler, null,
                                   new DummyFuture(message.capacity()));
                    handler = null;
                }
                System.err.println("write: " + src.remaining());
                return AttachedFuture.wrap(new DummyFuture(src.remaining()),
                                           attachment);
            }

            public <A> IoFuture<Integer, A> write(ByteBuffer src,
                                  CompletionHandler<Integer, ? super A> handler)
            {
                return write(src, null, handler);
            }

            public boolean isOpen() {
                return true;
            }

            public void close() throws IOException {}
            
            // Terrible hack to get around some generics weirdness. Note that
            // the CompletionHandler parameter type no longer includes the
            // "? super" that the read method does above. This removes a
            // complier error when calling handler.completed. Turns out this
            // is what happens in the bowels of the sgs nio implementation, either
            // by design or by accident. I'm not inclined to rip the nio code
            // apart to fix it.
            //
            private <R, A> void callCompletion(CompletionHandler<R, A> handler,
                                               A attachment,
                                               Future<R> future)
            {
                try {
                    System.err.println("calling completion size= " + future.get());
                } catch (InterruptedException ex) {
                    Logger.getLogger(TestSimpleSgsProtocol.class.getName()).log(Level.SEVERE, null, ex);
                } catch (ExecutionException ex) {
                    Logger.getLogger(TestSimpleSgsProtocol.class.getName()).log(Level.SEVERE, null, ex);
                }
                handler.completed(AttachedFuture.wrap(future, attachment));
            }
            
            private class DummyFuture implements Future<Integer> {

                // size of message or 0 to indicate "not done"
                private final int bytes;
                
                DummyFuture(int bytes) {
                    this.bytes = bytes;
                }
                
                public boolean cancel(boolean arg0) {
                    System.err.println("**cancel called");
                    return true;}

                public boolean isCancelled() {
                    System.err.println("**iscancled called");
                    return false;
                }

                public boolean isDone() {
                    System.err.println("**isdone called " +(bytes != 0));
                    return bytes != 0;
                }

                public Integer get() throws InterruptedException,
                                            ExecutionException
                {
                   return bytes;
                }

                public Integer get(long arg0, TimeUnit arg1)
                        throws InterruptedException,
                               ExecutionException,
                               TimeoutException
                {
                   return bytes;
                }
            }           
        }
        
        private class DummyDescriptor implements TransportDescriptor {

            public boolean supportsTransport(TransportDescriptor descriptor) {
                return true;
            }

            public byte[] getConnectionData() {
                throw new UnsupportedOperationException();
            }
        }
    }
}
