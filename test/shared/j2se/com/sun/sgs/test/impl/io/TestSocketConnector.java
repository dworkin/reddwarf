/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.test.impl.io;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import junit.framework.Assert;
import junit.framework.TestCase;

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
public class TestSocketConnector
    extends TestCase
{
    private final static int PORT = 5000;

    private final static int DELAY = 1000;

    private final SocketEndpoint connectEndpoint =
        new SocketEndpoint(new InetSocketAddress("", PORT),
            TransportType.RELIABLE);

    private final ServerSocketEndpoint acceptEndpoint =
        new ServerSocketEndpoint(new InetSocketAddress(PORT),
            TransportType.RELIABLE);

    Acceptor<SocketAddress> acceptor;

    @Override
    public void setUp() {
        try {
            acceptor = acceptEndpoint.createAcceptor();
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
     * Shuts down the acceptor and start over for the next test.
     * <p>
     * Note that even though the acceptor will shutdown in a separate thread,
     * the current thread will block until shutdown is complete. Even so, it
     * seems that sometimes the next call to "init" happens too quickly after
     * the call to shutdown, causing subsequent tests to fail. This may be an IO
     * limitation of the machine, not being able to fully unbind the port before
     * it is bound again.
     */
    @Override
    public void tearDown() {
        acceptor.shutdown();
        acceptor = null;
    }

    /**
     * Test connectivity.
     *
     * @throws IOException if an unexpected IO problem occurs
     */
    public void testConnect() throws IOException {
        final CountDownLatch connectLatch = new CountDownLatch(1);

        Connector<SocketAddress> connector =
            connectEndpoint.createConnector();

        ConnectionListener listener = new ConnectionAdapter() {
            @Override
            public void connected(Connection conn) {
                System.err.println("connected");
                connectLatch.countDown();
            }
        };

        boolean connected = false;

        connector.connect(listener);

        try {
            connected = connectLatch.await(DELAY, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ie) {
            System.err.println("interrupted!");
        }

        System.err.println("connected = " + connected);

        Assert.assertTrue(connected);
    }

    /**
     * Test the Connector's lack of re-usability.
     *
     * @throws IOException if an unexpected IO problem occurs
     */
    public void testMultipleConnect() throws IOException {
        Connector<SocketAddress> connector =
            connectEndpoint.createConnector();

        ConnectionListener listener = new ConnectionAdapter();

        connector.connect(listener);

        try {
            connector.connect(listener);
        } catch (IllegalStateException expected) {
            // passed
            return;
        }
        Assert.fail("Expected IllegalStateException");
    }

    public void testShutdown() throws IOException {
        Connector<SocketAddress> connector =
            connectEndpoint.createConnector();

        try {
            connector.shutdown();
        } catch (IllegalStateException expected) {
            // passed
            return;
        }
        Assert.fail("Expected IllegalStateException");
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
