package com.sun.sgs.impl.client.comm;

import java.io.IOException;
import java.nio.ByteBuffer;

import com.sun.sgs.client.ServerSessionListener;
import com.sun.sgs.client.comm.ClientConnection;
import com.sun.sgs.client.comm.ClientConnectionListener;
import com.sun.sgs.client.simple.ProtocolMessage;
import com.sun.sgs.client.simple.ProtocolMessageEncoder;
import com.sun.sgs.client.simple.ProtocolMessageDecoder;
import com.sun.sgs.io.IOHandle;
import com.sun.sgs.io.IOHandler;

/**
 * A {@code ClientConnection} is the central point of communication with the server.  
 * This {@code SimpleClientConnection} uses an {@link IOHandle} for its transport.  
 * All outbound messages to the server go out via the IOHandle.send.  Incoming
 * messages come in on the {@code IOHandler.messageReceived} callback and are dispatched 
 * to the appropriate callback on either the associated 
 * {@link ClientConnectionListener} or higher level {@link ServerSessionListener}.
 * 
 * @author      Sten Anderson
 * @version     1.0
 */
@SuppressWarnings("hiding")
public class SimpleClientConnection implements ClientConnection, IOHandler {
    
    private ClientConnectionListener ccl;
    private ServerSessionListener ssl;
    private IOHandle handle;
    private ProtocolMessageDecoder messageDecoder;
    
    public SimpleClientConnection(ClientConnectionListener listener) {
        this.ccl = listener;
        messageDecoder = new ProtocolMessageDecoder();
    }

    public void disconnect() throws IOException{
        handle.close();
    }

    // refactor to "sendMessage"
    public void sendMessage(byte[] message) {
        System.err.println("SimpleClientConnection: sendMessage: " + message);
        
        try {
            handle.sendBytes(message);
        }
        catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    public void connected(IOHandle handle) {
        System.err.println("SimpleClientConnection: connected: " + handle);
        this.handle = handle;
        ccl.connected(this);
    }

    public void disconnected(IOHandle handle) {
        System.err.println("SimpleClientConnection: disconnected: " + handle);
        ccl.disconnected(true, null);
    }

    public void exceptionThrown(Throwable exception, IOHandle handle) {
        System.err.println("SimpleClientConnection: exceptionThrown");
        exception.printStackTrace();
    }

    /**
     * Call back on the IOHandler.  This is called when there is an incoming
     * message from the server.  Connection related events are parsed here, but 
     * everything else is forwarded to the associated ServerSessionListener.
     * 
     * @param   message         the raw byte message from the server
     * @param   handle          the IOHandle on which the message arrived
     */
    public void bytesReceived(byte[] message, IOHandle handle) {
        System.err.println("SimpleClientConnection: bytesReceived: " +
                message);
        
        messageDecoder.setMessage(message);
        int versionNumber = messageDecoder.readVersionNumber();
        // TODO check the version number against the current version.
        // It's not clear yet where the "current version number" will live, 
        // but if it's a mismatch, bail out right away and notify the client.
        if (versionNumber != ProtocolMessage.VERSION) {
            throw new RuntimeException("Bad protocol version: " +
                    Integer.toHexString(versionNumber));
        }
        
        // this doesn't quite work here.  The ClientConnectionListener has no
        // methods for being notified of a successful (or failed) login.  It
        // needs to interpret this for itself and pass it up to the
        // SimpleClientListener.  There doesn't seem to be a reason for the 
        // ClientConnection to call CCL.sessionStarted
        /*int command = messageDecoder.readCommand();
        if (command == ProtocolMessage.LOGIN) {
            ssl = ccl.sessionStarted(message);
            return;
        }*/
        
        // if the ClientConnection doesn't understand the message, 
        // forward onto the client.
        ccl.receivedMessage(message);
        
    }

}
