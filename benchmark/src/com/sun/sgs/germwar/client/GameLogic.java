/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.germwar.client;

import java.util.Map;

import com.sun.sgs.germwar.shared.Coordinate;
import com.sun.sgs.germwar.shared.InvalidMoveException;
import com.sun.sgs.germwar.shared.Location;

/**
 * The interface for classes that implement logic for GermWar client
 * applications.  Typically, implementations of {@code GameLogic} will
 * communicate with an instance of {@link GameGui}.  Implementations should have
 * minimal knowledge of GUI-specific (e.g. Swing) classes.
 */
public interface GameLogic {
    /**
     * Issues a "move bacterium" request.
     *
     * @throws InvalidMoveException if the requested move is not legal
     */
    void doMove(Location src, Location dest) throws InvalidMoveException;

    /**
     * Returns the {@link Location} at {@code coord}, if known.  Otherwise,
     * returns {@code null}.
     */
    Location getLocation(Coordinate coord);

    /** Returns the server-assigned player ID for this client. */
    long getPlayerId();

    /** Issues a login request. */
    void login();

    /** Issues a logout request. */
    void logout(boolean force);

    /** Quits the application. */
    void quit();

    /** Sends a chat message to the specified recipient. */
    void sendChatMessage(String recipient, String msg);
}
