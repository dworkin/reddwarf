package com.sun.sgs.test.impl.io;

import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

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
 * This suite of tests is intended to test the functionality of the 
 * {code CompleteMessageFilter}.
 */
public class MessageFilterTest {

    private final static int DELAY = 2000;
    
    private final static int SERVER_PORT = 5000;
    private final SocketAddress SERVER_ADDRESS = 
        new InetSocketAddress("", SERVER_PORT);

    private final Semaphore sema = new Semaphore(0, true);

    Acceptor<SocketAddress> acceptor;
    Connection connection = null;
    
    /**
     * Set up an Acceptor with a CompleteMessageFilter installed that echos
     * the packets back to the client.
     */
    @Before
    public void init() {
        connection = null;
        acceptor = new ServerSocketEndpoint(
            new InetSocketAddress(SERVER_PORT), TransportType.RELIABLE).
                createAcceptor();
        
        try {
            acceptor.listen(new AcceptorListener() {

                public ConnectionListener newConnection() {
                    return new ConnectionAdapter() {
                        
                        @Override
                        public void bytesReceived(Connection conn, byte[] buffer) {
                            byte[] echoMessage = new byte[buffer.length];
                            try {
                                conn.sendBytes(echoMessage);
                            }
                            catch (IOException ioe) {}
                        }
                    };
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
     * Perform cleanup after each test case.
     */
    @After
    public void cleanup() {
        if (connection != null) {
            try {
                connection.close();
            } catch (IOException e) {
                System.err.println("Ignoring exception on close");
                e.printStackTrace();
                // ignore the exception
            }
            connection = null;
        }
        acceptor.shutdown();
        acceptor = null;
    }
    
    /**
     * This test ensures that a message big enough to be split into pieces
     * arrives in one piece on the other end (and is subsequently echoed back).
     * MINA's internal buffers max out at about 8k, so a 100k message will be 
     * split into 12 or so chunks.
     *
     * @throws IOException if an unexpected IO problem occurs
     */
    @Test
    public void bigMessage() throws IOException {
        Connector<SocketAddress> connector = 
                new SocketEndpoint(SERVER_ADDRESS, TransportType.RELIABLE).
                    createConnector();
        
        
        ConnectionListener listener = new ConnectionAdapter() {
            
            int messageSize = 100000;

            @Override
            public void connected(Connection conn) {
                MessageFilterTest.this.connection = conn;
                byte[] bigMessage = new byte[messageSize];
                for (int i = 0; i < messageSize; i++) {
                    bigMessage[i] = (byte) 1;
                }
                try {
                    conn.sendBytes(bigMessage);
                }
                catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }

            @Override
            public void bytesReceived(Connection conn, byte[] buffer) {
                Assert.assertEquals(messageSize, buffer.length);
                try {
                    conn.close();
                }
                catch (IOException ioe) {}

                sema.release();
            }

            @Override
            public void disconnected(Connection conn) {
                MessageFilterTest.this.connection = null;
            }
        };

        connector.connect(listener);

        try {
            sema.tryAcquire(DELAY, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
            fail("Interrupted!" + e.getMessage());
        }

    } 
    
    private int bytesIn;
    
    /**
     * Tests the basic interoperability of the message filter between
     * client and server.
     *
     * @throws IOException if an unexpected IO problem occurs
     */
    @Test
    public void completeMessage() throws IOException {
        bytesIn = 0;
        final int messageSize = 1000;
        Connector<SocketAddress> connector = 
                    new SocketEndpoint(SERVER_ADDRESS, TransportType.RELIABLE).
                        createConnector();

        ConnectionListener listener = new ConnectionAdapter() {

            @Override
            public void connected(Connection conn) {
                MessageFilterTest.this.connection = conn;
                byte[] message = new byte[messageSize];
                try {
                    conn.sendBytes(message);
                }
                catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }

            @Override
            public void bytesReceived(Connection conn, byte[] buffer) {
                bytesIn += buffer.length;
                System.err.println("Got " + buffer.length +
                        " bytes, total = " + bytesIn);

                if (bytesIn >= messageSize) {
                    sema.release();
                }
            }

            @Override
            public void disconnected(Connection conn) {
                MessageFilterTest.this.connection = null;
            }
        };

        connector.connect(listener);

        try {
            sema.tryAcquire(DELAY, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
            fail("Interrupted!" + e.getMessage());
        }

        Assert.assertEquals(messageSize, bytesIn);

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
