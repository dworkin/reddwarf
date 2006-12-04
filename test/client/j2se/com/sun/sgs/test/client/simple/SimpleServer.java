package com.sun.sgs.test.client.simple;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import com.sun.sgs.client.comm.ProtocolMessage;
import com.sun.sgs.client.comm.ProtocolMessageDecoder;
import com.sun.sgs.client.comm.ProtocolMessageEncoder;
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

    public void newHandle(IOHandle handle) {
        System.out.println("Server: New Connection");
        handle.setIOHandler(this);
        
        sendAuthRequest(handle);
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

    public void messageReceived(byte[] buffer, IOHandle handle) {
        System.out.println("SimpleServer: messageReceived " + buffer.length);
        messageDecoder.setMessage(buffer);
        int versionNumber = messageDecoder.readVersionNumber();
        if (versionNumber != ProtocolMessage.VERSION) {
            System.out.println("Version number mismatch: " + versionNumber + " " +
                                    ProtocolMessage.VERSION);     
            return;
        }
        int command = messageDecoder.readCommand();
        if (command == ProtocolMessage.AUTHENTICATION_REQUEST) {
            String username = messageDecoder.readString();
            String password = messageDecoder.readString();
            
            System.out.println("UserName: " + username + " Password " + password);
            
            messageEncoder.startMessage(ProtocolMessage.LOGIN);
            if (password.equals("hi!")) {
                messageEncoder.add(new Boolean(true));
                // don't know what the SessionID will look like yet, but 
                // it'll at least probably be a byte array.
                byte[] sessionByteArray = new byte[10];
                for (byte b = 0; b < sessionByteArray.length; b++) {
                    sessionByteArray[b] = b;
                }
                messageEncoder.add(sessionByteArray);
            }
            else {
                messageEncoder.add(new Boolean(false));
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
            handle.sendMessage(byteMessage);
        }
        catch (IOException ioe) {
            ioe.printStackTrace();
        }
        messageEncoder.reset();
    }
    
    private void sendAuthRequest(IOHandle handle) {
        messageEncoder.startMessage(ProtocolMessage.AUTHENTICATION_REQUEST);
        sendMessage(handle);
    }

}
