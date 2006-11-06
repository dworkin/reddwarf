package com.sun.sgs.io;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import com.sun.sgs.impl.io.SocketAcceptor;
import com.sun.sgs.io.AcceptedHandleListener;
import com.sun.sgs.io.IOAcceptor;
import com.sun.sgs.io.IOHandle;

/**
 * A test harness for the server {@code Acceptor} code.
 * 
 * @author      Sten Anderson
 * @version     1.0
 */
public class ServerTest implements AcceptedHandleListener, IOHandler {

    IOAcceptor acceptor;

    public ServerTest() {
        acceptor = new SocketAcceptor();
    }

    public void start() {
        int port = 5150;
        System.out.println("Listening on port " + port);
        try {
            acceptor.listen(new InetSocketAddress("127.0.0.1", port), this);
        }
        catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    public final static void main(String[] args) {
        new ServerTest().start();
    }

    public void newHandle(IOHandle handle) {
        System.out.println("ServerTest: New Connection");
        
        handle.setIOHandler(this);

    }

    public void connected(IOHandle handle) {
        System.out.println("ServerTest: connected");
    }

    public void disconnected(IOHandle handle) {
        System.out.println("ServerTest: disconnected");
        acceptor.shutdown();
    }

    public void exceptionThrown(Throwable exception, IOHandle handle) {
        System.out.print("ServerTest: exceptionThrown ");
        exception.printStackTrace();
    }

    public void messageReceived(ByteBuffer buffer, IOHandle handle) {
        System.out.println("ServerTest messageReceived " + buffer.get());
        try {
            handle.sendMessage(buffer);
        }
        catch (IOException ioe) {
            ioe.printStackTrace();
        }
        
    }

}
