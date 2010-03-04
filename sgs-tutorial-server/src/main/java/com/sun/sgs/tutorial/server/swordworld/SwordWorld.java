/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.tutorial.server.swordworld;

import java.io.Serializable;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.AppListener;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionListener;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedReference;

/**
 * A tiny sample MUD application for the Project Darkstar Server.
 * <p>
 * There is a Room.  In the Room there is a Sword...
 */
public class SwordWorld
    implements Serializable, AppListener
{
    /** The version of the serialized form of this class. */
    private static final long serialVersionUID = 1L;

    /** The {@link Logger} for this class. */
    private static final Logger logger =
        Logger.getLogger(SwordWorld.class.getName());

    /** A reference to the one-and-only {@linkplain SwordWorldRoom room}. */
    private ManagedReference<SwordWorldRoom> roomRef = null;

    /**
     * {@inheritDoc}
     * <p>
     * Creates the world within the MUD.
     */
    public void initialize(Properties props) {
        logger.info("Initializing SwordWorld");

        // Create the Room
        SwordWorldRoom room =
            new SwordWorldRoom("Plain Room", "a nondescript room");

        // Create the Sword
        SwordWorldObject sword =
            new SwordWorldObject("Shiny Sword", "a shiny sword.");

        // Put the Sword to the Room
        room.addItem(sword);

        // Keep a reference to the Room
        setRoom(room);

        logger.info("SwordWorld Initialized");
    }

    /**
     * Gets the SwordWorld's One True Room.
     * <p>
     * @return the room for this {@code SwordWorld}
     */
    public SwordWorldRoom getRoom() {
        if (roomRef == null) {
            return null;
        }

        return roomRef.get();
    }

    /**
     * Sets the SwordWorld's One True Room to the given room.
     * <p>
     * @param room the room to set
     */
    public void setRoom(SwordWorldRoom room) {
        DataManager dataManager = AppContext.getDataManager();

        if (room == null) {
            roomRef = null;
            return;
        }

        roomRef = dataManager.createReference(room);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Obtains the {@linkplain SwordWorldPlayer player} for this
     * {@linkplain ClientSession session}'s user, and puts the
     * player into the One True Room for this {@code SwordWorld}.
     */
    public ClientSessionListener loggedIn(ClientSession session) {
        logger.log(Level.INFO,
            "SwordWorld Client login: {0}", session.getName());

        // Delegate to a factory method on SwordWorldPlayer,
        // since player management really belongs in that class.
        SwordWorldPlayer player = SwordWorldPlayer.loggedIn(session);

        // Put player in room
        player.enter(getRoom());

        // return player object as listener to this client session
        return player;
    }
}
