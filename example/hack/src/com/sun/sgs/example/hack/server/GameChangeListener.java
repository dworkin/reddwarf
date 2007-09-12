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

package com.sun.sgs.example.hack.server;

import com.sun.sgs.app.ManagedObject;

import com.sun.sgs.example.hack.share.GameMembershipDetail;

import java.util.Collection;


/**
 * This interface represents anything that listens for updates to changing
 * details of games. While in this app only the <code>Lobby</code> listens
 * for these updates, in practice anyone could be a consumer of these
 * messages, and this pattern provides a nice way notify multiple parties
 * of these updates.
 */
public interface GameChangeListener extends ManagedObject {

    /**
     * Notifies the listener that games were added to the app.
     *
     * @param names the names of the added <code>Game</code>s
     */
    public void gameAdded(Collection<String> names);

    /**
     * Notifies the listener that games were removed from the app.
     *
     * @param names the names of the removed <code>Game</code>s
     */
    public void gameRemoved(Collection<String> names);

    /**
     * Notifies the listener that some game membership information has
     * changed.
     *
     * @param details information about the membership changes
     */
    public void membershipChanged(Collection<GameMembershipDetail> details);

}
