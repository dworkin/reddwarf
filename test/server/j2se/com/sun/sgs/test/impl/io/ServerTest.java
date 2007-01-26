package com.sun.sgs.test.impl.io;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

import com.sun.sgs.impl.io.SocketEndpoint;
import com.sun.sgs.impl.io.TransportType;
import com.sun.sgs.io.IOAcceptorListener;
import com.sun.sgs.io.IOAcceptor;
import com.sun.sgs.io.IOHandle;
import com.sun.sgs.io.IOHandler;

/**
 * A test harness for the server {@code Acceptor} code.
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
            });
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        System.out.println("Listening on " +
                acceptor.getBoundEndpoint().getAddress());
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

    public void exceptionThrown(IOHandle handle, Throwable exception) {
        System.out.print("ServerTest: exceptionThrown ");
        exception.printStackTrace();
    }

    public void bytesReceived(IOHandle handle, byte[] message) {
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
