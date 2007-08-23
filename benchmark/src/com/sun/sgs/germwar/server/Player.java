/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.germwar.server;

import com.sun.sgs.germwar.shared.Bacterium;
import com.sun.sgs.germwar.shared.Coordinate;
import com.sun.sgs.germwar.shared.Location;

/**
 * todo
 */
public interface Player extends Iterable<Bacterium> {
    /**
     * Returns the number of bacteria owned by this player.
     */
    int bacteriaCount();

    /**
     * Creates a new bacterium, adds it to this player's collection, and returns
     * it.
     *
     * @param coord coordinate of the the starting {@link Location} for the
     *        {@link Bacterium}, which must be empty
     */
    Bacterium createBacterium(Coordinate coord);

    /**
     * Returns the specified bacterium from this player's collection, or
     * {@code null} if no such bacterium exists.
     */
    Bacterium getBacterium(int id);

    /**
     * Returns this player's unique ID.  (IDs are constant for all time, unique
     * session-IDs which are new each time a player logs into a server)
     */
    long getId();

    /**
     * Returns this player's unique user (login) name.
     */
    String getUsername();

    /**
     * Takes care of setting up the game state of a new player.
     */
    void initialize();

    /**
     * Removes a bacterium from this player's collection.  Generally this should
     * only be done when the bacterium is being destroyed.
     *
     * @throws IllegalArgumentException if {@code bact} is not in this player's
     *         collection of bacteria
     */
    void removeBacterium(Bacterium bact);
}
