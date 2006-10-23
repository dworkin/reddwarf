package com.sun.sgs.app;

import java.nio.ByteBuffer;

/**
 * Interface representing a single, connected login session between a
 * client and the server.
 *
 * <p>When a client logs in, the application's {@link
 * AppListener#loggedIn(ClientSession) AppListener.loggedIn} method is
 * invoked with a new <code>ClientSession</code> instance which
 * represents the current connection between that client and the
 * server.  The application should register a {@link
 * ClientSessionListener} for each session logged in, so that it can
 * receive notification when a client session sends a message, is
 * disconnected, or logs out.
 *
 * <p>A <code>ClientSession</code> is used to identify a client that is
 * logged in, to send messages to that client, to register a listener
 * to receive messages sent by that client, and to forcibly disconnect
 * that client from the server.
 *
 * <p>A session is considered disconnected if one of the following occurs:
 * <ul>
 * <li> the client logs out
 * <li> the client is forcibly disconnected by the server by invoking
 * its session's {@link #disconnect disconnect} method
 * <li> the client becomes disconnected due to a network failure, and
 * a connection to the client cannot be re-established in a timely manner
 * </ul>
 *
 * <p>If a client associated with a <code>ClientSession</code> becomes
 * disconnected due to one of these conditions, the {@link
 * ClientSessionListener#disconnected(boolean) disconnected} method is
 * invoked on that session's registered
 * <code>ClientSessionListener</code> with a <code>boolean</code> that
 * if <code>true</code> indicates the client logged out gracefully.
 *
 * <p>Once a client becomes disconnected, its <code>ClientSession</code>
 * becomes invalid and can no longer be used to communicate with that
 * client.  When that client logs back in again, a new session is
 * established with the server.
 */
public interface ClientSession {

    /**
     * Returns the login name used to authenticate this session.
     *
     * <p>This method may be replaced by a <code>getUser</code> method
     * that returns a <code>User</code> object that provides the
     * user's login name and possibly more information.
     *
     * @return the name used to authenticate this session
     *
     * @throws TransactionException if the operation failed because of
     * a problem with the current transaction
     */
    String getName();

    /**
     * Returns a byte buffer containing the representation of the
     * client address for this session.
     *
     * @return the representation of the client address for this session
     */
    ByteBuffer getClientAddress();

    /**
     * Sends a message with the specified contents to this session's
     * client.
     *
     * @param message a message
     *
     * @throws IllegalStateException if this session is disconnected
     * @throws TransactionException if the operation failed because of
     * a problem with the current transaction
     */
    void send(ByteBuffer message);

    /**
     * Forcibly disconnects this client session.  If this session is
     * already disconnected, then no action is taken.
     *
     * @throws TransactionException if the operation failed because of
     * a problem with the current transaction
     */
    void disconnect();

    /**
     * Returns <code>true</code> if the client is connected,
     * otherwise returns <code>false</code>.
     *
     * @return <code>true</code> if the client is connected,
     * otherwise returns <code>false</code>
     *
     * @throws TransactionException if the operation failed because of
     * a problem with the current transaction
     */
    boolean isConnected();
}
