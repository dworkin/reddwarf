package com.sun.sgs.test.io;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.Executors;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.sun.sgs.impl.io.SocketEndpoint;
import com.sun.sgs.impl.io.IOConstants.TransportType;
import com.sun.sgs.io.IOAcceptorListener;
import com.sun.sgs.io.IOAcceptor;
import com.sun.sgs.io.IOConnector;
import com.sun.sgs.io.IOHandle;
import com.sun.sgs.io.IOHandler;

/**
 * A set of JUnit tests for the SocketConnector class.
 */
public class SocketConnectorTest {

    private final static int BIND_PORT = 5000;

    private final static int DELAY = 1000;

    private final SocketAddress ADDRESS = new InetSocketAddress(BIND_PORT);

    IOAcceptor<SocketAddress> acceptor;

    private boolean connected = false;

    @Before
    public void init() {
        connected = false;

        try {
            acceptor = new SocketEndpoint(ADDRESS, TransportType.RELIABLE)
                    .createAcceptor();
            acceptor.listen(new IOAcceptorListener() {

                public IOHandler newHandle() {
                    return new IOHandlerAdapter();
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
     */
    @Test
    public void testConnect() {
        IOConnector<SocketAddress> connector = new SocketEndpoint(ADDRESS,
                TransportType.RELIABLE, Executors.newCachedThreadPool())
                .createConnector();

        IOHandler handler = new IOHandlerAdapter() {
            public void connected(IOHandle handle) {
                connected = true;
                notifyAll();
            }

        };

        connector.connect(handler);

        synchronized (this) {
            try {
                wait(DELAY);
            } catch (InterruptedException ie) {
            }
        }
        Assert.assertTrue(connected);

    }

    /**
     * Test the IOConnector's lack of re-usability.
     * 
     */
    @Test(expected = IllegalStateException.class)
    public void testMultipleConnect() {
        IOConnector<SocketAddress> connector = new SocketEndpoint(ADDRESS,
                TransportType.RELIABLE).createConnector();

        IOHandler handler = new IOHandlerAdapter();

        connector.connect(handler);

        connector.connect(handler);
    }

    @Test(expected = IllegalStateException.class)
    public void testShutdown() {
        final IOConnector<SocketAddress> connector = new SocketEndpoint(ADDRESS,
                TransportType.RELIABLE).createConnector();

        IOHandler handler = new IOHandlerAdapter();

        connector.shutdown();
        connector.connect(handler);

    }

    private static class IOHandlerAdapter implements IOHandler {
        public void bytesReceived(IOHandle handle, byte[] buffer) {
        }

        public void connected(IOHandle handle) {
        }

        public void disconnected(IOHandle handle) {
        }

        public void exceptionThrown(IOHandle handle, Throwable exception) {
        }
    }

}
