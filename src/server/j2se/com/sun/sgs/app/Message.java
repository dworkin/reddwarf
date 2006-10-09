package com.sun.sgs.app;

/**
 * Represention of a message sent between a client session and the
 * server, or a message sent on a channel.
 */
public interface Message {

    /**
     * Returns the session that sent this message, or
     * <code>null</code>.  A <code>null</code> sender indicates that
     * the message was sent by the server.
     *
     * @return the session that sent this message, or <code>null</code>.
     *
     * @throws TransactionException if the operation failed because of
     * a problem with the current transaction
     */
    Session getSender();

    /**
     * Returns a byte array containing the contents of the message.
     *
     * @return a byte array containing the contents of the message
     *
     * @throws TransactionException if the operation failed because of
     * a problem with the current transaction
     */
     byte[] getContents();

}
    
