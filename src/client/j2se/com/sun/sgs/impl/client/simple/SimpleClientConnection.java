package com.sun.sgs.impl.client.simple;

import java.io.IOException;

import com.sun.sgs.impl.client.comm.ClientConnection;
import com.sun.sgs.impl.client.comm.ClientConnectionListener;
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
    
    private final ClientConnectionListener ccl;
    private IOHandle myHandle = null;
    
    SimpleClientConnection(ClientConnectionListener listener) {
        this.ccl = listener;
    }

    // ClientConnection methods

    /**
     * {@inheritDoc}
     * <p>
     * This implementation disconnects the underlying {@code IOHandle}.
     */
    public void disconnect() throws IOException {
        if (myHandle == null) {
            throw new IllegalStateException("not connected");
        }
        myHandle.close();
    }

    /**
     * {@inheritDoc}
     */
    public void sendMessage(byte[] message) {
        /*
        System.err.println("SimpleClientConnection: sendMessage: " +
                HexDumper.format(message));
        */
	try {
            myHandle.sendBytes(message);
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    // IOHandler methods

    /**
     * {@inheritDoc}
     * <p>
     * This implementation notifies the associated
     * {@code ClientConnectionListener}.
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
     * <p>
     * This implementation notifies the associated
     * {@code ClientConnectionListener}.
     * 
     */
    public void disconnected(IOHandle handle) {
        assert handle.equals(this.myHandle);
        System.err.println("SimpleClientConnection: disconnected: " + handle);
        // TODO what is graceful?
        ccl.disconnected(true, null);
    }

    /**
     * {@inheritDoc}
     */
    public void exceptionThrown(IOHandle handle, Throwable exception) {
        assert handle.equals(this.myHandle);
        System.err.println("SimpleClientConnection: exceptionThrown");
        exception.printStackTrace();
        // TODO should we take any action here?  Bubble this up
        // to the CCL somehow?
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implemenation forwards the message to the
     * associated {@code ClientConnectionListener}.
     *
     * @param   handle          the IOHandle on which the message arrived
     * @param   message         the raw byte message from the server
     */
    public void bytesReceived(IOHandle handle, byte[] message) {
        assert handle.equals(this.myHandle);
        ccl.receivedMessage(message);
    }

}
