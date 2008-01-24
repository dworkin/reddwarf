/*
 * Copyright 2007 Sun Microsystems, Inc.
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

/**
 * Manager for creating channels.  A {@link Channel} is a communication
 * group consisting of multiple client sessions and the server.
 *
 * <p>A Channel is created with a {@link Delivery} requirement.
 * Messages sent on a channel are delivered according to the
 * delivery requirement specified at creation time.  A delivery
 * requirement on a channel cannot be changed.  If different delivery
 * requirements are needed, then different channels should be used for
 * communication.
 */
public interface ChannelManager {

    /**
     * Creates a new channel with the specified delivery requirement.
     * The caller may want to associate the returned channel with a
     * binding in the {@link DataManager} or store a {@link
     * ManagedReference} to the returned channel.
     *
     * @param	delivery a delivery requirement
     *
     * @return	a new channel
     *
     * @throws	ResourceUnavailableException if there are not enough
     *		resources to create the channel
     * @throws	TransactionException if the operation failed because of
     *		a problem with the current transaction
     */
    Channel createChannel(Delivery delivery);
}
