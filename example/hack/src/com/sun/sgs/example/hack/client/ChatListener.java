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

package com.sun.sgs.example.hack.client;

import java.math.BigInteger;
import java.util.Map;


/**
 * This interface is used to listen for incoming chat messages and
 * membership change events.
 */
public interface ChatListener
{

    /**
     * Called when a player joins the chat group.
     *
     * @param uid the identifier for the player
     */
    public void playerJoined(BigInteger uid);

    /**
     * Called when a player leaves the chat group.
     *
     * @param uid the identifier for the player
     */
    public void playerLeft(BigInteger uid);

    /**
     * Notify the listener when a message has arrived.
     *
     * @param sender the identifier for the sender
     * @param message the message itself
     */
    public void messageArrived(BigInteger sender, String message);

    /**
     * Notifies the listener about some set of mappings from identifier
     * to user name. This information is needed to map names to the
     * other messages received by this interface. It is assumed that
     * user identifiers will not collide during the lifetime of this
     * client, so there is no message to remove or replace existing
     * mappings, but should one of the identifiers in the map collide
     * with an already known identifier, then the existing identifier
     * to name mapping should be replaced by the new one.
     *
     * @param uidMap a map from user identifier to user name
     */
    public void addUidMappings(Map<BigInteger,String> uidMap);

}
