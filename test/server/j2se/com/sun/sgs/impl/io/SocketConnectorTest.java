package com.sun.sgs.impl.io;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.Executors;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.sun.sgs.impl.io.IOConstants.TransportType;
import com.sun.sgs.io.AcceptedHandleListener;
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
    private final SocketAddress ADDRESS = 
                                new InetSocketAddress("localhost", BIND_PORT);
    
    IOAcceptor acceptor;
    private boolean connected = false;
    
    
    @Before
    public void init() {
        connected = false;
        acceptor = AcceptorFactory.createAcceptor(TransportType.RELIABLE);
        
        try {
            acceptor.listen(ADDRESS, new AcceptedHandleListener() {

                public IOHandler newHandle(IOHandle handle) {
                    return new IOHandlerAdapter();
                }
                
            });
        }
        catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }
    
    @After
    public void cleanup() {
        acceptor.shutdown();
        acceptor = null;
        
        // There seems to be a race condition between shutting the acceptor down
        // and creating a new one (bound to the same port) in "init".  This 
        // delay gives the acceptor time to shutdown.
        synchronized(this) {
            try {
                wait(DELAY);
            }
            catch (InterruptedException ie) {}
        }
    }
    
    /**
     * Test connectivity.
     */
    @Test
    public void testConnect() {
        IOConnector connector = 
                    ConnectorFactory.createConnector(TransportType.RELIABLE, 
                            Executors.newCachedThreadPool());
        
        
        IOHandler handler = new IOHandlerAdapter() {
            public void connected(IOHandle handle) {
                connected = true;
                notifyAll();
            }
           
        };
        
        connector.connect(ADDRESS, handler);
        
        synchronized(this) {
            try {
                wait(DELAY);
            }
            catch (InterruptedException ie) {}
        }
        Assert.assertTrue(connected);

    }    
    
    /**
     * Test the IOConnector's lack of re-usability.
     *
     */
    @Test (expected=IllegalStateException.class)
    public void testMultipleConnect() {
        IOConnector connector = 
            ConnectorFactory.createConnector(TransportType.RELIABLE);


        IOHandler handler = new IOHandlerAdapter();
        
        connector.connect(ADDRESS, handler);
        
        connector.connect(ADDRESS, handler);
    }
    
    @Test (expected=IllegalStateException.class)
    public void testShutdown() {
        final IOConnector connector = 
            ConnectorFactory.createConnector(TransportType.RELIABLE);


        IOHandler handler = new IOHandlerAdapter();
        
        connector.shutdown();
        connector.connect(ADDRESS, handler);
        
    }
    
    private static class IOHandlerAdapter implements IOHandler {
        public void bytesReceived(byte[] buffer, IOHandle handle) {
        }

        public void connected(IOHandle handle) {
        }

        public void disconnected(IOHandle handle) {
        }

        public void exceptionThrown(Throwable exception, IOHandle handle) {
        }   
    }
    
}
