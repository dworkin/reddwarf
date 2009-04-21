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

package com.sun.sgs.test.impl.transport.tcp;

import com.sun.sgs.impl.io.SocketEndpoint;
import com.sun.sgs.impl.io.TransportType;
import com.sun.sgs.impl.transport.tcp.TcpTransport;
import com.sun.sgs.io.Connection;
import com.sun.sgs.io.ConnectionListener;
import com.sun.sgs.io.Connector;
import com.sun.sgs.nio.channels.AsynchronousByteChannel;
import com.sun.sgs.tools.test.FilteredNameRunner;
import com.sun.sgs.transport.ConnectionHandler;
import com.sun.sgs.transport.Transport;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Properties;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test the TcpTransport transport.
 */
@RunWith(FilteredNameRunner.class)
public class TestTcpTransport {
    
    private volatile Transport transport;
    
    @Before
    public void setUp() throws Exception {
        transport = null;
    }

    @After
    public void tearDown() throws Exception {
        shutdown();
    }
    
    @Test(expected=NullPointerException.class)
    public void testNullProperties() throws Exception {        
        transport = new TcpTransport(null); 
    }
    
    @Test
    public void testDefaults() throws Exception {
        transport = new TcpTransport(new Properties());
        
        if (transport.getDescriptor() == null)
            throw new Exception("Null descriptor");
        shutdown();
    }
    
    @Test
    public void testShutdownAfterShutdown() throws Exception {
        transport = new TcpTransport(new Properties());
        transport.shutdown();
        shutdown();
    }
    
    @Test(expected=NullPointerException.class)
    public void testNullHandler() throws Exception {
        transport = new TcpTransport(new Properties());
        transport.accept(null);
    }
    
    @Test(expected=IllegalStateException.class)
    public void testAcceptAfterShutdown() throws Exception {
        transport = new TcpTransport(new Properties());
        Transport t = transport;
        shutdown();
        t.accept(new DummyHandler());
    }
    
    @Test
    public void testAccept() throws Exception {
        transport = new TcpTransport(new Properties());
        transport.accept(new DummyHandler());
        shutdown();
    }
    
    @Test(expected=IllegalStateException.class)
    public void testMultipleAccept() throws Exception {
        transport = new TcpTransport(new Properties());
        transport.accept(new DummyHandler());
        transport.accept(new DummyHandler());
    }
    
    @Test
    public void testAcceptConnect() throws Exception {
        transport = new TcpTransport(new Properties());
        DummyHandler handler = new DummyHandler();
        transport.accept(handler);
        final DummyClient client = new DummyClient("testAcceptConnect");
        client.connect(TcpTransport.DEFAULT_PORT);    
        if (!handler.isConnected()) {
            throw new Exception("Server did not receive connect");
        }
        shutdown();
    }
    
    @Test
    public void testServerDisconnect() throws Exception {
        transport = new TcpTransport(new Properties());
        DummyHandler handler = new DummyHandler();
        transport.accept(handler);
        final DummyClient client = new DummyClient("testServerDisconnect");
        client.connect(TcpTransport.DEFAULT_PORT);

        if (!handler.isConnected()) {
            throw new Exception("Server did not receive connect");
        }
        shutdown();

        if (client.waitForDisconnect()) {
            throw new Exception("Client did not receive disconnect");
        }
    }
      
    @Test
    public void testClientDisconnect() throws Exception {
        transport = new TcpTransport(new Properties());
        DummyHandler handler = new DummyHandler();
        transport.accept(handler);
        final DummyClient client = new DummyClient("testClientDisconnect");
        client.connect(TcpTransport.DEFAULT_PORT);

        if (!handler.isConnected()) {
            throw new Exception("Server did not receive connect");
        }
        client.disconnect();

//        if (handler.isConnected()) {
//            throw new Exception("Server did not receive disconnect");
//        }
        shutdown();
    }
      
    private void shutdown() {
        if (transport != null) {
            transport.shutdown();
            transport = null;
        }
    }

    /**
     * Dummy connection handler.
     */
    private static class DummyHandler implements ConnectionHandler {

        AsynchronousByteChannel channel;
        
        public void newConnection(AsynchronousByteChannel channel)
            throws Exception
        {
            this.channel = channel;
        }
        
        public void shutdown() {
            throw new UnsupportedOperationException("Not supported yet.");
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
        
        public String toString() {
	    return "[" + name + "]";
	}
    
        private class Listener implements ConnectionListener {

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

	    public void disconnected(Connection conn) {
                System.err.println("DummyClient.Listener.disconnected");
                synchronized (lock) {
                    connected = false;
                    lock.notifyAll();
                }
	    }
	    
	    public void exceptionThrown(Connection conn, Throwable exception) {
		System.err.println("DummyClient.Listener.exceptionThrown " +
				   "exception:" + exception);
		exception.printStackTrace();
	    }

            public void bytesReceived(Connection arg0, byte[] arg1) {
                throw new UnsupportedOperationException();
            }
	}
    }
}
