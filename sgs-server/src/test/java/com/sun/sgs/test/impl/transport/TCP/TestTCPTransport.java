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

package com.sun.sgs.test.impl.transport.TCP;

import com.sun.sgs.impl.io.SocketEndpoint;
import com.sun.sgs.impl.io.TransportType;
import com.sun.sgs.impl.transport.tcp.TCP;
import com.sun.sgs.io.Connection;
import com.sun.sgs.io.ConnectionListener;
import com.sun.sgs.io.Connector;
import com.sun.sgs.nio.channels.AsynchronousByteChannel;
import com.sun.sgs.transport.ConnectionHandler;
import com.sun.sgs.transport.Transport;
import com.sun.sgs.transport.TransportDescriptor;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Properties;
import junit.framework.TestCase;

/**
 * Test the TCP transport.
 */
public class TestTCPTransport extends TestCase {

    public TestTCPTransport(String name) {
        super(name);
    }
    
    @Override
    protected void setUp() throws Exception {
        System.err.println("Testcase: " + getName());
    }
    
    public void testNullProperties() throws Exception {
        try {
            final Transport transport = new TCP(null);
            shutdown(transport);
            fail("Expected NullPointerException");
        } catch (NullPointerException npe) {
            System.err.println(npe);
        }
    }
    
    public void testDefaults() throws Exception {
        final Transport transport = new TCP(new Properties());
        try {
            if (transport.getDescriptor() == null)
                fail("Null descriptor");
        } finally {
            shutdown(transport);
        }
    }
    
    public void testShutdown() throws Exception {
        final Transport transport = new TCP(new Properties());
        transport.shutdown();
    }
    
    public void testNullHandler() throws Exception {
        final Transport transport = new TCP(new Properties());
        try {
            transport.accept(null);
            fail("Expected NullPointerException");
        } catch (NullPointerException npe) {
            System.err.println(npe);
        } finally {
            shutdown(transport);
        }
    }
    
    public void testAcceptAfterShutdown() throws Exception {
        final Transport transport = new TCP(new Properties());
        try {
            transport.shutdown();
            transport.accept(new DummyHandler());
            fail("Expected IllegalStateException");
        } catch (IllegalStateException ise) {
            System.err.println(ise);
        }
    }
    
    public void testAccept() throws Exception {
        final Transport transport = new TCP(new Properties());
        try {
            transport.accept(new DummyHandler());
        } finally {
            shutdown(transport);
        }
    }
    
    public void testAcceptConnect() throws Exception {
        final Transport transport = new TCP(new Properties());
        try {
            DummyHandler handler = new DummyHandler();
            transport.accept(handler);
            final DummyClient client = new DummyClient("testAcceptConnect");
            client.connect(TCP.DEFAULT_PORT);    
            if (!handler.isConnected()) {
                fail("Server did not receive connect");
            }
        } finally {
            shutdown(transport);
        }
    }
    
    public void testServerDisconnect() throws Exception {
        final Transport transport = new TCP(new Properties());
        try {
            DummyHandler handler = new DummyHandler();
            transport.accept(handler);
            final DummyClient client = new DummyClient("testServerDisconnect");
            client.connect(TCP.DEFAULT_PORT);
                
            if (!handler.isConnected()) {
                fail("Server did not receive connect");
            }
            transport.shutdown();
            
            if (client.waitForDisconnect()) {
                fail("Client did not receive disconnect");
            }
        } finally {
            shutdown(transport);
        }
    }
        
//    public void testClientDisconnect() throws Exception {
//        final Transport transport = new TCP(new Properties());
//        try {
//            DummyHandler handler = new DummyHandler();
//            transport.accept(handler);
//            final DummyClient client = new DummyClient("testClientDisconnect");
//            client.connect(TCP.DEFAULT_PORT);
//                
//            if (!handler.isConnected()) {
//                fail("Server did not receive connect");
//            }
//            client.disconnect();
//            
//            if (handler.isConnected()) {
//                fail("Server did not receive disconnect");
//            }
//        } finally {
//            shutdown(transport);
//        }
//    }
      
    private void shutdown(Transport transport) {
        try {
            transport.shutdown();
        } catch (Exception e) {
            System.err.println("Exception during shutdown: " + e);
        }
    }

    /**
     * Dummy connection handler.
     */
    private static class DummyHandler implements ConnectionHandler {

        AsynchronousByteChannel channel;
        
        @Override
        public void newConnection(AsynchronousByteChannel channel,
                                  TransportDescriptor descriptor)
            throws Exception
        {
            this.channel = channel;
        }
        
        boolean isConnected() {
            return channel == null ? false : channel.isOpen();
        }
    }
        
    /**
     * Dummy client code for testing purposes.
     */
    private class DummyClient {
	private static final int WAIT_TIME = 5000;
        
	private final String name;
	private Connector<SocketAddress> connector;
	private Listener listener;
	private Connection connection;
	private boolean connected = false;
	private final Object lock = new Object();
	
	DummyClient(String name) {
	    this.name = name;
	}

	DummyClient connect(int port) throws Exception {
	    connected = false;
	    listener = new Listener();
	    try {
		SocketEndpoint endpoint =
		    new SocketEndpoint(
		        new InetSocketAddress(InetAddress.getLocalHost(), port),
			TransportType.RELIABLE);
		connector = endpoint.createConnector();
		connector.connect(listener);
	    } catch (Exception e) {
		System.err.println(toString() + " connect throws: " + e);
		e.printStackTrace();
		throw new Exception("DummyClient.connect failed", e);
	    }
	    synchronized (lock) {
		try {
		    if (connected == false) {
			lock.wait(WAIT_TIME);
		    }
		    if (connected != true) {
			throw new Exception(
			    toString() + " connect timed out to " + port);
		    }
		} catch (InterruptedException e) {
		    throw new Exception(
			toString() + " connect timed out to " + port, e);
		}
	    }
	    return this;
	}

	void disconnect() {
            System.err.println(toString() + " disconnecting");

            synchronized (lock) {
                if (connected == false) {
                    return;
                }
                connected = false;
            }

            try {
                connection.close();
            } catch (IOException e) {
                System.err.println(toString() + " disconnect exception:" + e);
            }

            synchronized (lock) {
                lock.notifyAll();
            }
	}
        
        boolean waitForDisconnect() {
            synchronized (lock) {
		try {
		    if (connected) {
			lock.wait(WAIT_TIME);
		    }
                } catch (InterruptedException e) {
		    System.err.println(toString() + " wait interrupted: " + e);
		}
            }
            return connected;
        }
        
        @Override
        public String toString() {
	    return "[" + name + "]";
	}
    
        private class Listener implements ConnectionListener {

            /** {@inheritDoc} */
            @Override
	    public void connected(Connection conn) {
		System.err.println("DummyClient.Listener.connected");
		if (connection != null) {
		    System.err.println(
			"DummyClient.Listener.already connected handle: " +
			connection);
		    return;
		}
		connection = conn;
		synchronized (lock) {
		    connected = true;
		    lock.notifyAll();
		}
	    }

            /** {@inheritDoc} */
            @Override
	    public void disconnected(Connection conn) {
                System.err.println("DummyClient.Listener.disconnected");
                synchronized (lock) {
                    connected = false;
                    lock.notifyAll();
                }
	    }
	    
            /** {@inheritDoc} */
            @Override
	    public void exceptionThrown(Connection conn, Throwable exception) {
		System.err.println("DummyClient.Listener.exceptionThrown " +
				   "exception:" + exception);
		exception.printStackTrace();
	    }

            @Override
            public void bytesReceived(Connection arg0, byte[] arg1) {
                throw new UnsupportedOperationException("Not supported yet.");
            }
	}
    }
}
