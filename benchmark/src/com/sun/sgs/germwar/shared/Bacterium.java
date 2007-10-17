/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.germwar.shared;

import com.sun.sgs.app.ManagedObject;

import com.sun.sgs.germwar.shared.Coordinate;
import com.sun.sgs.germwar.shared.InvalidMoveException;
import com.sun.sgs.germwar.shared.Location;

/**
 * Represents a single bacterium.
 */
public interface Bacterium extends ManagedObject {
    /** The health cost to performing a move. */
    public static final float MOVE_HEALTH_COST = 5f;

    /** The health cost to performing a split. */
    public static final float SPLIT_HEALTH_COST = 100f;

    /** The health cost incurred every turn no matter what you do. */
    public static final float TURN_HEALTH_COST = 0f;

    /** Adds {@code mod} amount to this {@code Bacterium's} health level. */
    void addHealth(float mod);

    /**
     * Updates bacterium to reflect an attack by another player's bacterium.
     * @return whether the attack modified this bacterium (the defender) at all
     */
    boolean doFight(Bacterium attacker);

    /**
     * Updates the bacterium's internal state to reflect that a move has
     * occurred to {@code newPos}.  Does not cause any changes to the world
     * itself (such as where the Bacterium is located), only to the bacterium's
     * internal state.
     *
     * @param newPos the bacterium's new position after the move
     * @throws InvalidMoveException if the movement attempt fails
     */
    void doMove(Coordinate newPos) throws InvalidMoveException;

    /**
     * Updates a bacterium's internal state to reflect that a split has
     * occurred, returning a new, spawned bacterium.  Does not cause any changes
     * to the world itself (such as placing the spawned bacterium in the world).
     *
     * @param spawnPos the position where the newly spawned bacterium should be
     *        placed
     * @return the newly spawned bacterium
     * @throws InvalidSplitException if the split attempt fails
     */
    Bacterium doSplit(Coordinate spawnPos) throws InvalidSplitException;

    /** Returns the coordinate of the entity's current position. */
    Coordinate getCoordinate();

    /**
     * Returns the number of movement points that this Bacterium currently has
     * available for the remainder of this turn.
     */
    int getCurrentMovementPoints();

    /** Returns this {@code Bacterium's} current health level. */
    float getHealth();
    
    /** Returns this {@code Bacterium's} unique ID. */
    int getId();

    /** Returns the last turn that {@code turnUpdate} was called. */
    long getLastUpdated();
    
    /**
     * Returns the number of movement points that this Bacterium has at the
     * beginning of each turn.
     */
    int getMaxMovementPoints();

    /**
     * Returns the ID of this object's owner.  Returns 0 if not owned by any
     * player (i.e. "owned" by the server).
     */
    long getPlayerId();

    /**
     * Updates the current conditions of this bacterium if {@code turnNo} is
     * greater than the value passed the last time that {@code turnUpdate} was
     * called on this object.  This enables asynchronous updates so that {@code
     * turnUpdate} doesn't have to be called on a bacterium on each and every
     * turn if the bacterium doesn't otherwise need to be accessed on that turn.
     */
    void turnUpdate(long turnNo);
}
