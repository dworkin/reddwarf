package com.sun.sgs.test.impl.io;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.notification.Failure;

import com.sun.sgs.impl.io.SocketEndpoint;
import com.sun.sgs.impl.io.IOConstants.TransportType;
import com.sun.sgs.io.IOAcceptor;

/**
 * JUnit test class for the SocketAcceptor. 
 * 
 */
public class SocketAcceptorTest {
    
    private final static int BIND_PORT = 0;
    
    IOAcceptor<SocketAddress> acceptor;
    
    @Before
    public void init() {
        acceptor = new SocketEndpoint(
                new InetSocketAddress(BIND_PORT),
               TransportType.RELIABLE).createAcceptor();
    }
    
    @After
    public void cleanup() {
        if (acceptor != null) {
            acceptor.shutdown();
            acceptor = null;
        }
    }
    
    /**
     * Test listening on one port.
     */
    @Test
    public void listen() {
        try {
            acceptor.listen(null);
        }
        catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }
    
    /**
     * Test that verifies the IOAcceptor won't listen on any port after 
     * shutdown.
     */
    @Test (expected=IllegalStateException.class)
    public void listenAfterShutdown() {
        try {
            acceptor.listen(null);
            acceptor.shutdown();
            acceptor.listen(null);
        }
        catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

}
