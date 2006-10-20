package com.sun.sgs.client;

import java.net.PasswordAuthentication;

/**
 * A client's listener for handling messages sent from server to
 * client and for handling other connection-related events.
 *
 * <p>A <code>ServerSessionListener</code> for a client (specified as
 * part of the login procedure...) is notified in the following cases:
 * when a {@link ServerSession} is established with the server ({@link
 * #connected connected}), a connection with the server is being
 * re-established ({@link #reconnecting reconnecting}), a connection
 * has been re-established ({@link #reconnected reconnected}), or
 * finally when the associated server session becomes disconnected,
 * gracefully or otherwise ({@link #disconnected disconnected}).
 *
 * <p>If a server session becomes disconnected, it can no longer be
 * used to send messages to the server.  In this case, a client must
 * log in again to obtain a new server session to communicate with the
 * server.
 */
public interface SimpleClientListener extends ServerSessionListener {

    void connected();
    
    PasswordAuthentication getPasswordAuthentication(String prompt);

    void loginFailed(String reason);

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
    void reconnecting();

    /**
     * Notifies this listener whether the associated server connection is
     * successfully reconnected. 
     */
    void reconnected();
    
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
    void disconnected(boolean graceful);
}
