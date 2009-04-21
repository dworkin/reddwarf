/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
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

package com.sun.sgs.tutorial.server.swordworld;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionListener;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.NameNotBoundException;

/**
 * Represents a player in the {@link SwordWorld} example MUD.
 */
public class SwordWorldPlayer
    extends SwordWorldObject
    implements ClientSessionListener
{
    /** The version of the serialized form of this class. */
    private static final long serialVersionUID = 1L;

    /** The {@link Logger} for this class. */
    private static final Logger logger =
        Logger.getLogger(SwordWorldPlayer.class.getName());

    /** The message encoding. */
    public static final String MESSAGE_CHARSET = "UTF-8";

    /** The prefix for player bindings in the {@code DataManager}. */
    protected static final String PLAYER_BIND_PREFIX = "Player.";

    /** The {@code ClientSession} for this player, or null if logged out. */
    private ManagedReference<ClientSession> currentSessionRef = null;

    /** The {@link SwordWorldRoom} this player is in, or null if none. */
    private ManagedReference<SwordWorldRoom> currentRoomRef = null;

    /**
     * Find or create the player object for the given session, and mark
     * the player as logged in on that session.
     *
     * @param session which session to find or create a player for
     * @return a player for the given session
     */
    public static SwordWorldPlayer loggedIn(ClientSession session) {
        String playerBinding = PLAYER_BIND_PREFIX + session.getName();

        // try to find player object, if non existent then create
        DataManager dataMgr = AppContext.getDataManager();
        SwordWorldPlayer player;

        try {
            player = (SwordWorldPlayer) dataMgr.getBinding(playerBinding);
        } catch (NameNotBoundException ex) {
            // this is a new player
            player = new SwordWorldPlayer(playerBinding);
            logger.log(Level.INFO, "New player created: {0}", player);
            dataMgr.setBinding(playerBinding, player);
        }
        player.setSession(session);
        return player;
    }

    /**
     * Creates a new {@code SwordWorldPlayer} with the given name.
     *
     * @param name the name of this player
     */
    protected SwordWorldPlayer(String name) {
        super(name, "Seeker of the Sword");
    }

    /**
     * Returns the session for this listener.
     * 
     * @return the session for this listener
     */
    protected ClientSession getSession() {
        if (currentSessionRef == null) {
            return null;
        }

        return currentSessionRef.get();
    }

    /**
     * Mark this player as logged in on the given session.
     *
     * @param session the session this player is logged in on
     */
    protected void setSession(ClientSession session) {
        DataManager dataMgr = AppContext.getDataManager();
        dataMgr.markForUpdate(this);

        currentSessionRef = dataMgr.createReference(session);

        logger.log(Level.INFO,
            "Set session for {0} to {1}",
            new Object[] { this, session });
    }

    /**
     * Handles a player entering a room.
     *
     * @param room the room for this player to enter
     */
    public void enter(SwordWorldRoom room) {
        logger.log(Level.INFO, "{0} enters {1}",
            new Object[] { this, room }
        );
        room.addPlayer(this);
        setRoom(room);
    }

    /** {@inheritDoc} */
    public void receivedMessage(ByteBuffer message) {
        String command = decodeString(message);

        logger.log(Level.INFO,
            "{0} received command: {1}",
            new Object[] { this, command }
        );

        if (command.equalsIgnoreCase("look")) {
            String reply = getRoom().look(this);
            getSession().send(encodeString(reply));
        } else {
            logger.log(Level.WARNING,
                "{0} unknown command: {1}",
                new Object[] { this, command }
            );
            // We could disconnect the rogue player at this point.
            //currentSession.disconnect();
        }
    }

    /** {@inheritDoc} */
    public void disconnected(boolean graceful) {
        setSession(null);
        logger.log(Level.INFO, "Disconnected: {0}", this);
        getRoom().removePlayer(this);
        setRoom(null);
    }

    /**
     * Returns the room this player is currently in, or {@code null} if
     * this player is not in a room.
     * <p>
     * @return the room this player is currently in, or {@code null}
     */
    protected SwordWorldRoom getRoom() {
        if (currentRoomRef == null) {
            return null;
        }

        return currentRoomRef.get();
    }

    /**
     * Sets the room this player is currently in.  If the room given
     * is null, marks the player as not in any room.
     * <p>
     * @param room the room this player should be in, or {@code null}
     */
    protected void setRoom(SwordWorldRoom room) {
        DataManager dataManager = AppContext.getDataManager();
        dataManager.markForUpdate(this);

        if (room == null) {
            currentRoomRef = null;
            return;
        }

        currentRoomRef = dataManager.createReference(room);
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder(getName());
        buf.append('@');
        if (getSession() == null) {
            buf.append("null");
        } else {
            buf.append(currentSessionRef.getId());
        }
        return buf.toString();
    }

    /**
     * Encodes a {@code String} into a {@link ByteBuffer}.
     *
     * @param s the string to encode
     * @return the {@code ByteBuffer} which encodes the given string
     */
    protected static ByteBuffer encodeString(String s) {
        try {
            return ByteBuffer.wrap(s.getBytes(MESSAGE_CHARSET));
        } catch (UnsupportedEncodingException e) {
            throw new Error("Required character set " + MESSAGE_CHARSET +
                " not found", e);
        }
    }

    /**
     * Decodes a message into a {@code String}.
     *
     * @param message the message to decode
     * @return the decoded string
     */
    protected static String decodeString(ByteBuffer message) {
        try {
            byte[] bytes = new byte[message.remaining()];
            message.get(bytes);
            return new String(bytes, MESSAGE_CHARSET);
        } catch (UnsupportedEncodingException e) {
            throw new Error("Required character set " + MESSAGE_CHARSET +
                " not found", e);
        }
    }
}
