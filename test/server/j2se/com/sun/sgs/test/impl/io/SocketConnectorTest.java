package com.sun.sgs.test.impl.io;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.Executors;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.sun.sgs.impl.io.ServerSocketEndpoint;
import com.sun.sgs.impl.io.SocketEndpoint;
import com.sun.sgs.impl.io.TransportType;
import com.sun.sgs.io.AcceptorListener;
import com.sun.sgs.io.Acceptor;
import com.sun.sgs.io.Connector;
import com.sun.sgs.io.Connection;
import com.sun.sgs.io.ConnectionListener;

/**
 * A set of JUnit tests for the SocketConnector class.
 */
public class SocketConnectorTest {

    private final static int BIND_PORT = 5000;

    private final static int DELAY = 1000;

    private final SocketAddress ADDRESS = new InetSocketAddress(BIND_PORT);

    Acceptor<SocketAddress> acceptor;

    private boolean connected = false;

    @Before
    public void init() {
        connected = false;

        try {
            acceptor = new ServerSocketEndpoint(ADDRESS, TransportType.RELIABLE)
                    .createAcceptor();
            acceptor.listen(new AcceptorListener() {

                public ConnectionListener newConnection() {
                    return new ConnectionAdapter();
                }

                public void disconnected() {
                    // TODO Auto-generated method stub
                }

            });
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    /**
     * Shutdown the acceptor and start over for the next test.
     * 
     * Note that even though the acceptor will shutdown in a separate thread,
     * the current thread will block until shutdown is complete. Even so, it
     * seems that sometimes the next call to "init" happens too quickly after
     * the call to shutdown, causing subsequent tests to fail. This may be an IO
     * limitation of the machine, not being able to fully unbind the port before
     * it is bound again.
     * 
     */
    @After
    public void cleanup() {
        acceptor.shutdown();
        acceptor = null;

    }

    /**
     * Test connectivity.
     *
     * @throws IOException if an unexpected IO problem occurs
     */
    @Test
    public void testConnect() throws IOException {
        Connector<SocketAddress> connector =
            new SocketEndpoint(ADDRESS,
                               TransportType.RELIABLE,
                               Executors.newCachedThreadPool())
                .createConnector();

        ConnectionListener listener = new ConnectionAdapter() {
            public void connected(Connection conn) {
                connected = true;
                notifyAll();
            }
        };

        connector.connect(listener);

        synchronized (this) {
            try {
                wait(DELAY);
            } catch (InterruptedException ie) {
            }
        }
        Assert.assertTrue(connected);

    }

    /**
     * Test the Connector's lack of re-usability.
     *
     * @throws IOException if an unexpected IO problem occurs
     */
    @Test(expected = IllegalStateException.class)
    public void testMultipleConnect() throws IOException {
        Connector<SocketAddress> connector =
            new SocketEndpoint(ADDRESS,
                               TransportType.RELIABLE).createConnector();

        ConnectionListener listener = new ConnectionAdapter();

        connector.connect(listener);

        connector.connect(listener);
    }

    @Test(expected = IllegalStateException.class)
    public void testShutdown() throws IOException {
        final Connector<SocketAddress> connector =
            new SocketEndpoint(ADDRESS,
                               TransportType.RELIABLE).createConnector();

        ConnectionListener listener = new ConnectionAdapter();

        connector.shutdown();
        connector.connect(listener);

    }

    private static class ConnectionAdapter implements ConnectionListener {
        /** {@inheritDoc} */
        public void bytesReceived(Connection conn, byte[] buffer) {
            //empty
        }

        /** {@inheritDoc} */
        public void connected(Connection conn) {
            //empty
        }

        /** {@inheritDoc} */
        public void disconnected(Connection conn) {
            //empty
        }

        /** {@inheritDoc} */
        public void exceptionThrown(Connection conn, Throwable exception) {
            //empty
        }   
    }
}
