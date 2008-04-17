/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sun.sgs.app;

import java.io.Serializable;

/**
 * Manager for creating and obtaining channels.  A {@link Channel} is a
 * communication group consisting of multiple client sessions and the
 * server.
 *
 * <p>A Channel is created with a {@link Delivery} requirement.
 * Messages sent on a channel are delivered according to the
 * delivery requirement specified at creation time.  A delivery
 * requirement on a channel cannot be changed.  If different delivery
 * requirements are needed, then different channels should be used for
 * communication.
 * 
 * @see AppContext#getChannelManager
 */
public interface ChannelManager {

    /**
     * Creates a new channel with the specified listener and specified
     * delivery requirement, binds it to the specified name, and
     * returns it.
     *
     * <p>If the specified {@code listener} is
     * non-{@code null}, then when any client session sends a
     * message on the returned channel, the specified listener's {@link
     * ChannelListener#receivedMessage(ClientSession,ByteBuffer)
     * receivedMessage} method is invoked with the client
     * session and the message.  The specified listener is not
     * invoked for messages that the server sends on the channel via
     * the channel's {@link Channel#send send} method.  If the specified
     * {@code listener} is non-{@code null}, then it must also
     * be {@link Serializable}.
     *
     * <p>Supplying a non-{@code null} listener (although not required) is
     * <i>strongly</i> suggested.  A listener's {@code receivedMessage}
     * method provides an opportunity for an application to intervene when
     * a client sends a channel message, to perform access control,
     * filtering, or take other application-specific action on such channel
     * messages.  
     *
     * <p>Messages sent on the returned channel are delivered according to
     * the specified delivery requirement.
     *
     * @param	name a name
     * @param	listener a channel listener, or {@code null}
     * @param	delivery a delivery requirement
     *
     * @return	a new channel bound to the specified name
     *
     * @throws	IllegalArgumentException if the specified listener is
     *		non-{@code null} and is not serializable
     * @throws	ResourceUnavailableException if there are not enough
     *		resources to create the channel
     * @throws	NameExistsException if a channel is already bound to
     *		the specified name
     * @throws	TransactionException if the operation failed because of
     * 		a problem with the current transaction
     */
    Channel createChannel(String name,
			  ChannelListener listener,
			  Delivery delivery);
    
    /**
     * Returns an existing channel with the specified name.
     *
     * @param name a channel name
     *
     * @return an exisiting channel bound to the specified name
     *
     * @throws NameNotBoundException if a channel is not bound to the
     * specified name
     * @throws TransactionException if the operation failed because of
     * a problem with the current transaction
     */
    Channel getChannel(String name);
}
