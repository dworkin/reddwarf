/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.germwar.shared;

/**
 * Represents a specific location on the world grid.
 */
public interface Location {
    /** The maximum amount of food that can accumulate in a location. */
    public static final float FOOD_CAPACITY = 100f;

    /**
     * Reduces the amount of food in this location to 0, returning the amount
     * that used to be present.
     */
    float emptyFood();

    /** Returns the coordinates of this location. */
    Coordinate getCoordinate();

    /** Returns the amount of food in this location. */
    float getFood();

    /** Returns the amount of food produced in this location each turn. */
    float getFoodGrowthRate();

    /** Returns the last turn that {@code update} was called. */
    long getLastUpdated();

    /** Returns the bacterium currently sitting in this location, if any. */
    Bacterium getOccupant();

    /**
     * Returns whether this location currently has an occupant.  This method
     * offers better performance than {@code getOccupant}.
     */
    boolean isOccupied();

    /**
     * Updates the current conditions of this location if {@code turnNo} is
     * greater than the value passed the last time that {@code update} was
     * called on this object.  This enables asynchronous updates so that {@code
     * update} doesn't have to be called on a location on each and every turn if
     * the location doesn't otherwise need to be accessed on that turn.
     */
    void update(long turnNo);

    /**
     * Sets (or clears, if {@code bacterium = null}) the bacterium currently
     * "sitting" in this location.
     *
     * @return the previous occupant, if any, or {@code null} if not
     */
    Bacterium setOccupant(Bacterium bacterium);

    // TODO - perhaps Locations should know if any of their edges are along the
    // map border?
}
