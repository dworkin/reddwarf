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

package com.sun.sgs.example.hack.client;


/**
 * This interface defines a class that listens for commands from the player.
 * Specifically, it supports key-presses. It may be extended to support
 * other kinds of commands from the player.
 */
public interface CommandListener
{

    /**
     * Called when the user presses a key.
     *
     * @param key the key, as defined in <code>java.awt.event.KeyEvent</code>
     */
    public void action(int key);

}
