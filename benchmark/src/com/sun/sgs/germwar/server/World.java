/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.germwar.server;

import java.io.Serializable;
import java.util.Properties;

import com.sun.sgs.app.ManagedObject;

import com.sun.sgs.germwar.shared.Coordinate;
import com.sun.sgs.germwar.shared.Location;

/**
 * Contains information on the constant features of the game world; it should be
 * initialized once and only once at startup, and considered immutable
 * afterwards.
 *
 * Instances of {@code World} should also implement {@link Serializable} so that
 * they can be stored in the data store.
 *
 * Note that implementations of World typically require a no-arg constructor so
 * that they can be instantiated via Class.newInstance() in
 * GermWarApp.initialize().
 */
public interface World extends ManagedObject {
    /** 
     * Initializes the state of the world.  May only be called once on any
     * instance of World, and must be called before any other World methods are
     * called on that object.
     */
    void initialize(Properties props);

    /** Returns the requested location. */
    Location getLocation(Coordinate coordinate);

    /** Returns the size of the world in the X-dimension. */
    int getXDimension();
    
    /** Returns the size of the world in the Y-dimension. */
    int getYDimension();

    // TODO - comments
    void markForUpdate(Location loc);

    /**
     * Returns whether {@code coordindate} represents a valid coordinate in this
     * world (i.e. lies within the boundaries of the world).
     */
    boolean validCoordinate(Coordinate coordinate);
}
