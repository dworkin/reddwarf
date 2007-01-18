package com.sun.sgs.impl.client.simple;

import java.io.IOException;

import com.sun.sgs.impl.client.comm.ClientConnection;
import com.sun.sgs.impl.client.comm.ClientConnectionListener;
import com.sun.sgs.impl.util.HexDumper;
import com.sun.sgs.io.IOHandle;
import com.sun.sgs.io.IOHandler;

/**
 * A {@code ClientConnection} is the central point of communication with the 
 * server. This {@code SimpleClientConnection} uses an {@link IOHandle} for its 
 * transport.  All outbound messages to the server go out via the IOHandle.send.
 * Incoming messages come in on the {@code IOHandler.messageReceived} callback 
 * and are dispatched to the appropriate callback on either the associated 
 * {@link ClientConnectionListener}.
 * 
 * @author      Sten Anderson
 * @version     1.0
 */
public class SimpleClientConnection implements ClientConnection, IOHandler {
    
    private ClientConnectionListener ccl;
    private IOHandle myHandle;
    
    SimpleClientConnection(ClientConnectionListener listener) {
        this.ccl = listener;
    }

    // ClientConnection methods

    /**
     * Disconnects the underlying {@code IOHandle}.
     * 
     * @throws IOException if the handle is not connected
     */
    public void disconnect() throws IOException{
        myHandle.close();
    }

    /**
     * {@inheritDoc}
     */
    public void sendMessage(byte[] message) {
        System.err.println("SimpleClientConnection: sendMessage: " +
                HexDumper.format(message));
        try {
            myHandle.sendBytes(message);
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    // IOHandler methods

    /**
     * Called when the associated {@code IOHandle} finishes connecting and 
     * becomes "live".  The associated {@code ClientConnectionListener} is
     * notified.
     * 
     * @param handle    the associated handle that connected
     */
    public void connected(IOHandle handle) {
        System.err.println("SimpleClientConnection: connected: " + handle);
        this.myHandle = handle;
        ccl.connected(this);
    }

    /**
     * {@inheritDoc}
     * 
     * This associated {@code ClientConnectionListener} is notified of the 
     * disconnect.
     * 
     * TODO what is graceful?
     */
    public void disconnected(IOHandle handle) {
        assert handle.equals(this.myHandle);
        System.err.println("SimpleClientConnection: disconnected: " + handle);
        ccl.disconnected(true, null);
    }

    /**
     * {@inheritDoc}
     * 
     * TODO should we take any action here?  Bubble this up to the CCL somehow?
     */
    public void exceptionThrown(Throwable exception, IOHandle handle) {
        assert handle.equals(this.myHandle);
        System.err.println("SimpleClientConnection: exceptionThrown");
        exception.printStackTrace();
    }

    /**
     * Call back on the IOHandler.  This is called when there is an incoming
     * message from the server.  All messages are forwarded on to the
     * associated {@code ClientConnectionListener}.
     * 
     * @param   message         the raw byte message from the server
     * @param   handle          the IOHandle on which the message arrived
     */
    public void bytesReceived(byte[] message, IOHandle handle) {
        assert handle.equals(this.myHandle);
        System.err.println("SimpleClientConnection: bytesReceived: " +
                message);
        ccl.receivedMessage(message);
    }

}
