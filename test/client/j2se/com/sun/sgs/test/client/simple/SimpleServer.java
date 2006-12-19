package com.sun.sgs.test.client.simple;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import com.sun.sgs.client.simple.ProtocolMessage;
import com.sun.sgs.client.simple.ProtocolMessageDecoder;
import com.sun.sgs.client.simple.ProtocolMessageEncoder;
import com.sun.sgs.impl.io.AcceptorFactory;
import com.sun.sgs.impl.io.IOConstants.TransportType;
import com.sun.sgs.io.AcceptedHandleListener;
import com.sun.sgs.io.IOAcceptor;
import com.sun.sgs.io.IOHandle;
import com.sun.sgs.io.IOHandler;

/**
 * A simple server harness for testing the Client API.  This server
 * will respond to the client/server protocol.  It uses the IO framework
 * for its networking.
 * 
 * @author      Sten Anderson
 * @version     1.0
 */
public class SimpleServer implements AcceptedHandleListener, IOHandler {
    
    private final static int VERSION = 1;
    
    private IOAcceptor acceptor;
    private int port = 5150;
    private ProtocolMessageDecoder messageDecoder;
    private ProtocolMessageEncoder messageEncoder;
    
    public SimpleServer() {
        acceptor = AcceptorFactory.createAcceptor(TransportType.RELIABLE);
        
        messageDecoder = new ProtocolMessageDecoder();
        messageEncoder = new ProtocolMessageEncoder();
    }
    
    private void start() {
        System.out.println("Listening on port " + port);
        try {
            acceptor.listen(new InetSocketAddress("127.0.0.1", port), 
                    SimpleServer.this);
        }
        catch (IOException ioe) {
            ioe.printStackTrace();
        }                

    }
    
    public final static void main(String[] arg) {
        new SimpleServer().start();
    }

    public IOHandler newHandle(IOHandle handle) {
        System.out.println("Server: New Connection");
        
        return this;
    }
    
    public void connected(IOHandle handle) {
        System.out.println("SimpleServer: connected");
    }

    public void disconnected(IOHandle handle) {
        System.out.println("SimpleServer: disconnected");
        System.exit(0);
    }

    public void exceptionThrown(Throwable exception, IOHandle handle) {
        System.out.println("SimpleServer: exceptionThrown ");
        exception.printStackTrace();
    }

    public void bytesReceived(byte[] buffer, IOHandle handle) {
        System.out.println("SimpleServer: messageReceived " + buffer.length);
        messageDecoder.setMessage(buffer);
        int versionNumber = messageDecoder.readVersionNumber();
        if (versionNumber != ProtocolMessage.VERSION) {
            System.out.println("Version number mismatch: " + versionNumber + " " +
                                    ProtocolMessage.VERSION);     
            return;
        }
        int command = messageDecoder.readCommand();
        if (command == ProtocolMessage.LOGIN_REQUEST) {
            String username = messageDecoder.readString();
            String password = messageDecoder.readString();
            
            System.out.println("UserName: " + username + " Password " + password);
            
            if (password.equals("hi!")) {
                messageEncoder.startMessage(ProtocolMessage.LOGIN_SUCCESS);
                // don't know what the SessionID will look like yet, but 
                // it'll at least probably be a byte array.
                byte[] sessionByteArray = new byte[10];
                for (byte b = 0; b < sessionByteArray.length; b++) {
                    sessionByteArray[b] = b;
                }
                messageEncoder.add(sessionByteArray);
            }
            else {
                messageEncoder.startMessage(ProtocolMessage.LOGIN_FAILURE);
                messageEncoder.add("Bad password");
            }
            sendMessage(handle);
        }
    }
    
    private void sendMessage(IOHandle handle) {
        try {
            byte[] byteMessage = messageEncoder.getMessage();
            System.out.println("byteMessage: " + byteMessage.length + " " + 
                                byteMessage[0]);
            handle.sendBytes(byteMessage);
        }
        catch (IOException ioe) {
            ioe.printStackTrace();
        }
        messageEncoder.reset();
    }
    
    private void sendAuthRequest(IOHandle handle) {
        messageEncoder.startMessage(ProtocolMessage.LOGIN_REQUEST);
        sendMessage(handle);
    }

}
