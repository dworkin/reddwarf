package com.sun.sgs.test.io;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

import com.sun.sgs.impl.io.CompleteMessageFilter;
import com.sun.sgs.impl.io.SocketEndpoint;
import com.sun.sgs.impl.io.IOConstants.TransportType;
import com.sun.sgs.io.IOAcceptorListener;
import com.sun.sgs.io.IOAcceptor;
import com.sun.sgs.io.IOHandle;
import com.sun.sgs.io.IOHandler;
import com.sun.sgs.test.client.simple.SimpleServer;

/**
 * A test harness for the server {@code Acceptor} code.
 * 
 * @author      Sten Anderson
 * @version     1.0
 */
public class ServerTest implements IOHandler {

    private static final String DEFAULT_HOST = "0.0.0.0";
    private static final String DEFAULT_PORT = "5150";
    
    IOAcceptor<SocketAddress> acceptor;
    private int numConnections;

    public ServerTest() { }

    public void start() {
        String host = System.getProperty("host", DEFAULT_HOST);
        String portString = System.getProperty("port", DEFAULT_PORT);
        int port = Integer.valueOf(portString);
        InetSocketAddress addr = new InetSocketAddress(host, port);
        try {
            acceptor = new SocketEndpoint(
                    new InetSocketAddress(host, port),
                   TransportType.RELIABLE).createAcceptor();
            acceptor.listen(new IOAcceptorListener() {
                /**
                 * {@inheritDoc}
                 */
                public IOHandler newHandle() {
                    return ServerTest.this;
                }

                /**
                 * {@inheritDoc}
                 */
                public void disconnected() {
                    // TODO Auto-generated method stub
                }
            },
            CompleteMessageFilter.class);
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        System.out.println("Listening on " +
                acceptor.getEndpoint().getAddress());
    }

    public final static void main(String[] args) {
        ServerTest server = new ServerTest();
        synchronized(server) {
            server.start();
            try {
                server.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void connected(IOHandle handle) {
        synchronized (this) {
            numConnections++;
        }
        System.out.println("ServerTest: connected");
    }

    public void disconnected(IOHandle handle) {
        synchronized (this) {
            numConnections--;
            if (numConnections <= 0) {
                acceptor.shutdown();
            }
            this.notifyAll();
        }
    }

    public void exceptionThrown(Throwable exception, IOHandle handle) {
        System.out.print("ServerTest: exceptionThrown ");
        exception.printStackTrace();
    }

    public void bytesReceived(byte[] message, IOHandle handle) {
        byte[] buffer = new byte[message.length]; 
        System.arraycopy(message, 0, buffer, 0, message.length); 
        try {
            handle.sendBytes(buffer);
        }
        catch (IOException ioe) {
            ioe.printStackTrace();
        }
        
    }

}
