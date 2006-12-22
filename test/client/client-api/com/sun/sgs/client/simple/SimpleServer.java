package com.sun.sgs.client.simple;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import com.sun.sgs.client.SessionId;
import com.sun.sgs.impl.io.AcceptorFactory;
import com.sun.sgs.impl.io.IOConstants.TransportType;
import com.sun.sgs.io.AcceptedHandleListener;
import com.sun.sgs.io.IOAcceptor;
import com.sun.sgs.io.IOHandle;
import com.sun.sgs.io.IOHandler;

import static com.sun.sgs.client.simple.ProtocolMessage.*;

/**
 * A simple server harness for testing the Client API.  This server
 * will respond to the client/server protocol.  It uses the IO framework
 * for its networking.
 * 
 * @author      Sten Anderson
 * @version     1.0
 */
public class SimpleServer implements AcceptedHandleListener, IOHandler {
    
    private IOAcceptor acceptor;
    private int port = 5150;
    private long sequenceNumber;
    
    // just a place holder for some sort of ID
    private SessionId serverID;
    
    public SimpleServer() {
        acceptor = AcceptorFactory.createAcceptor(TransportType.RELIABLE);
        serverID = SessionId.fromBytes(new byte[10]);
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
        ProtocolMessageDecoder messageDecoder = 
                                    new ProtocolMessageDecoder(buffer);
        int versionNumber = messageDecoder.readVersionNumber();
        if (versionNumber != VERSION) {
            System.out.println("Version number mismatch: " + versionNumber + 
                                " " + VERSION);     
            return;
        }
        int service = messageDecoder.readServiceNumber();
        int command = messageDecoder.readCommand();
        if (command == LOGIN_REQUEST) {
            ProtocolMessageEncoder messageEncoder = null;
            
            String username = messageDecoder.readString();
            String password = messageDecoder.readString();
            
            System.out.println("UserName: " + username + " Password " + password);
            
            if (password.equals("secret")) {
                messageEncoder = new ProtocolMessageEncoder(APPLICATION_SERVICE,
                                                            lOGIN_SUCCESS);
                
                // don't know what the SessionID will look like yet, but 
                // it'll at least probably be a byte array.
                messageEncoder.writeSessionId(serverID);
                byte[] sessionByteArray = new byte[10];
                for (byte b = 0; b < sessionByteArray.length; b++) {
                    sessionByteArray[b] = b;
                }
                messageEncoder.writeBytes(sessionByteArray);
                
                // send the reconnect key
                messageEncoder.writeBytes(new byte[10]);
            }
            else {
                messageEncoder = new ProtocolMessageEncoder(APPLICATION_SERVICE, 
                                                                LOGIN_FAILURE);
                messageEncoder.writeString("Bad password");
            }
            sendMessage(messageEncoder, handle);
        }
        else if (command == MESSAGE_TO_SERVER) {
            short messageLength = messageDecoder.readShort();
            String serverMessage = messageDecoder.readString();
            System.out.println("Received general server message: " + 
                       messageLength + " " + serverMessage);
            
            if (serverMessage.equals("Join Channel")) {
                ProtocolMessageEncoder messageEncoder = new ProtocolMessageEncoder(
                        CHANNEL_SERVICE, CHANNEL_JOIN);
                
                messageEncoder.writeString("Test Channel");
                sendMessage(messageEncoder, handle);
                
                sendPeriodicChannelMessages(handle);
            }
            else if (serverMessage.equals("Leave Channel")) {
                ProtocolMessageEncoder messageEncoder = new ProtocolMessageEncoder(
                        CHANNEL_SERVICE, CHANNEL_LEAVE);
                messageEncoder.writeString("Test Channel");
                sendMessage(messageEncoder, handle);
            }
        }
        else if (command == CHANNEL_SEND_REQUEST) {
            String channelName = messageDecoder.readString();
            int numRecipients = messageDecoder.readInt();
            String messageStr = messageDecoder.readString();
            System.out.println("Channel Message " + channelName + 
                " num recipients " + numRecipients + " message " + messageStr);
            
            ProtocolMessageEncoder messageEncoder = new ProtocolMessageEncoder(
                    CHANNEL_SERVICE, CHANNEL_LEAVE);
            
            messageEncoder.writeString(channelName);
            sendMessage(messageEncoder, handle);
        }
        else if (command == LOGOUT_SUCCESS) {
            ProtocolMessageEncoder messageEncoder = new ProtocolMessageEncoder(
                    APPLICATION_SERVICE, LOGOUT_SUCCESS);
            
            sendMessage(messageEncoder, handle);
        }
    }
    
    private void sendPeriodicChannelMessages(final IOHandle handle) {
        Thread t = new Thread() {
            public void run() {
                while (true) {
                    sendChannelMessage("Channel Message " + sequenceNumber, handle);
                    try {
                        Thread.sleep((int) (Math.random() * 1000) + 50);
                    }
                    catch (InterruptedException ie) {
                        ie.printStackTrace();
                    }
                }
            }
        };
        t.start();
    }
    
    private void sendChannelMessage(String chanMessage, IOHandle handle) {
        ProtocolMessageEncoder message = new ProtocolMessageEncoder(
                CHANNEL_SERVICE, CHANNEL_MESSAGE);

        message.writeString("Test Channel");
        sequenceNumber++;
        message.writeLong(sequenceNumber % 3 == 0 ? 2 : sequenceNumber);
        message.writeShort(0);
        
        message.writeShort(2 + chanMessage.getBytes().length);
        message.writeString(chanMessage);
        sendMessage(message, handle);
    }
    
    private void sendMessage(ProtocolMessageEncoder messageEncoder, 
                                                            IOHandle handle) {
        try {
            handle.sendBytes(messageEncoder.getMessage());
        }
        catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }
    
    private void sendAuthRequest(IOHandle handle) {
        ProtocolMessageEncoder messageEncoder = new ProtocolMessageEncoder(
                                    APPLICATION_SERVICE, LOGIN_REQUEST);
        sendMessage(messageEncoder, handle);
    }

}
