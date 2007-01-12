package com.sun.sgs.test.io;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.notification.Failure;

import com.sun.sgs.impl.io.AcceptorFactory;
import com.sun.sgs.impl.io.IOConstants.TransportType;
import com.sun.sgs.io.IOAcceptor;

/**
 * JUnit test class for the SocketAcceptor. 
 * 
 */
public class SocketAcceptorTest {
    
    private final static int BIND_PORT = 5000;
    
    IOAcceptor acceptor;
    
    @Before
    public void init() {
        acceptor = AcceptorFactory.createAcceptor(TransportType.RELIABLE);
    }
    
    @After
    public void cleanup() {
        acceptor.shutdown();
        acceptor = null;
    }
    
    /**
     * Test listening on one port.
     */
    @Test
    public void listen() {
        try {
            acceptor.listen(new InetSocketAddress("localhost", BIND_PORT), null);
        }
        catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    /**
     * Test listening on mulitple ports.
     */
    @Test
    public void listenMultiple() {
        try {
            for (int i = 0; i < 10; i++) {
                acceptor.listen(new InetSocketAddress("localhost", 
                                                    BIND_PORT + i), null);
                                                        
            }
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
        SocketAddress address = new InetSocketAddress("localhost", BIND_PORT); 
        try {
            acceptor.listen(address, null);
            acceptor.shutdown();
            acceptor.listen(address, null);
        }
        catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

}
