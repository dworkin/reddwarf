package com.sun.sgs.test.io;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import com.sun.sgs.impl.io.AcceptorFactory;
import com.sun.sgs.impl.io.CompleteMessageFilter;
import com.sun.sgs.impl.io.IOConstants.TransportType;
import com.sun.sgs.io.AcceptedHandleListener;
import com.sun.sgs.io.IOAcceptor;
import com.sun.sgs.io.IOHandle;
import com.sun.sgs.io.IOHandler;

/**
 * A test harness for the server {@code Acceptor} code.
 * 
 * @author      Sten Anderson
 * @version     1.0
 */
public class ServerTest implements AcceptedHandleListener, IOHandler {

    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final String DEFAULT_PORT = "5150";
    
    IOAcceptor acceptor;
    private int numConnections;

    public ServerTest() {
        acceptor = AcceptorFactory.createAcceptor(TransportType.RELIABLE, 
                                            Executors.newCachedThreadPool());
    }

    public void start() {
        String host = System.getProperty("host", DEFAULT_HOST);
        String portString = System.getProperty("port", DEFAULT_PORT);
        int port = Integer.valueOf(portString);
        InetSocketAddress addr = new InetSocketAddress(host, port);
        System.out.println("Listening on port " + port);
        try {
            acceptor.listen(addr, this, CompleteMessageFilter.class);
        }
        catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    public final static void main(String[] args) {
        new ServerTest().start();
    }

    public IOHandler newHandle(IOHandle handle) {
        synchronized (this) {
            numConnections++;
        }
        return this;
    }

    public void connected(IOHandle handle) {
        System.out.println("ServerTest: connected");
    }

    public void disconnected(IOHandle handle) {
        synchronized (this) {
            numConnections--;
        }
        if (numConnections <= 0) {
            acceptor.shutdown();
            System.exit(0);
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
