/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.germwar.shared.impl;

import java.io.Serializable;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import com.sun.sgs.germwar.shared.Bacterium;
import com.sun.sgs.germwar.shared.Coordinate;
import com.sun.sgs.germwar.shared.Location;

/**
 * Basic implementation of {@link Location}.
 */
public class LocationImpl implements Location, Serializable {
    /** The version of the serialized form of this class. */
    private static final long serialVersionUID = 1L;

    /** The coordinate of this location. */
    private Coordinate coord;

    /** The entity currently occupying this location, if any. */
    private Bacterium occupant = null;

    /** Current amount of food. */
    private float foodAmount;

    /** Rate at which food appears in this location per turn. */
    private float foodGrowthRate;

    /** The last turn on which updateFood() was called. */
    private long lastUpdated;

    /**
     * Creates a new {@code LocationImpl} with an initial coordinate, 0 initial
     * food, and a growth rate of food of 0.
     */
    public LocationImpl(Coordinate coord) {
        this(coord, 0, 0, 1);
    }

    /**
     * Creates a new {@code LocationImpl} with an initial coordinate, food
     * amount, and growth rate of food.
     */
    public LocationImpl(Coordinate coord, float initialFood,
        float foodGrowthRate)
    {
        this(coord, initialFood, foodGrowthRate, 1);
    }

    /**
     * Creates a new {@code LocationImpl} with an initial coordinate, food
     * amount, and growth rate of food, at some point in the middle of a game
     * (turn is not 1, which is default).
     */
    public LocationImpl(Coordinate coord, float initialFood, float foodGrowthRate,
        long turnNo)
    {
        this.coord = coord;
        this.foodAmount = initialFood;
        this.foodGrowthRate = foodGrowthRate;
        this.lastUpdated = turnNo;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "Location {" + coord + ": " +
            (isOccupied() ? "occupied" : "empty") + "}";
    }

    // implement Location interface

    /**
     * {@inheritDoc}
     */
    public float emptyFood() {
        float old = foodAmount;
        foodAmount = 0f;
        return old;
    }

    /**
     * {@inheritDoc}
     */
    public Coordinate getCoordinate() {
        return coord;
    }

    /**
     * {@inheritDoc}
     */
    public float getFood() {
        return foodAmount;
    }

    /**
     * {@inheritDoc}
     */
    public float getFoodGrowthRate() {
        return foodGrowthRate;
    }

    /**
     * {@inheritDoc}
     */
    public long getLastUpdated() {
        return lastUpdated;
    }

    /**
     * {@inheritDoc}
     */
    public Bacterium getOccupant() {
        return occupant;
    }

    /**
     * {@inheritDoc}
     */
    public boolean isOccupied() {
        return (occupant != null);
    }

    /**
     * {@inheritDoc}
     */
    public void update(long turnNo) {
        if (turnNo == lastUpdated) return;  // efficiency

        double tmp = foodAmount + (turnNo - lastUpdated)*foodGrowthRate;
        lastUpdated = turnNo;

        if (tmp > Location.FOOD_CAPACITY)
            foodAmount = Location.FOOD_CAPACITY;
        else
            foodAmount = (float)tmp;

        if (occupant != null)
            occupant.turnUpdate(turnNo);
    }

    /**
     * {@inheritDoc}
     */
    public Bacterium setOccupant(Bacterium bacterium) {
        Bacterium old = getOccupant();
        occupant = bacterium;
        return old;
    }
}
