/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.test.impl.io;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

import junit.framework.Assert;
import junit.framework.TestCase;

import com.sun.sgs.impl.io.ServerSocketEndpoint;
import com.sun.sgs.impl.io.TransportType;
import com.sun.sgs.io.Acceptor;

/**
 * JUnit test class for the SocketAcceptor. 
 * 
 */
public class TestSocketAcceptor
    extends TestCase
{
    private final static int BIND_PORT = 0;
    
    Acceptor<SocketAddress> acceptor;

    @Override
    public void setUp() {
        acceptor = new ServerSocketEndpoint(
                new InetSocketAddress(BIND_PORT),
               TransportType.RELIABLE).createAcceptor();
    }
    
    @Override
    public void tearDown() {
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
    public void testListen() throws IOException {
        acceptor.listen(null);
    }
    
    /**
     * Test that verifies the Acceptor won't listen on any port after 
     * shutdown.
     *
     * @throws IOException if an unexpected IO problem occurs
     */
    public void testListenAfterShutdown() throws IOException {
        acceptor.listen(null);
        acceptor.shutdown();
        try {
            acceptor.listen(null);
        } catch (IllegalStateException expected) {
            // passed
            return;
        }
        Assert.fail("Expected IllegalStateException");
    }

}
