package com.sun.sgs.client;

import java.net.PasswordAuthentication;

/**
 * A client's listener for handling messages sent from server to
 * client and for handling other connection-related events.
 *
 * <p>A <code>SimpleClientListener</code> for a client (specified as
 * part of the login procedure...) is notified in the following cases:
 * when a connection is established with the server ({@link
 * #loggedIn loggedIn}), a connection with the server is being
 * re-established ({@link #reconnecting reconnecting}), a connection
 * has been re-established ({@link #reconnected reconnected}), or
 * finally when client becomes disconnected,
 * gracefully or otherwise ({@link #disconnected disconnected}).
 */
public interface SimpleClientListener extends ServerSessionListener {

    /**
     * Notifies this listener that a connection was established with
     * the server.
     */
    void loggedIn();
    
    /**
     * Requests login credentials from this listener.
     * 
     * @param prompt the login prompt
     * @return the PasswordAuthentication credentials
     */
    PasswordAuthentication getPasswordAuthentication(String prompt);
    
    /**
     * Notifies this listener that its associated server 
     * connection could not be established due to login failure.
     */
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
