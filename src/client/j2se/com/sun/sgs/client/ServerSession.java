package com.sun.sgs.client;

import java.io.IOException;

/**
 * Represents a client's view of a login session with the server. Each time
 * a client logs in, it will be assigned a different server session. A
 * client can use its {@code ServerSession} to send messages to the
 * server, to check if it is connected, or to log out.
 * <p>
 * A server session has an associated {@link ServerSessionListener} that is
 * notified of session communication events such as message receipt, channel
 * joins, reconnection, or disconnection. Once a server session is
 * disconnected, it can no longer be used to send messages to the server. In
 * this case, a client must log in again to obtain a new server session to
 * communicate with the server.
 */
public interface ServerSession {

    /**
     * Returns the session identifier for this server session.
     * 
     * @return the session identifier for this server session
     *
     * @throws IllegalStateException if this session is disconnected
     */
    SessionId getSessionId();

    /**
     * Sends the message contained in the specified byte array to the
     * server. The specified message is sent asychronously to the server;
     * therefore, a successful invocation of this method does not indicate
     * that the given message was successfully sent. Messages that are
     * received by the server are delivered in sending order.
     * <p>
     * The specified byte array must not be modified after invoking this
     * method; if the byte array is modified, then this method may have
     * unpredictable results.
     *
     * @param message a message
     *
     * @throws IOException if this session is disconnected or an IO error
     *         occurs
     */
    void send(byte[] message) throws IOException;

    /**
     * Returns {@code true} if this session is connected, otherwise
     * returns {@code false}.
     * 
     * @return {@code true} if this session is connected, and
     *         {@code false} otherwise
     */
    boolean isConnected();

    /**
     * Initiates logging out from the server. If {@code force} is
     * {@code true} then this session is forcibly terminated, for
     * example, by terminating the associated client's network connections.
     * If {@code force} is {@code false}, then this session
     * is gracefully disconnected, notifying the server that the client
     * logged out. When a session has been logged out, gracefully or
     * otherwise, the {@link ServerSessionListener#disconnected
     * disconnected} method is invoked on this session's associated {@link
     * ServerSessionListener} passing a {@code boolean} indicating
     * whether the disconnection was graceful.
     * <p>
     * If this server session is already disconnected, then no action is
     * taken.
     * 
     * @param force if {@code true}, this session is forcibly
     *        terminated; otherwise the session is gracefully disconnected
     *
     * @throws IllegalStateException if this session is disconnected
     */
    void logout(boolean force);

}
