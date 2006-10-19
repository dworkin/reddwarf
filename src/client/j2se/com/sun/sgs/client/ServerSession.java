package com.sun.sgs.client;

import java.nio.ByteBuffer;

/**
 * Represents a client's view of a login session with the server.
 * Each time a client logs in, it will be assigned a different server
 * session.
 *
 * <p>A client, as part of its login procedure (...), specifies a {@link
 * ServerSessionListener} to be notified when a login session is
 * established with the server, as well as to be notified of other
 * events.  Once a session is established with the server, a client is
 * notified by having its {@link ServerSessionListener#connected
 * ServerSessionListener.connected} method invoked with the
 * newly-established <code>ServerSession</code>.  A client can use its
 * <code>ServerSession</code> to send messages to the server, to check
 * if it is connected, or to log out.
 *
 * <p>When a server session becomes disconnected, its listener's
 * {@link ServerSessionListener#disconnected disconnected} method is
 * invoked.  Once a server session is disconnected, it can no longer
 * be used to send messages to the server.  In this case, a client
 * must log in again to obtain a new server session to communicate
 * with the server.
 */
public interface ServerSession {

    /**
     * Returns the client address for this server session.
     *
     * @return the client address for this server session
     *
     * @throws IllegalStateException if this session is disconnected
     */
    ClientAddress getClientAddress();
    
    /**
     * Sends a message to the server.  The specified message is sent
     * asychronously to the server; therefore, a successful invocation
     * of this method does not indicate that the given message was
     * successfully sent.  Messages that are received by the server
     * are delivered in sending order.
     *
     * @param message a message
     *
     * @throws IllegalStateException if this session is disconnected
     */
    void send(ByteBuffer message);

    /**
     * Returns <code>true</code> if this session is connected,
     * otherwise returns <code>false</code>.
     *
     * @return <code>true</code> if this session is connected, and
     * <code>false</code> otherwise
     */
    boolean isConnected();

    /**
     * Initiates logging out from the server.  If <code>force</code>
     * is <code>true</code> then this session is forcibly terminated,
     * for example, by terminating the associated client's network
     * connections. If <code>force</code> is <code>false</code>, then
     * this session is gracefully disconnected, notifying the server
     * that the client logged out.  When the session has been logged
     * out, gracefully or otherwise, the {@link
     * ServerSessionListener#disconnected disconnected} method is
     * invoked on this session's associated {@link
     * ServerSessionListener} passing a boolean indicating whether the
     * disconnection was graceful.
     *
     * @param force if <code>true</code>, this session is forcibly
     * terminated; otherwise the session is gracefully disconnected
     *
     * @throws IllegalStateException if this session is disconnected
     */
    void logout(boolean force);
}
