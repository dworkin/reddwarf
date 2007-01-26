package com.sun.sgs.test.impl.io;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.sun.sgs.impl.io.ServerSocketEndpoint;
import com.sun.sgs.impl.io.TransportType;
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
        acceptor = new ServerSocketEndpoint(
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
     *
     * @throws IOException if an unexpected IO problem occurs
     */
    @Test
    public void listen() throws IOException {
        acceptor.listen(null);
    }
    
    /**
     * Test that verifies the IOAcceptor won't listen on any port after 
     * shutdown.
     *
     * @throws IOException if an unexpected IO problem occurs
     */
    @Test (expected=IllegalStateException.class)
    public void listenAfterShutdown() throws IOException {
        acceptor.listen(null);
        acceptor.shutdown();
        acceptor.listen(null);
    }

}
