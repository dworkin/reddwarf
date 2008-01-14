/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.app.util;

import java.io.Serializable;

import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.Delivery;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.TransactionException;

/**
 * Manager for creating and obtaining channels.  A {@link UtilChannel} is
 * a communication group consisting of multiple client sessions and
 * the server.
 *
 * <p>A UtilChannel is created with a {@link Delivery} requirement.
 * Messages sent on a channel are delivered according to the
 * delivery requirement specified at creation time.  A delivery
 * requirement on a channel cannot be changed.  If different delivery
 * requirements are needed, then different channels should be used for
 * communication.
 * 
 * TODO need to register this as a factory for AppDispatcher
 */
public class UtilChannelManager 
    implements ManagedObject, Serializable
{
    /** The version of the serialized form. */
    private static final long serialVersionUID = 1L;


    public static UtilChannelManager instance() {
        // TBI
        return null;
    }

    /**
     * Creates a new channel with the specified listener and specified
     * delivery requirement, binds it to the specified name, and
     * returns it.
     *
     * <p>If the specified {@code listener} is
     * non-{@code null}, then when any client session sends a
     * message on the returned channel, the specified listener's {@link
     * UtilChannelListener#receivedMessage
     * receivedMessage} method is invoked with this channel, the
     * session, and the message.  The specified listener is not
     * invoked for messages that the server sends on the channel via
     * one of the channel's {@code send} methods.  If the specified
     * {@code listener} is non-{@code null}, then it must also
     * be {@link Serializable}.
     *
     * <p>Messages sent on the returned channel are delivered
     * according to the specified delivery requirement.
     *
     * @param name a name
     * @param listener a channel listener, or {@code null}
     * @param delivery a delivery requirement
     *
     * @return a new channel bound to the specified name
     *
     * @throws IllegalArgumentException if the specified listener is
     * non-{@code null} and is not serializable
     * @throws NameExistsException if a channel is already bound to
     * the specified name
     * @throws TransactionException if the operation failed because of
     * a problem with the current transaction
     */
    public UtilChannel createChannel(
            String name, UtilChannelListener listener, Delivery delivery)
    {
        // TODO
        return null;
    }

    /**
     * Returns an existing channel with the specified name.
     *
     * @param name a channel name
     *
     * @return an existing channel bound to the specified name
     *
     * @throws NameNotBoundException if a channel is not bound to the
     * specified name
     * @throws TransactionException if the operation failed because of
     * a problem with the current transaction
     */
    public UtilChannel getChannel(String name) {
        // TODO
        return null;
    }

    
}
