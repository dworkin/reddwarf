package com.sun.sgs.impl.client.simple;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.sgs.impl.client.comm.ClientConnection;
import com.sun.sgs.impl.client.comm.ClientConnectionListener;
import com.sun.sgs.impl.util.LoggerWrapper;
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
class SimpleClientConnection implements ClientConnection, IOHandler {

    /** The logger for this class. */
    private static final LoggerWrapper logger =
        new LoggerWrapper(Logger.getLogger(
                SimpleClientConnection.class.getName()));
    
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
            RuntimeException re =
                new IllegalStateException("Not connected");
            logger.logThrow(Level.FINE, re, re.getMessage());
            throw re;
        }
        myHandle.close();
    }

    /**
     * {@inheritDoc}
     */
    public void sendMessage(byte[] message) {
        if (logger.isLoggable(Level.FINEST)) {
            // FIXME comment back in when HexDumper is committed
            //logger.log(Level.FINEST, "send on {0}: {1}",
            //    myHandle, HexDumper.format(message));
        }
	try {
            myHandle.sendBytes(message);
        } catch (IOException e) {
            logger.logThrow(Level.FINE, e, "Send failed:");
        }
    }

    // IOHandler methods

    /**
     * {@inheritDoc}
     * <p>
     * This implementation notifies the associated
     * {@code ClientConnectionListener}.
     */
    public void connected(IOHandle handle) {
        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "connected: {0}", handle);
        }
        this.myHandle = handle;
        ccl.connected(this);
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation notifies the associated
     * {@code ClientConnectionListener}.
     */
    public void disconnected(IOHandle handle) {
        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "disconnected: {0}", handle);
        }
        assert handle.equals(this.myHandle);
        // TODO what is graceful?
        ccl.disconnected(true, null);
    }

    /**
     * {@inheritDoc}
     */
    public void exceptionThrown(IOHandle handle, Throwable exception) {
        if (logger.isLoggable(Level.FINER)) {
            logger.logThrow(Level.FINER, exception,
                    "exception on: {0}: ", handle);
        }
        assert handle.equals(this.myHandle);

        // TODO should we take any action here?  Bubble this up
        // to the CCL somehow?
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implemenation forwards the message to the
     * associated {@code ClientConnectionListener}.
     */
    public void bytesReceived(IOHandle handle, byte[] message) {
        if (logger.isLoggable(Level.FINEST)) {
            // FIXME comment back in when HexDumper is committed
            //logger.log(Level.FINEST, "recv on {0}: {1}",
            //    handle, HexDumper.format(message));
        }
        assert handle.equals(this.myHandle);
        ccl.receivedMessage(message);
    }

}
