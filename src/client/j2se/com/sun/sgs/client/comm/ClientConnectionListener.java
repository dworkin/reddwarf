package com.sun.sgs.client.comm;

import com.sun.sgs.client.ServerSessionListener;

public interface ClientConnectionListener {

    void connected(ClientConnection connection);

    /**
     * Notifies this listener that its associated server connection is in
     * the process of reconnecting with the server.
     *
     * <p>If a connection can be re-established with the server in a
     * timely manner, this listener's {@link #reconnected reconnected}
     * method will be invoked.  Otherwise, if a connection cannot be
     * re-established, this listener's <code>disconnected</code>
     * method will be invoked with <code>false</code> indicating that
     * the associated session is disconnected from the server and the
     * client must log in again.
     */
    void reconnecting(byte[] message);

    /**
     * Notifies this listener whether the associated server connection is
     * successfully reconnected. 
     */
    void reconnected(byte[] message);
    
    /**
     * Notifies this listener that the associated server connection is
     * disconnected.
     *
     * <p>If <code>graceful</code> is <code>true</code>, the
     * disconnection was due to the associated client gracefully
     * logging out; otherwise, the disconnection was due to other
     * circumstances, such as forced disconnection.
     *
     * @param graceful <code>true</code> if disconnection was due to
     * the associated client gracefully logging out, and
     * <code>false</code> otherwise
     */
    void disconnected(boolean graceful, byte[] message);

    void receivedMessage(byte[] message);

    ServerSessionListener sessionStarted(byte[] message);
}
